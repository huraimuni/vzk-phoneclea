package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 같은 내용으로 보이는 사진·영상을 묶어서 보여 주고, 남길 1개 빼고 휴지통으로 보냅니다. */
public class DupMediaActivity extends Activity {

    private static final int REQ_PERM = 11;
    private static final int REQ_TRASH = 12;

    static final class Item {
        long id;
        Uri uri;
        String name;
        long size;
        long dateAdded;
        String folder;
        boolean video;
        int group;
    }

    private final List<Object> rows = new ArrayList<>(); // String(머리글) 또는 Item
    private final List<List<Item>> groups = new ArrayList<>();
    private final Set<Long> checked = new HashSet<>(); // uri 끝 id가 아니라 고유키(video/image 구분)
    private TextView summary;
    private ListView listView;
    private Button btnDelete;
    private RowAdapter adapter;
    private boolean scanning = false;
    private final ExecutorService thumbPool = Executors.newFixedThreadPool(2);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);

    private static long key(Item it) {
        return it.video ? -it.id : it.id;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "중복 사진·영상", "같은 파일로 추정되는 것끼리 묶었습니다"));

        summary = Ui.text(this, "", 15, Ui.INK, true);
        summary.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(summary);

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

        listView = new ListView(this);
        listView.setDividerHeight(Ui.dp(this, 1));
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        btnDelete = Ui.button(this, "선택한 파일 휴지통으로", Ui.RED, Color.WHITE);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmTrash();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thumbPool.shutdownNow();
    }

    // ------------------------------------------------------------ 권한

    private String[] neededPerms() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"};
        }
        return new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
    }

    private boolean hasPerms() {
        for (String p : neededPerms()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void ensurePermissionThenScan() {
        if (hasPerms()) {
            startScan();
        } else {
            summary.setText("사진·영상을 읽을 권한이 필요합니다. 허용 창에서 [허용]을 눌러 주세요.");
            requestPermissions(neededPerms(), REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (hasPerms()) startScan();
            else summary.setText("권한이 없어서 사진·영상을 볼 수 없습니다. 폰 설정 > 앱 > VZK 폰클린 > 권한에서 허용해 주세요.");
        }
    }

    // ------------------------------------------------------------ 검색

    private void startScan() {
        if (scanning) return;
        scanning = true;
        summary.setText("사진·영상을 읽는 중입니다...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<List<Item>> found = new ArrayList<>();
                try {
                    List<Item> all = new ArrayList<>();
                    all.addAll(query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false));
                    all.addAll(query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true));

                    Map<Long, List<Item>> bySize = new HashMap<>();
                    for (Item it : all) {
                        if (it.size < 2048) continue;
                        List<Item> l = bySize.get(it.size);
                        if (l == null) {
                            l = new ArrayList<>();
                            bySize.put(it.size, l);
                        }
                        l.add(it);
                    }
                    int total = 0;
                    for (List<Item> l : bySize.values()) if (l.size() >= 2) total += l.size();
                    int done = 0;
                    for (List<Item> l : bySize.values()) {
                        if (l.size() < 2) continue;
                        Map<String, List<Item>> byHash = new LinkedHashMap<>();
                        for (Item it : l) {
                            String h = sampleHash(it);
                            done++;
                            if (done % 10 == 0) postProgress(done, total);
                            if (h == null) continue;
                            List<Item> g = byHash.get(h);
                            if (g == null) {
                                g = new ArrayList<>();
                                byHash.put(h, g);
                            }
                            g.add(it);
                        }
                        for (List<Item> g : byHash.values()) {
                            if (g.size() >= 2) found.add(g);
                        }
                    }
                } catch (Exception ignored) {
                }
                Collections.sort(found, new Comparator<List<Item>>() {
                    @Override
                    public int compare(List<Item> a, List<Item> b) {
                        long wa = a.get(0).size * (a.size() - 1);
                        long wb = b.get(0).size * (b.size() - 1);
                        return Long.compare(wb, wa);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        showResult(found);
                    }
                });
            }
        }).start();
    }

    private void postProgress(final int done, final int total) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (scanning) summary.setText("같은 파일인지 비교하는 중... " + done + " / " + total);
            }
        });
    }

    private List<Item> query(Uri base, boolean video) {
        List<Item> out = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        String[] proj;
        if (Build.VERSION.SDK_INT >= 29) {
            proj = new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.RELATIVE_PATH};
        } else {
            proj = new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED};
        }
        Cursor c = null;
        try {
            c = cr.query(base, proj, null, null, null);
            if (c == null) return out;
            while (c.moveToNext()) {
                Item it = new Item();
                it.id = c.getLong(0);
                it.name = c.getString(1);
                it.size = c.getLong(2);
                it.dateAdded = c.getLong(3) * 1000L;
                it.folder = Build.VERSION.SDK_INT >= 29 ? c.getString(4) : "";
                it.video = video;
                it.uri = ContentUris.withAppendedId(base, it.id);
                if (it.name == null) it.name = "(이름 없음)";
                out.add(it);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** 파일 크기 + 앞/중간/뒤 일부 내용의 SHA-1. 같은 크기에서만 호출한다. */
    private String sampleHash(Item it) {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = getContentResolver().openFileDescriptor(it.uri, "r");
            if (pfd == null) return null;
            FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
            FileChannel ch = fis.getChannel();
            long size = ch.size();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            int chunk = 256 * 1024;
            long[] positions = {0, Math.max(0, size / 2 - chunk / 2), Math.max(0, size - chunk)};
            ByteBuffer buf = ByteBuffer.allocate(chunk);
            for (long pos : positions) {
                buf.clear();
                int n = ch.read(buf, pos);
                if (n > 0) md.update(buf.array(), 0, n);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(size).append(':');
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (pfd != null) pfd.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------ 결과 표시

    private void showResult(List<List<Item>> found) {
        groups.clear();
        rows.clear();
        checked.clear();
        long waste = 0;
        int gi = 0;
        for (List<Item> g : found) {
            // 남길 파일: 가장 먼저 저장된 것
            Collections.sort(g, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return Long.compare(a.dateAdded, b.dateAdded);
                }
            });
            for (Item it : g) it.group = gi;
            groups.add(g);
            Item first = g.get(0);
            rows.add((first.video ? "영상 " : "사진 ") + (gi + 1) + "묶음 · " + g.size() + "개 · 파일 하나당 "
                    + StorageActivity.fmt(first.size));
            rows.addAll(g);
            waste += first.size * (g.size() - 1);
            gi++;
        }
        applyRecommended();
        if (groups.isEmpty()) {
            summary.setText("중복으로 보이는 사진·영상이 없습니다.");
        } else {
            summary.setText("중복 " + groups.size() + "묶음 · 모두 정리하면 약 " + StorageActivity.fmt(waste) + " 확보");
        }
        adapter.notifyDataSetChanged();
        updateButtons();
    }

    /** 각 묶음에서 가장 먼저 저장된 1개만 남기고 나머지를 선택 */
    private void applyRecommended() {
        checked.clear();
        for (List<Item> g : groups) {
            for (int i = 1; i < g.size(); i++) checked.add(key(g.get(i)));
        }
    }

    private void updateButtons() {
        int n = checked.size();
        btnDelete.setText(n == 0 ? "선택한 파일 휴지통으로" : "선택한 " + n + "개 휴지통으로");
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
                    t = Ui.text(DupMediaActivity.this, "", 14, Color.WHITE, true);
                    t.setBackgroundColor(Ui.NAVY);
                    t.setPadding(Ui.dp(DupMediaActivity.this, 12), Ui.dp(DupMediaActivity.this, 8),
                            Ui.dp(DupMediaActivity.this, 12), Ui.dp(DupMediaActivity.this, 8));
                } else {
                    t = (TextView) convertView;
                }
                t.setText((String) o);
                return t;
            }
            final Item it = (Item) o;
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(DupMediaActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(Color.WHITE);
                row.setPadding(Ui.dp(DupMediaActivity.this, 8), Ui.dp(DupMediaActivity.this, 8),
                        Ui.dp(DupMediaActivity.this, 10), Ui.dp(DupMediaActivity.this, 8));
                CheckBox cb = new CheckBox(DupMediaActivity.this);
                cb.setTag("cb");
                cb.setFocusable(false);
                row.addView(cb);
                ImageView iv = new ImageView(DupMediaActivity.this);
                iv.setTag("iv");
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        Ui.dp(DupMediaActivity.this, 64), Ui.dp(DupMediaActivity.this, 64));
                ilp.setMargins(0, 0, Ui.dp(DupMediaActivity.this, 10), 0);
                row.addView(iv, ilp);
                LinearLayout col = new LinearLayout(DupMediaActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView n = Ui.text(DupMediaActivity.this, "", 15, Ui.INK, true);
                n.setTag("name");
                n.setMaxLines(1);
                TextView m = Ui.text(DupMediaActivity.this, "", 12, Ui.GRAY, false);
                m.setTag("meta");
                col.addView(n);
                col.addView(m);
                row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                row = (LinearLayout) convertView;
            }
            CheckBox cb = (CheckBox) row.findViewWithTag("cb");
            final ImageView iv = (ImageView) row.findViewWithTag("iv");
            TextView name = (TextView) row.findViewWithTag("name");
            TextView meta = (TextView) row.findViewWithTag("meta");

            boolean isKeeper = groups.get(it.group).get(0) == it;
            name.setText((isKeeper ? "[남김 추천] " : "") + it.name);
            String folder = it.folder == null ? "" : it.folder;
            meta.setText(StorageActivity.fmt(it.size) + " · " + dateFmt.format(new Date(it.dateAdded))
                    + (it.video ? " · 영상" : " · 사진") + (folder.isEmpty() ? "" : "\n" + folder));
            cb.setChecked(checked.contains(key(it)));
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (((CheckBox) v).isChecked()) checked.add(key(it));
                    else checked.remove(key(it));
                    updateButtons();
                }
            });

            // 미리보기: 누르면 갤러리 앱으로 열기
            iv.setImageDrawable(null);
            iv.setTag(R_TAG, it.uri.toString());
            iv.setBackgroundColor(Color.rgb(230, 230, 235));
            loadThumb(iv, it);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(it.uri, it.video ? "video/*" : "image/*");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(i);
                    } catch (Exception e) {
                        Ui.toast(DupMediaActivity.this, "미리 볼 수 있는 앱이 없습니다.");
                    }
                }
            });
            return row;
        }
    }

    private static final int R_TAG = 0x7f0f0001;

    private void loadThumb(final ImageView iv, final Item it) {
        if (Build.VERSION.SDK_INT < 29) return;
        final String want = it.uri.toString();
        try {
            thumbPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final Bitmap bm = getContentResolver().loadThumbnail(it.uri, new Size(160, 160), null);
                        iv.post(new Runnable() {
                            @Override
                            public void run() {
                                if (want.equals(iv.getTag(R_TAG))) iv.setImageBitmap(bm);
                            }
                        });
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ 휴지통으로

    private void confirmTrash() {
        if (checked.isEmpty()) {
            Ui.toast(this, "정리할 파일을 먼저 체크해 주세요.");
            return;
        }
        if (Build.VERSION.SDK_INT < 30) {
            Ui.toast(this, "이 기능은 안드로이드 11 이상에서만 지원합니다.");
            return;
        }
        // 한 묶음을 통째로 지우는 실수를 막는다
        for (List<Item> g : groups) {
            boolean keepOne = false;
            for (Item it : g) if (!checked.contains(key(it))) keepOne = true;
            if (!keepOne) {
                Ui.toast(this, "같은 묶음의 파일을 전부 체크할 수는 없습니다. 묶음마다 1개는 남겨 주세요.");
                return;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(checked.size() + "개를 휴지통으로 보낼까요?")
                .setMessage("갤러리 휴지통에 30일 동안 보관되어서 그 안에는 되살릴 수 있습니다.\n"
                        + "묶음마다 남길 파일이 맞는지 한 번 더 확인하세요.")
                .setPositiveButton("휴지통으로", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doTrash();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void doTrash() {
        List<Uri> uris = new ArrayList<>();
        for (List<Item> g : groups) {
            for (Item it : g) if (checked.contains(key(it))) uris.add(it.uri);
        }
        try {
            PendingIntent pi = MediaStore.createTrashRequest(getContentResolver(), uris, true);
            startIntentSenderForResult(pi.getIntentSender(), REQ_TRASH, null, 0, 0, 0);
        } catch (Exception e) {
            Ui.toast(this, "휴지통 이동 요청을 보내지 못했습니다.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TRASH) {
            if (resultCode == RESULT_OK) {
                Ui.toast(this, "휴지통으로 보냈습니다. 다시 찾아봅니다.");
                startScan();
            } else {
                Ui.toast(this, "취소했습니다.");
            }
        }
    }
}
