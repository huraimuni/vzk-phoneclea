package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
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
import java.util.Collections;
import java.util.Comparator;
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
    private TextView homeBanner;
    private TextView usageHint;
    private boolean lastUsageState = false;
    private static final int REQ_NOTI = 31;
    private final Set<String> safeSet = new HashSet<>();
    private final Set<String> paused = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private android.widget.FrameLayout rootFrame;
    private boolean resumed = false;
    private final Runnable keepTask = new Runnable() {
        @Override
        public void run() {
            if (resumed && cbKeep != null && cbKeep.isChecked() && !scanning) {
                sendStop(targets());
                handler.postDelayed(this, 4000);
            }
        }
    };

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(getApplicationContext());
        loadSafe();
        buildUi();
        if (CrashLog.hasCrash(this)) offerCrashReport();
        if (savedInstanceState == null && rootFrame != null) showSplash(rootFrame);
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
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);

        root.addView(Ui.header(this, "VZK 폰클린",
                "광고로 깔린 의심 앱을 찾아 한 번에 정리합니다"));

        // 글자를 크게 키워 쓰는 폰에서도 목록이 보이도록, 위쪽 안내·버튼들은 목록과 함께 스크롤되게 한다
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);

        summary = Ui.text(this, "앱을 분석하는 중입니다...", 15, Ui.INK, true);
        summary.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6));
        top.addView(summary);

        usageHint = Ui.text(this, "", 12, Ui.GRAY, false);
        usageHint.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 2));
        top.addView(usageHint);

        btnPause = Ui.button(this, "1단계 ▸ 원클릭 전체 일시 중지", Ui.CORAL, Color.WHITE);
        btnPause.setTextSize(19);
        btnPause.setPadding(Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18));
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPause();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.setMargins(Ui.dp(this, 11), Ui.dp(this, 2), Ui.dp(this, 11), 0);
        top.addView(btnPause, plp);

        TextView pauseNote = Ui.text(this,
                "이 앱, 시스템 앱, 키보드·홈 화면·문자·전화 앱은 빼고 모두 멈춥니다",
                12, Ui.GRAY, false);
        pauseNote.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), 0);
        top.addView(pauseNote);

        homeBanner = Ui.text(this, "", 14, Color.WHITE, true);
        homeBanner.setBackground(Ui.round(Ui.RED, 10, this));
        homeBanner.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        homeBanner.setVisibility(View.GONE);
        homeBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreHome();
            }
        });
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(Ui.dp(this, 11), Ui.dp(this, 6), Ui.dp(this, 11), 0);
        top.addView(homeBanner, hlp);

        LinearLayout pauseRow = new LinearLayout(this);
        pauseRow.setOrientation(LinearLayout.HORIZONTAL);
        pauseRow.setGravity(Gravity.CENTER_VERTICAL);
        pauseRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 2));
        cbKeep = new CheckBox(this);
        cbKeep.setText("정지 유지 (4초마다 반복, 카톡 알림이 늦을 수 있음)");
        cbKeep.setTextSize(13);
        cbKeep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.removeCallbacks(keepTask);
                if (cbKeep.isChecked()) handler.post(keepTask);
            }
        });
        pauseRow.addView(cbKeep, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button homeBtn = Ui.button(this, "기본 홈 화면 되돌리기", Color.rgb(226, 230, 244), Ui.NAVY);
        homeBtn.setTextSize(12);
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreHome();
            }
        });
        pauseRow.addView(homeBtn);
        top.addView(pauseRow);

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
        Button btnSelect = Ui.button(this, "의심 앱 선택", Color.rgb(255, 226, 214), Ui.CORAL_DARK);
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
        Button btnClear = Ui.button(this, "선택 해제", Color.rgb(226, 230, 244), Ui.NAVY);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected.clear();
                refreshList();
            }
        });
        btnFilter.setTextSize(13);
        btnSelect.setTextSize(13);
        btnClear.setTextSize(13);
        row1.addView(btnSelect, Ui.weight(this, 3));
        row1.addView(btnClear, Ui.weight(this, 3));
        top.addView(row1);

        listView = new ListView(this);
        listView.setDividerHeight(Ui.dp(this, 1));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(228, 224, 220)));
        adapter = new RowAdapter();
        listView.addHeaderView(top, null, false);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - listView.getHeaderViewsCount();
                if (idx >= 0 && idx < shown.size()) showDetail(shown.get(idx));
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
        Button b1 = Ui.button(this, "안전 앱으로 표시", Ui.GREEN, Color.WHITE);
        b1.setTextSize(13);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markSelectedSafe();
            }
        });
        Button b2 = Ui.button(this, "저장공간 관리", Ui.CORAL, Color.WHITE);
        b2.setTextSize(13);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StorageActivity.class));
            }
        });
        Button b3 = Ui.button(this, "더보기", Ui.NAVY, Color.WHITE);
        b3.setTextSize(13);
        b3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore();
            }
        });
        row2.addView(b1, Ui.weight(this, 3));
        row2.addView(b2, Ui.weight(this, 3));
        row2.addView(b3, Ui.weight(this, 3));
        root.addView(row2);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        rootFrame = frame;
    }

    /** 앱을 처음 켤 때 로고를 1.2초쯤 보여 준 뒤 서서히 사라지게 한다. */
    private void showSplash(final android.widget.FrameLayout frame) {
        final SplashView sv = new SplashView(this);
        frame.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sv.animate().alpha(0f).setDuration(350).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        frame.removeView(sv);
                    }
                }).start();
            }
        }, 1200);
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        summary.setText("앱을 분석하는 중입니다...");
        final Runnable progress = new Runnable() {
            @Override
            public void run() {
                if (!scanning) return;
                int t = Scanner.progressTotal;
                if (t > 0) summary.setText("앱을 분석하는 중입니다... " + Scanner.progressDone + " / " + t);
                handler.postDelayed(this, 400);
            }
        };
        handler.post(progress);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> result;
                try {
                    result = Scanner.scan(MainActivity.this);
                } catch (final Throwable ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scanning = false;
                            CrashLog.record(MainActivity.this, "분석 전체", ex);
                            summary.setText("분석 중 오류가 났습니다: " + ex.getClass().getSimpleName()
                                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage())
                                    + "\n[전체 앱 보기]를 눌러 다시 시도해 보세요.");
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        all.clear();
                        all.addAll(result);
                        for (AppEntry e : all) e.safe = safeSet.contains(e.pkg);
                        Collections.sort(all, new Comparator<AppEntry>() {
                            @Override
                            public int compare(AppEntry x, AppEntry y) {
                                int sx = x.sortScore();
                                int sy = y.sortScore();
                                if (sx != sy) return sy - sx;
                                return Long.compare(y.installTime, x.installTime);
                            }
                        });
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
        updateHomeBanner();
        usageHint.setVisibility(View.GONE);
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
            String tg = e.tagText();
            meta.setText("[" + e.levelText() + "]  " + date + " 설치" + (paused.contains(e.pkg) ? "  [정지 명령 보냄]" : "")
                    + (tg.isEmpty() ? "" : "\n" + tg));
            meta.setTextColor(levelColor(e));
            if (e.reasons.isEmpty()) {
                why.setText(e.preloaded ? "출고 시 기본 설치된 앱으로 보임" : (e.trusted ? "정상 스토어에서 설치된 알려진 앱" : "특이 징후 없음"));
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
        if (Scanner.hasUsageAccess(this) != lastUsageState && !scanning && !all.isEmpty()) startScan();
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

    /** 일시 중지 대상: 직접 설치한 앱 전부. 단, 출고 기본 앱과 키보드·홈 화면·문자·전화 앱은 뺀다 */
    private List<String> targets() {
        Set<String> keep = Scanner.essentialPackages(this);
        List<String> out = new ArrayList<>();
        for (AppEntry e : all) {
            if (e.preloaded || e.safe || keep.contains(e.pkg)) continue;
            out.add(e.pkg);
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

    private void doPause() {
        if (scanning) {
            Ui.toast(this, "앱을 분석하는 중입니다. 잠시 뒤에 다시 눌러 주세요.");
            return;
        }
        final List<String> pkgs = targets();
        if (pkgs.isEmpty()) {
            Ui.toast(this, "정지할 앱이 없습니다.");
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
                .setMessage("직접 설치한 앱 " + pkgs.size() + "개의 백그라운드 실행을 멈추게 했습니다. "
                        + "(시스템 앱, 안전 표시한 앱, 키보드·홈 화면·문자·전화 앱은 제외)\n\n"
                        + "폰이 조금 가벼워졌는지 확인해 보세요. 이건 임시 조치라서, 앱이 다시 켜질 수 있습니다. "
                        + "카톡 같은 앱은 새 알림이 오면 다시 켜집니다. 다시 느려지면 한 번 더 누르세요.\n\n"
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

    // ---------------------------------------------------------------- 안전 표시 / 더보기 / AI

    private void loadSafe() {
        try {
            Set<String> st = getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("safe_pkgs", null);
            safeSet.clear();
            if (st != null) safeSet.addAll(st);
        } catch (Exception ignored) {
        }
    }

    private void saveSafe() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet("safe_pkgs", new HashSet<>(safeSet)).apply();
    }

    private void setSafe(List<String> pkgs, boolean safe) {
        for (String p : pkgs) {
            if (safe) safeSet.add(p);
            else safeSet.remove(p);
        }
        saveSafe();
        selected.removeAll(pkgs);
        for (AppEntry e : all) e.safe = safeSet.contains(e.pkg);
        Collections.sort(all, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry x, AppEntry y) {
                int sx = x.sortScore();
                int sy = y.sortScore();
                if (sx != sy) return sy - sx;
                return Long.compare(y.installTime, x.installTime);
            }
        });
        applyFilter();
        Ui.toast(this, pkgs.size() + "개 앱을 " + (safe ? "안전한 앱으로 표시했습니다." : "안전 표시에서 뺐습니다."));
    }

    private void markSelectedSafe() {
        if (selected.isEmpty()) {
            Ui.toast(this, "안전한 앱을 먼저 체크해 주세요. 목록 왼쪽 네모 칸을 누르면 됩니다.");
            return;
        }
        final List<String> pkgs = new ArrayList<>(selected);
        new AlertDialog.Builder(this)
                .setTitle(pkgs.size() + "개를 안전한 앱으로 표시할까요?")
                .setMessage("내가 알고 쓰는 앱만 표시하세요.\n표시한 앱은 의심 목록에서 빠지고, 일시 중지와 삭제 대상에서도 제외됩니다. "
                        + "[더보기 > 안전 표시한 앱 관리]에서 언제든 되돌릴 수 있습니다.")
                .setPositiveButton("안전 표시", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setSafe(pkgs, true);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void manageSafe() {
        final List<AppEntry> list = new ArrayList<>();
        for (AppEntry e : all) if (e.safe) list.add(e);
        if (list.isEmpty()) {
            Ui.toast(this, "안전 표시한 앱이 없습니다.");
            return;
        }
        final String[] names = new String[list.size()];
        final boolean[] on = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).label;
            on[i] = false;
        }
        new AlertDialog.Builder(this)
                .setTitle("안전 표시한 앱 (해제할 앱을 체크)")
                .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        on[which] = isChecked;
                    }
                })
                .setPositiveButton("안전 표시 해제", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<String> rm = new ArrayList<>();
                        for (int i = 0; i < on.length; i++) if (on[i]) rm.add(list.get(i).pkg);
                        if (!rm.isEmpty()) setSafe(rm, false);
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showMore() {
        final String[] items = {"응급 가이드 (폰이 멈췄을 때)", "광고·알고리즘 정리", "알림 정리 (알림 비우기·끄기)", "안전 표시한 앱 관리",
                watchOn() ? "새 앱 설치 알림 끄기" : "새 앱 설치 알림 켜기 (15분마다 확인)",
                "결과 공유 (카카오톡 등)", "오류 보고·건의하기", "도움말"};
        new AlertDialog.Builder(this)
                .setTitle("더보기")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) startActivity(new Intent(MainActivity.this, EmergencyActivity.class));
                        else if (which == 1) startActivity(new Intent(MainActivity.this, AdsGuideActivity.class));
                        else if (which == 2) startActivity(new Intent(MainActivity.this, NotifActivity.class));
                        else if (which == 3) manageSafe();
                        else if (which == 4) toggleWatch();
                        else if (which == 5) shareReport();
                        else if (which == items.length - 2) showFeedback();
                        else showHelp();
                    }
                })
                .show();
    }

    private boolean watchOn() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("watch_on", false);
    }

    private void toggleWatch() {
        if (watchOn()) {
            NewAppJob.cancel(this);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("watch_on", false).apply();
            Ui.toast(this, "새 앱 설치 알림을 껐습니다.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTI);
            return;
        }
        enableWatch();
    }

    private void enableWatch() {
        NewAppJob.saveBaseline(this);
        NewAppJob.schedule(this);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("watch_on", true).apply();
        Ui.toast(this, "켰습니다. 새 앱이 설치되면 15분 안에 분석 결과를 알림으로 알려 드립니다. (설치를 막지는 못하고, 설치된 뒤에 알립니다)");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTI) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableWatch();
            else Ui.toast(this, "알림 권한이 없어서 새 앱 알림을 켤 수 없습니다.");
        }
    }

    /** 앱 서버로 아무것도 보내지 않는다. 질문 글을 복사해 주고 네이버 AI(클로바X)를 브라우저로 연다. */
    private void askAi(AppEntry e) {
        String q = "안드로이드 앱 '" + e.label + "' (패키지명: " + e.pkg + ")이 안전한 앱인가요? "
                + "유튜브 광고를 누르다가 설치된 것 같은데, 지워도 되는지 알려 주세요.";
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("질문", q));
            Ui.toast(this, "네이버 검색으로 연결합니다. (질문도 복사해 두었습니다)");
        } catch (Exception ex) {
            Ui.toast(this, "복사하지 못했습니다. 앱 이름으로 직접 물어보세요.");
        }
        try {
            String sq = "앱 " + e.label + " 안전한가요 삭제해도 되나요";
            Ui.openUrl(this, "https://m.search.naver.com/search.naver?query="
                    + java.net.URLEncoder.encode(sq, "UTF-8"));
        } catch (Exception ex) {
            Ui.openUrl(this, "https://m.naver.com");
        }
    }

    // ---------------------------------------------------------------- 홈 화면(런처)

    private void updateHomeBanner() {
        String raw = Scanner.homePackage(getPackageManager());
        if (raw != null && !Scanner.isSystemPkg(getPackageManager(), raw)) {
            String name = labelOf(raw);
            homeBanner.setText("홈 화면이 폰 기본 앱이 아닌 [" + name + "](으)로 바뀌어 있습니다.\n눌러서 기본 홈으로 되돌리기");
            homeBanner.setVisibility(View.VISIBLE);
        } else {
            homeBanner.setVisibility(View.GONE);
        }
    }

    private void restoreHome() {
        PackageManager pm = getPackageManager();
        String raw = Scanner.homePackage(pm);
        boolean replaced = raw != null && !Scanner.isSystemPkg(pm, raw);
        List<String[]> homes = Scanner.systemHomes(pm);
        StringBuilder sb = new StringBuilder();
        if (replaced) {
            sb.append("지금 홈 화면: ").append(labelOf(raw)).append(" (직접 설치된 앱)\n\n");
        } else {
            sb.append("지금 홈 화면은 폰 기본 앱입니다. 그래도 다른 홈 앱으로 바꾸고 싶지 않다면 이대로 두셔도 됩니다.\n\n");
        }
        if (homes.isEmpty()) {
            sb.append("폰 기본 홈 앱을 찾지 못했습니다. 설정 화면에서 직접 골라 주세요.\n");
        } else {
            sb.append("폰 기본 홈 앱:\n");
            for (String[] h : homes) sb.append("- ").append(h[1]).append("\n");
        }
        sb.append("\n[홈 설정 열기]를 누르고, [기본 홈 앱]에서 위 기본 앱을 선택하세요. ")
                .append("끝나면 바뀌어 있던 런처 앱은 목록에서 삭제하시면 됩니다.");
        new AlertDialog.Builder(this)
                .setTitle("기본 홈 화면 되돌리기")
                .setMessage(sb.toString())
                .setPositiveButton("홈 설정 열기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Ui.openFirst(MainActivity.this,
                                new Intent(Settings.ACTION_HOME_SETTINGS),
                                new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    // ---------------------------------------------------------------- 상세 보기

    private void showDetail(final AppEntry e) {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 6));
        sv.addView(box);

        TextView lv = Ui.text(this, "판정: " + e.levelText() + (e.tagText().isEmpty() ? "" : "  (" + e.tagText() + ")"), 16, levelColor(e), true);
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
        Button safeBtn = Ui.button(this, e.safe ? "안전 표시 해제" : "안전한 앱으로 표시 (목록에서 제외)",
                Color.rgb(222, 245, 232), Color.rgb(10, 110, 60));
        safeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                List<String> one = new ArrayList<>();
                one.add(e.pkg);
                setSafe(one, !e.safe);
            }
        });
        Button aiBtn = Ui.button(this, "AI에게 물어보기 (질문 복사 + 네이버 검색)",
                Color.rgb(237, 231, 255), Color.rgb(80, 50, 170));
        aiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askAi(e);
            }
        });
        for (Button b : new Button[]{del, safeBtn, info2, aiBtn, naver, google}) {
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
        try {
            CharSequence l = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0));
            if (l != null) return l.toString();
        } catch (Exception ignored) {
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
                + "1단계 [원클릭 전체 일시 중지]를 누르면, 직접 설치한 앱 전체의 백그라운드 실행을 멈추게 합니다. 시스템 앱과 이 앱, 키보드·홈 화면·문자·전화 앱은 빼고 멈춥니다. 그러면 폰이 조금 가벼워집니다. (임시 조치)\n\n"
                + "2단계 삭제\n"
                + "- 일시 중지 뒤 안내 창에서 [삭제할 앱 고르기]를 누르면 의심 앱이 미리 체크됩니다.\n"
                + "- 내가 쓰는 앱은 체크를 풀고 (앱을 누르면 이유가 나옵니다)\n"
                + "- [2단계 ▸ 선택한 앱 삭제]를 누른 뒤, 앱마다 뜨는 삭제 확인 창에서 [확인]\n"
                + "- 이상한 광고가 계속 나오면 [광고·알고리즘 정리] 따라 하기\n\n"
                + "알아두세요\n"
                + "- 바이러스를 잡는 백신이 아닙니다. 악성 파일 DB가 없고, 징후만 보고 판단합니다.\n"
                + "- 징후가 있어도 정상 앱일 수 있습니다. 모르는 앱만 지우세요.\n"
                + "- 판정 이름: 권한 장악형 / 광고 도배형 / 방치형 / 한 패거리형(같은 개발사) / 설치 도우미형 / 도구·겁주기 위장형. 근거가 2개 이상 겹치면 [위험 의심]입니다.\n"
                + "- [더보기 > 새 앱 설치 알림]을 켜면 새 앱이 깔린 뒤 15분 안에 분석 결과가 알림으로 뜹니다. 알림을 누르면 분석 화면이 열리고 바로 삭제할 수 있습니다. (설치를 막지는 못합니다)\n"
                + "- [더보기 > 알림 정리]에서 알림이 자꾸 뜨는 앱의 알림 설정 화면으로 바로 가서 끌 수 있습니다.\n"
                + "- [더보기 > 응급 가이드]에 폰이 멈췄을 때 안전 모드 등의 순서가 있습니다.\n"
                + "- 내가 아는 안전한 앱은 체크한 뒤 [안전 앱으로 표시]를 누르면 목록·일시 중지·삭제에서 빠집니다.\n"
                + "- [저장공간 관리]에서 저장공간 사용량을 보고 폰의 저장공간 정리 화면으로 이동할 수 있습니다.\n"
                + "- 홈 화면(런처)이 바뀌어 있으면 맨 위에 빨간 안내가 뜹니다. [기본 홈 화면 되돌리기]로 설정 화면에서 기본 홈 앱을 다시 고르세요. (앱이 대신 바꿔 주지는 못합니다)\n"
                + "- 폰에 원래 들어 있던 기본 앱(출고 시 설치)은 의심 목록에서 뺍니다.\n"
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

    // ---------------------------------------------------------------- 오류 보고·건의

    private void offerCrashReport() {
        new AlertDialog.Builder(this)
                .setTitle("지난번에 앱이 오류로 꺼졌습니다")
                .setMessage("불편을 드려 죄송합니다. 오류 내용을 보내 주시면 고치는 데 큰 도움이 됩니다.\n"
                        + "오류 내용에는 앱 이름이나 개인 정보가 들어 있지 않고, 기종·안드로이드 버전·오류 문구만 있습니다. "
                        + "이 앱은 인터넷 권한이 없어서 자동으로 보내지 않고, 직접 보내기를 누르셨을 때만 공유됩니다.")
                .setPositiveButton("오류 내용 보내기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        sendErrorReport();
                        CrashLog.clearCrash(MainActivity.this);
                    }
                })
                .setNegativeButton("나중에", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        CrashLog.clearCrash(MainActivity.this);
                    }
                })
                .show();
    }

    private void sendErrorReport() {
        String text = CrashLog.buildReport(this);
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("오류 보고", text));
        } catch (Exception ignored) {
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "오류 내용 보내기"));
        } catch (Exception e) {
            Ui.toast(this, "내용을 복사했습니다. 카페 게시판에 붙여넣어 주세요.");
        }
    }

    private void showFeedback() {
        final String[] items = {"오류 내용 보내기 (카톡·문자 등으로 공유)", "건의하기 (카페 게시판 열기)"};
        new AlertDialog.Builder(this)
                .setTitle("오류 보고·건의하기")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) sendErrorReport();
                        else Ui.openUrl(MainActivity.this, Ui.CAFE_URL);
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }
}
