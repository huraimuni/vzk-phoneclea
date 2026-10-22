package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 같은 전화번호가 들어 있는 연락처를 묶어서 보여 주고, 남길 1개 빼고 삭제합니다. */
public class DupContactsActivity extends Activity {

    private static final int REQ_PERM = 21;

    static final class Info {
        long id;
        String name;
        final Set<String> numbers = new LinkedHashSet<>();
        int group;
    }

    private final Map<Long, Info> infos = new LinkedHashMap<>();
    private final List<List<Info>> groups = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private final Set<Long> checked = new HashSet<>();
    private TextView summary;
    private Button btnDelete;
    private RowAdapter adapter;
    private boolean scanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "중복 연락처", "같은 전화번호가 들어 있는 연락처끼리 묶었습니다"));

        summary = Ui.text(this, "", 15, Ui.INK, true);
        summary.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 4));
        root.addView(summary);

        TextView warn = Ui.text(this,
                "삭제한 연락처는 되돌릴 수 없습니다. 지우기 전에 구글 연락처 앱에서 [내보내기]로 백업해 두세요.",
                13, Ui.RED, false);
        warn.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(warn);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        Button recommend = Ui.button(this, "추천대로 선택", Color.rgb(255, 226, 214), Ui.CORAL_DARK);
        recommend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRecommended();
                adapter.notifyDataSetChanged();
                updateButtons();
            }
        });
        Button clear = Ui.button(this, "선택 해제", Color.rgb(226, 230, 244), Ui.NAVY);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checked.clear();
                adapter.notifyDataSetChanged();
                updateButtons();
            }
        });
        row1.addView(recommend, Ui.weight(this, 3));
        row1.addView(clear, Ui.weight(this, 3));
        root.addView(row1);

        ListView lv = new ListView(this);
        lv.setDividerHeight(Ui.dp(this, 1));
        adapter = new RowAdapter();
        lv.setAdapter(adapter);
        root.addView(lv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        btnDelete = Ui.button(this, "선택한 연락처 삭제", Ui.RED, Color.WHITE);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(Ui.dp(this, 11), Ui.dp(this, 6), Ui.dp(this, 11), Ui.dp(this, 8));
        root.addView(btnDelete, dlp);

        setContentView(root);
        updateButtons();
        ensurePermissionThenScan();
    }

    private static final String[] PERMS = {
            "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS"};

    private boolean hasPerms() {
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void ensurePermissionThenScan() {
        if (hasPerms()) {
            startScan();
        } else {
            summary.setText("연락처를 읽고 지울 권한이 필요합니다. 허용 창에서 [허용]을 눌러 주세요.");
            requestPermissions(PERMS, REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (hasPerms()) startScan();
            else summary.setText("권한이 없어서 연락처를 볼 수 없습니다. 폰 설정 > 앱 > VZK 폰클린 > 권한에서 허용해 주세요.");
        }
    }

    static String normalize(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("[^0-9]", "");
        if (d.startsWith("82") && d.length() >= 11) d = "0" + d.substring(2);
        return d.length() >= 7 ? d : null;
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        summary.setText("연락처를 읽는 중입니다...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<Long, Info> found = new LinkedHashMap<>();
                final Map<String, Set<Long>> byNumber = new HashMap<>();
                ContentResolver cr = getContentResolver();
                Cursor c = null;
                try {
                    c = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                    ContactsContract.CommonDataKinds.Phone.NUMBER},
                            null, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " ASC");
                    if (c != null) {
                        while (c.moveToNext()) {
                            long id = c.getLong(0);
                            String name = c.getString(1);
                            String num = c.getString(2);
                            String n = normalize(num);
                            if (n == null) continue;
                            Info info = found.get(id);
                            if (info == null) {
                                info = new Info();
                                info.id = id;
                                info.name = (name == null || name.isEmpty()) ? "(이름 없음)" : name;
                                found.put(id, info);
                            }
                            info.numbers.add(num);
                            Set<Long> s = byNumber.get(n);
                            if (s == null) {
                                s = new LinkedHashSet<>();
                                byNumber.put(n, s);
                            }
                            s.add(id);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.close();
                }
                final List<List<Info>> result = cluster(found, byNumber);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        show(found, result);
                    }
                });
            }
        }).start();
    }

    /** 같은 번호로 이어진 연락처들을 하나의 묶음으로 합친다 */
    private static List<List<Info>> cluster(Map<Long, Info> found, Map<String, Set<Long>> byNumber) {
        final Map<Long, Long> parent = new HashMap<>();
        for (Long id : found.keySet()) parent.put(id, id);
        for (Set<Long> s : byNumber.values()) {
            if (s.size() < 2) continue;
            Long first = null;
            for (Long id : s) {
                if (first == null) {
                    first = id;
                    continue;
                }
                Long ra = root(parent, first);
                Long rb = root(parent, id);
                if (!ra.equals(rb)) parent.put(rb, ra);
            }
        }
        Map<Long, List<Info>> groupsMap = new LinkedHashMap<>();
        for (Long id : found.keySet()) {
            Long r = root(parent, id);
            List<Info> l = groupsMap.get(r);
            if (l == null) {
                l = new ArrayList<>();
                groupsMap.put(r, l);
            }
            l.add(found.get(id));
        }
        List<List<Info>> out = new ArrayList<>();
        for (List<Info> l : groupsMap.values()) {
            if (l.size() >= 2) out.add(l);
        }
        return out;
    }

    private static Long root(Map<Long, Long> parent, Long x) {
        Long r = x;
        while (!parent.get(r).equals(r)) r = parent.get(r);
        return r;
    }

    private void show(Map<Long, Info> found, List<List<Info>> result) {
        infos.clear();
        infos.putAll(found);
        groups.clear();
        groups.addAll(result);
        rows.clear();
        int gi = 0;
        for (List<Info> g : groups) {
            for (Info i : g) i.group = gi;
            rows.add("중복 묶음 " + (gi + 1) + "  (" + g.size() + "명)");
            rows.addAll(g);
            gi++;
        }
        applyRecommended();
        summary.setText(groups.isEmpty() ? "같은 번호가 겹치는 연락처가 없습니다."
                : "중복 " + groups.size() + "묶음 (연락처 " + countItems() + "건)");
        adapter.notifyDataSetChanged();
        updateButtons();
    }

    private int countItems() {
        int n = 0;
        for (List<Info> g : groups) n += g.size();
        return n;
    }

    /** 각 묶음에서 가장 오래된(번호가 가장 작은) 연락처 1개만 남기고 나머지를 선택 */
    private void applyRecommended() {
        checked.clear();
        for (List<Info> g : groups) {
            for (int i = 1; i < g.size(); i++) checked.add(g.get(i).id);
        }
    }

    private void updateButtons() {
        int n = checked.size();
        btnDelete.setText(n == 0 ? "선택한 연락처 삭제" : "선택한 " + n + "건 삭제");
    }

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object o = rows.get(position);
            if (o instanceof String) {
                TextView t;
                if (convertView == null) {
                    t = Ui.text(DupContactsActivity.this, "", 14, Color.WHITE, true);
                    t.setBackgroundColor(Ui.NAVY);
                    t.setPadding(Ui.dp(DupContactsActivity.this, 12), Ui.dp(DupContactsActivity.this, 8),
                            Ui.dp(DupContactsActivity.this, 12), Ui.dp(DupContactsActivity.this, 8));
                } else {
                    t = (TextView) convertView;
                }
                t.setText((String) o);
                return t;
            }
            final Info info = (Info) o;
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(DupContactsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(Color.WHITE);
                row.setPadding(Ui.dp(DupContactsActivity.this, 8), Ui.dp(DupContactsActivity.this, 10),
                        Ui.dp(DupContactsActivity.this, 10), Ui.dp(DupContactsActivity.this, 10));
                CheckBox cb = new CheckBox(DupContactsActivity.this);
                cb.setTag("cb");
                cb.setFocusable(false);
                row.addView(cb);
                LinearLayout col = new LinearLayout(DupContactsActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView n = Ui.text(DupContactsActivity.this, "", 16, Ui.INK, true);
                n.setTag("name");
                TextView m = Ui.text(DupContactsActivity.this, "", 13, Ui.GRAY, false);
                m.setTag("num");
                col.addView(n);
                col.addView(m);
                row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                row = (LinearLayout) convertView;
            }
            CheckBox cb = (CheckBox) row.findViewWithTag("cb");
            TextView name = (TextView) row.findViewWithTag("name");
            TextView num = (TextView) row.findViewWithTag("num");
            boolean keeper = groups.get(info.group).get(0) == info;
            name.setText((keeper ? "[남김 추천] " : "") + info.name);
            StringBuilder sb = new StringBuilder();
            for (String s : info.numbers) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s);
            }
            num.setText(sb.toString());
            cb.setChecked(checked.contains(info.id));
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (((CheckBox) v).isChecked()) checked.add(info.id);
                    else checked.remove(info.id);
                    updateButtons();
                }
            });
            return row;
        }
    }

    private void confirmDelete() {
        if (checked.isEmpty()) {
            Ui.toast(this, "삭제할 연락처를 먼저 체크해 주세요.");
            return;
        }
        for (List<Info> g : groups) {
            boolean keepOne = false;
            for (Info i : g) if (!checked.contains(i.id)) keepOne = true;
            if (!keepOne) {
                Ui.toast(this, "같은 묶음의 연락처를 전부 체크할 수는 없습니다. 묶음마다 1명은 남겨 주세요.");
                return;
            }
        }
        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (Long id : checked) {
            Info i = infos.get(id);
            if (i == null) continue;
            if (shown < 8) names.append("- ").append(i.name).append("\n");
            shown++;
        }
        if (shown > 8) names.append("외 ").append(shown - 8).append("건\n");
        new AlertDialog.Builder(this)
                .setTitle(checked.size() + "건을 삭제할까요?")
                .setMessage(names
                        + "\n삭제하면 되돌릴 수 없고, 그 연락처에만 있던 메모·다른 번호도 함께 사라집니다.\n"
                        + "남길 연락처에 필요한 정보가 다 들어 있는지 확인하세요.")
                .setPositiveButton("삭제", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doDelete();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void doDelete() {
        int ok = 0;
        int fail = 0;
        ContentResolver cr = getContentResolver();
        for (Long id : new ArrayList<>(checked)) {
            try {
                int n = cr.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), null, null);
                if (n > 0) ok++;
                else fail++;
            } catch (Exception e) {
                fail++;
            }
        }
        Ui.toast(this, ok + "건 삭제" + (fail > 0 ? ", " + fail + "건 실패" : "") + ". 다시 찾아봅니다.");
        startScan();
    }
}
