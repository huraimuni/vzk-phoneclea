package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_DELETE = 100;
    private static final String PREFS = "phoneclean";

    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private boolean onlySuspicious = true;
    private boolean scanning = false;

    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final List<String> failed = new ArrayList<>();
    private String current;

    private TextView summary;
    private ListView listView;
    private Button btnFilter;
    private Button btnDelete;
    private RowAdapter adapter;
    private Button btnPause;
    private CheckBox cbKeep;
    private final Set<String> paused = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed = false;
    private final Runnable keepTask = new Runnable() {
        @Override
        public void run() {
            if (resumed && cbKeep != null && cbKeep.isChecked() && !scanning) {
                sendStop(targets(false));
                handler.postDelayed(this, 4000);
            }
        }
    };

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        startScan();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("seen_help", false)) {
            sp.edit().putBoolean("seen_help", true).apply();
            showHelp();
        }
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        root.addView(Ui.header(this, "VZK 폰클린",
                "광고로 깔린 의심 앱을 찾아 한 번에 정리합니다"));

        summary = Ui.text(this, "앱을 분석하는 중입니다...", 15, Ui.INK, true);
        summary.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(summary);

        btnPause = Ui.button(this, "1단계 ▸ 원클릭 일시 중지", Ui.CORAL, Color.WHITE);
        btnPause.setTextSize(19);
        btnPause.setPadding(Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18));
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPause(false);
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.setMargins(Ui.dp(this, 11), Ui.dp(this, 2), Ui.dp(this, 11), 0);
        root.addView(btnPause, plp);

        LinearLayout pauseRow = new LinearLayout(this);
        pauseRow.setOrientation(LinearLayout.HORIZONTAL);
        pauseRow.setGravity(Gravity.CENTER_VERTICAL);
        pauseRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 2));
        cbKeep = new CheckBox(this);
        cbKeep.setText("정지 유지 (켜 두는 동안 4초마다 다시 정지)");
        cbKeep.setTextSize(13);
        cbKeep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.removeCallbacks(keepTask);
                if (cbKeep.isChecked()) handler.post(keepTask);
            }
        });
        pauseRow.addView(cbKeep, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button allBtn = Ui.button(this, "직접 설치한 앱 전체 중지", Color.rgb(226, 230, 244), Ui.NAVY);
        allBtn.setTextSize(12);
        allBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPause(true);
            }
        });
        pauseRow.addView(allBtn);
        root.addView(pauseRow);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        btnFilter = Ui.button(this, "전체 앱 보기", Color.rgb(226, 230, 244), Ui.NAVY);
        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onlySuspicious = !onlySuspicious;
                applyFilter();
            }
        });
        Button btnSelect = Ui.button(this, "의심 앱 모두 선택", Color.rgb(255, 226, 214), Ui.CORAL_DARK);
        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected.clear();
                for (AppEntry e : all) {
                    if (e.level() >= AppEntry.LEVEL_WARN) selected.add(e.pkg);
                }
                if (selected.isEmpty()) Ui.toast(MainActivity.this, "선택할 의심 앱이 없습니다.");
                refreshList();
            }
        });
        row1.addView(btnFilter, Ui.weight(this, 3));
        row1.addView(btnSelect, Ui.weight(this, 3));
        root.addView(row1);

        listView = new ListView(this);
        listView.setDividerHeight(Ui.dp(this, 1));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(228, 224, 220)));
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < shown.size()) showDetail(shown.get(position));
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        btnDelete = Ui.button(this, "2단계 ▸ 선택한 앱 삭제", Ui.RED, Color.WHITE);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(Ui.dp(this, 11), Ui.dp(this, 6), Ui.dp(this, 11), 0);
        root.addView(btnDelete, dlp);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 6));
        Button b1 = Ui.button(this, "광고·알고리즘 정리", Ui.CORAL, Color.WHITE);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AdsGuideActivity.class));
            }
        });
        Button b2 = Ui.button(this, "결과 공유", Ui.GREEN, Color.WHITE);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareReport();
            }
        });
        Button b3 = Ui.button(this, "도움말", Ui.NAVY, Color.WHITE);
        b3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelp();
            }
        });
        row2.addView(b1, Ui.weight(this, 3));
        row2.addView(b2, Ui.weight(this, 3));
        row2.addView(b3, Ui.weight(this, 3));
        root.addView(row2);

        setContentView(root);
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        summary.setText("앱을 분석하는 중입니다...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> result = Scanner.scan(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        all.clear();
                        all.addAll(result);
                        // 이미 지워진 앱은 선택에서 뺀다
                        Set<String> present = new HashSet<>();
                        for (AppEntry e : all) present.add(e.pkg);
                        selected.retainAll(present);
                        scanning = false;
                        applyFilter();
                    }
                });
            }
        }).start();
    }

    private void applyFilter() {
        shown.clear();
        int high = 0;
        int warn = 0;
        for (AppEntry e : all) {
            if (e.level() == AppEntry.LEVEL_HIGH) high++;
            else if (e.level() == AppEntry.LEVEL_WARN) warn++;
            if (!onlySuspicious || e.level() >= AppEntry.LEVEL_WARN) shown.add(e);
        }
        summary.setText("직접 설치한 앱 " + all.size() + "개 중  위험 의심 " + high + "개 / 주의 " + warn + "개");
        btnFilter.setText(onlySuspicious ? "전체 앱 보기" : "의심 앱만 보기");
        refreshList();
    }

    private void refreshList() {
        adapter.notifyDataSetChanged();
        int n = selected.size();
        btnDelete.setText(n == 0 ? "2단계 ▸ 선택한 앱 삭제" : "2단계 ▸ 선택한 앱 " + n + "개 삭제");
    }

    private int levelColor(AppEntry e) {
        switch (e.level()) {
            case AppEntry.LEVEL_HIGH:
                return Ui.RED;
            case AppEntry.LEVEL_WARN:
                return Ui.AMBER;
            default:
                return Ui.GREEN;
        }
    }

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final AppEntry e = shown.get(position);
            LinearLayout row;
            CheckBox cb;
            ImageView icon;
            TextView name;
            TextView meta;
            TextView why;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 10),
                        Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 10));
                row.setBackgroundColor(Color.WHITE);

                cb = new CheckBox(MainActivity.this);
                cb.setFocusable(false);
                cb.setTag("cb");
                row.addView(cb);

                icon = new ImageView(MainActivity.this);
                icon.setTag("icon");
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        Ui.dp(MainActivity.this, 44), Ui.dp(MainActivity.this, 44));
                ilp.setMargins(0, 0, Ui.dp(MainActivity.this, 10), 0);
                row.addView(icon, ilp);

                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                name = Ui.text(MainActivity.this, "", 17, Ui.INK, true);
                name.setTag("name");
                meta = Ui.text(MainActivity.this, "", 13, Ui.GRAY, false);
                meta.setTag("meta");
                why = Ui.text(MainActivity.this, "", 13, Ui.INK, false);
                why.setTag("why");
                why.setMaxLines(3);
                col.addView(name);
                col.addView(meta);
                col.addView(why);
                row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                row = (LinearLayout) convertView;
            }
            cb = (CheckBox) row.findViewWithTag("cb");
            icon = (ImageView) row.findViewWithTag("icon");
            name = (TextView) row.findViewWithTag("name");
            meta = (TextView) row.findViewWithTag("meta");
            why = (TextView) row.findViewWithTag("why");

            name.setText(e.label);
            String date = e.installTime > 0 ? dateFmt.format(new Date(e.installTime)) : "날짜 모름";
            meta.setText("[" + e.levelText() + "]  " + date + " 설치" + (paused.contains(e.pkg) ? "  [정지 명령 보냄]" : ""));
            meta.setTextColor(levelColor(e));
            if (e.reasons.isEmpty()) {
                why.setText(e.trusted ? "정상 스토어에서 설치된 알려진 앱" : "특이 징후 없음");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < e.reasons.size() && i < 2; i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("- ").append(e.reasons.get(i));
                }
                if (e.reasons.size() > 2) sb.append("\n외 ").append(e.reasons.size() - 2).append("건");
                why.setText(sb.toString());
            }
            try {
                Drawable d = getPackageManager().getApplicationIcon(e.pkg);
                icon.setImageDrawable(d);
            } catch (Exception ex) {
                icon.setImageDrawable(null);
            }
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(selected.contains(e.pkg));
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (((CheckBox) v).isChecked()) selected.add(e.pkg);
                    else selected.remove(e.pkg);
                    int n = selected.size();
                    btnDelete.setText(n == 0 ? "2단계 ▸ 선택한 앱 삭제" : "2단계 ▸ 선택한 앱 " + n + "개 삭제");
                }
            });
            return row;
        }
    }

    // ---------------------------------------------------------------- 일시 중지

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (cbKeep != null && cbKeep.isChecked()) {
            handler.removeCallbacks(keepTask);
            handler.postDelayed(keepTask, 2000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(keepTask);
    }

    /** 일시 중지 대상: 평소엔 의심·주의 앱, allUser면 직접 설치한 앱 중 알려진 정상 앱을 뺀 전부 */
    private List<String> targets(boolean allUser) {
        List<String> out = new ArrayList<>();
        for (AppEntry e : all) {
            if (e.trusted) continue;
            if (allUser || e.level() >= AppEntry.LEVEL_WARN) out.add(e.pkg);
        }
        return out;
    }

    private void sendStop(List<String> pkgs) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        for (String p : pkgs) {
            try {
                am.killBackgroundProcesses(p);
            } catch (Exception ignored) {
            }
        }
    }

    private void doPause(boolean allUser) {
        if (scanning) {
            Ui.toast(this, "앱을 분석하는 중입니다. 잠시 뒤에 다시 눌러 주세요.");
            return;
        }
        final List<String> pkgs = targets(allUser);
        if (pkgs.isEmpty()) {
            Ui.toast(this, allUser ? "정지할 앱이 없습니다." : "의심·주의 앱이 없습니다. [직접 설치한 앱 전체 중지]도 써 볼 수 있습니다.");
            return;
        }
        // 한 번에 끝나지 않는 경우가 많아서 몇 번 반복해서 보낸다
        for (int i = 0; i < 4; i++) {
            final int round = i;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendStop(pkgs);
                    if (round == 3) refreshList();
                }
            }, i * 700L);
        }
        paused.addAll(pkgs);
        refreshList();
        new AlertDialog.Builder(this)
                .setTitle("일시 중지 명령을 보냈습니다")
                .setMessage(pkgs.size() + "개 앱의 백그라운드 실행을 멈추게 했습니다.\n\n"
                        + "폰이 조금 가벼워졌는지 확인해 보세요. 이건 임시 조치라서, 앱이 다시 켜질 수 있습니다. "
                        + "다시 켜지면 한 번 더 누르거나, [정지 유지]를 체크해 두세요.\n\n"
                        + "다음 단계로 지울 앱을 고르시겠어요?")
                .setPositiveButton("삭제할 앱 고르기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selected.clear();
                        for (AppEntry e : all) {
                            if (e.level() >= AppEntry.LEVEL_WARN) selected.add(e.pkg);
                        }
                        onlySuspicious = true;
                        applyFilter();
                        Ui.toast(MainActivity.this, "의심 앱을 미리 체크해 두었습니다. 지우면 안 되는 앱은 체크를 풀고 [2단계 ▸ 삭제]를 누르세요.");
                    }
                })
                .setNegativeButton("나중에", null)
                .show();
    }

    // ---------------------------------------------------------------- 상세 보기

    private void showDetail(final AppEntry e) {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 6));
        sv.addView(box);

        TextView lv = Ui.text(this, "판정: " + e.levelText(), 16, levelColor(e), true);
        box.addView(lv);
        String date = e.installTime > 0 ? dateFmt.format(new Date(e.installTime)) : "날짜 모름";
        TextView info = Ui.text(this,
                "설치일: " + date + "\n설치 경로: " + e.installerText + "\n패키지: " + e.pkg,
                14, Ui.INK, false);
        info.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        box.addView(info);

        StringBuilder sb = new StringBuilder();
        if (e.reasons.isEmpty()) {
            sb.append("의심스러운 징후가 발견되지 않았습니다.");
        } else {
            sb.append("의심하는 이유\n");
            for (String r : e.reasons) sb.append("- ").append(r).append("\n");
        }
        sb.append("\n※ 징후가 있다고 해서 반드시 악성 앱은 아닙니다. 내가 쓰는 앱인지 확인하고 판단하세요.");
        TextView why = Ui.text(this, sb.toString(), 14, Ui.INK, false);
        why.setLineSpacing(0, 1.2f);
        box.addView(why);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setView(sv)
                .setPositiveButton("닫기", null)
                .create();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);

        Button del = Ui.button(this, "이 앱 삭제", Ui.RED, Color.WHITE);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                List<String> one = new ArrayList<>();
                one.add(e.pkg);
                beginDelete(one);
            }
        });
        Button info2 = Ui.button(this, "앱 정보 열기 (설정)", Color.rgb(226, 230, 244), Ui.NAVY);
        info2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppInfo(e.pkg);
            }
        });
        Button naver = Ui.button(this, "네이버에서 앱 이름 검색", Color.rgb(222, 245, 232), Color.rgb(10, 110, 60));
        naver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.openUrl(MainActivity.this, "https://search.naver.com/search.naver?query="
                        + Uri.encode(e.label + " 앱"));
            }
        });
        Button google = Ui.button(this, "구글에서 앱 이름 검색", Color.rgb(224, 236, 255), Color.rgb(25, 80, 190));
        google.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.openUrl(MainActivity.this, "https://www.google.com/search?q="
                        + Uri.encode(e.label + " 앱"));
            }
        });
        for (Button b : new Button[]{del, info2, naver, google}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            actions.addView(b, lp);
        }
        box.addView(actions);
        dlg.show();
    }

    private void openAppInfo(String pkg) {
        Ui.openFirst(this,
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)),
                new Intent(Settings.ACTION_APPLICATION_SETTINGS));
    }

    // ---------------------------------------------------------------- 삭제

    private void confirmDelete() {
        if (selected.isEmpty()) {
            Ui.toast(this, "삭제할 앱을 먼저 체크해 주세요. 목록 왼쪽 네모 칸을 누르면 됩니다.");
            return;
        }
        final List<String> pkgs = new ArrayList<>(selected);
        StringBuilder names = new StringBuilder();
        int shownCount = 0;
        for (AppEntry e : all) {
            if (selected.contains(e.pkg)) {
                if (shownCount < 8) names.append("- ").append(e.label).append("\n");
                shownCount++;
            }
        }
        if (shownCount > 8) names.append("외 ").append(shownCount - 8).append("개\n");
        new AlertDialog.Builder(this)
                .setTitle(pkgs.size() + "개 앱을 삭제할까요?")
                .setMessage(names
                        + "\n앱마다 안드로이드의 삭제 확인 창이 하나씩 뜹니다. 그때마다 [확인]을 눌러 주세요.\n"
                        + "내가 쓰는 앱이 섞여 있지 않은지 한 번 더 보세요.")
                .setPositiveButton("삭제 시작", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        beginDelete(pkgs);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void beginDelete(List<String> pkgs) {
        queue.clear();
        queue.addAll(pkgs);
        failed.clear();
        nextDelete();
    }

    private void nextDelete() {
        while (!queue.isEmpty()) {
            current = queue.poll();
            if (!Scanner.isInstalled(getPackageManager(), current)) continue;
            try {
                Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + current));
                i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                startActivityForResult(i, REQ_DELETE);
                return;
            } catch (Exception ex) {
                failed.add(current);
            }
        }
        current = null;
        finishDelete();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_DELETE) return;
        if (current != null && Scanner.isInstalled(getPackageManager(), current)) {
            failed.add(current);
        }
        nextDelete();
    }

    private String labelOf(String pkg) {
        for (AppEntry e : all) {
            if (e.pkg.equals(pkg)) return e.label;
        }
        return pkg;
    }

    private void finishDelete() {
        startScan();
        if (failed.isEmpty()) {
            Ui.toast(this, "삭제를 마쳤습니다. 목록을 다시 분석합니다.");
            return;
        }
        final List<String> bad = new ArrayList<>(failed);
        StringBuilder sb = new StringBuilder();
        for (String p : bad) sb.append("- ").append(labelOf(p)).append("\n");
        new AlertDialog.Builder(this)
                .setTitle("삭제되지 않은 앱 " + bad.size() + "개")
                .setMessage(sb
                        + "\n삭제 확인 창에서 [취소]를 눌렀거나, 앱이 스스로를 지키고 있는 경우입니다.\n\n"
                        + "[기기 관리자]나 [접근성]으로 등록된 앱은 그 권한을 먼저 끄고 나서 삭제해야 합니다.\n"
                        + "1) 아래 [기기 관리자 설정]이나 [접근성 설정]에서 해당 앱을 끄기\n"
                        + "2) 앱 정보를 열어 [삭제] 누르기\n"
                        + "그래도 안 지워지면 폰을 안전 모드로 켜서 삭제해 보세요.")
                .setPositiveButton("앱 정보 열기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openAppInfo(bad.get(0));
                    }
                })
                .setNeutralButton("기기 관리자 설정", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Ui.openFirst(MainActivity.this,
                                new Intent("android.settings.DEVICE_ADMIN_SETTINGS"),
                                new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                })
                .setNegativeButton("접근성 설정", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Ui.openFirst(MainActivity.this,
                                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), null);
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- 공유 / 도움말

    private void shareReport() {
        if (all.isEmpty()) {
            Ui.toast(this, "분석 결과가 아직 없습니다.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[VZK 폰클린 점검 결과]\n");
        sb.append("점검일: ").append(new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date())).append("\n");
        sb.append("직접 설치한 앱 ").append(all.size()).append("개\n\n");
        int n = 0;
        for (AppEntry e : all) {
            if (e.level() < AppEntry.LEVEL_WARN) continue;
            n++;
            sb.append(n).append(". ").append(e.label).append(" [").append(e.levelText()).append("]\n");
            sb.append("   ").append(e.pkg).append("\n");
            for (String r : e.reasons) sb.append("   - ").append(r).append("\n");
        }
        if (n == 0) sb.append("의심 앱이 발견되지 않았습니다.\n");
        sb.append("\n문의: ").append(Ui.CAFE_URL);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "결과 공유 (카카오톡 등)"));
    }

    private void showHelp() {
        String msg = "이 앱은 무엇을 하나요?\n"
                + "내 폰에 직접 설치된 앱을 훑어서, 광고로 깔린 앱에서 흔한 징후(출처, 설치 시기, 위험한 권한 등)를 찾아 보여 줍니다.\n\n"
                + "사용 순서\n"
                + "폰이 너무 느려서 조작이 안 될 때\n"
                + "1단계 [원클릭 일시 중지]를 누르면 의심·주의 앱의 백그라운드 실행을 멈추게 합니다. 그러면 폰이 조금 가벼워집니다. (임시 조치)\n\n"
                + "2단계 삭제\n"
                + "- 일시 중지 뒤 안내 창에서 [삭제할 앱 고르기]를 누르면 의심 앱이 미리 체크됩니다.\n"
                + "- 내가 쓰는 앱은 체크를 풀고 (앱을 누르면 이유가 나옵니다)\n"
                + "- [2단계 ▸ 선택한 앱 삭제]를 누른 뒤, 앱마다 뜨는 삭제 확인 창에서 [확인]\n"
                + "- 이상한 광고가 계속 나오면 [광고·알고리즘 정리] 따라 하기\n\n"
                + "알아두세요\n"
                + "- 바이러스를 잡는 백신이 아닙니다. 악성 파일 DB가 없고, 징후만 보고 판단합니다.\n"
                + "- 징후가 있어도 정상 앱일 수 있습니다. 모르는 앱만 지우세요.\n"
                + "- 일시 중지는 백그라운드 실행을 끄는 임시 조치입니다. 앱이 다시 켜질 수 있고, 지금 화면에 떠 있는 앱은 못 끌 수 있습니다.\n"
                + "- 앱을 몰래 지우지 않습니다. 삭제는 항상 확인 창에서 직접 누릅니다.\n"
                + "- 인터넷 권한이 없어서, 점검 결과가 밖으로 나가지 않습니다.\n"
                + "- 개인이 만든 무료 도구이며, 판단과 책임은 사용자 본인에게 있습니다.\n\n"
                + "문의는 카페 게시판으로만 받습니다.";
        new AlertDialog.Builder(this)
                .setTitle("VZK 폰클린 v1.0")
                .setMessage(msg)
                .setPositiveButton("확인", null)
                .setNeutralButton("카페 열기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Ui.openUrl(MainActivity.this, Ui.CAFE_URL);
                    }
                })
                .show();
    }
}
