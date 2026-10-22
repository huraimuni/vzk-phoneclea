package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 알림 정리: (1) 지금 쌓인 알림을 앱별로 비우기 (2) 앱별 알림 끄기 화면 바로 열기 */
public class NotifActivity extends Activity {

    private LinearLayout body;
    private LinearLayout stackBox;
    private LinearLayout appBox;
    private final Set<String> picked = new HashSet<>();
    private List<AppEntry> apps = new ArrayList<>();
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "알림 정리", "알림이 많이 오는 앱을 찾아 끄거나 비웁니다"));

        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        body.addView(Ui.text(this, "1. 지금 쌓여 있는 알림 비우기", 17, Ui.CORAL_DARK, true));
        stackBox = new LinearLayout(this);
        stackBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(stackBox);

        body.addView(Ui.space(this, 14));
        body.addView(Ui.text(this, "2. 앱별 알림 끄기", 17, Ui.CORAL_DARK, true));
        TextView d = Ui.text(this, "알림이 자꾸 뜨는 앱의 [알림 끄기]를 누르면 그 앱의 알림 설정 화면이 열립니다. "
                + "거기서 스위치를 끄세요. 이 앱이 대신 꺼 줄 수는 없습니다. 의심 순으로 보여 드립니다.", 14, Ui.INK, false);
        d.setLineSpacing(0, 1.2f);
        body.addView(d);
        Button all = Ui.button(this, "폰 전체 앱 알림 설정 열기", Ui.NAVY, Color.WHITE);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent a = new Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS");
                Intent b = new Intent(Settings.ACTION_SETTINGS);
                Ui.openFirst(NotifActivity.this, a, b);
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        body.addView(all, alp);
        appBox = new LinearLayout(this);
        appBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(appBox);
        appBox.addView(Ui.text(this, "앱 목록을 불러오는 중...", 14, Ui.GRAY, false));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> r = Scanner.scan(NotifActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        apps = r;
                        loaded = true;
                        renderApps();
                    }
                });
            }
        }).start();
        renderStack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStack();
    }

    private boolean listenerOn() {
        Set<String> s = Scanner.enabledListenerPackages(this);
        return s.contains(getPackageName()) && NotifListener.instance != null;
    }

    private boolean listenerGranted() {
        return Scanner.enabledListenerPackages(this).contains(getPackageName());
    }

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private void renderStack() {
        stackBox.removeAllViews();
        if (!listenerGranted()) {
            TextView t = Ui.text(this, "쌓인 알림을 앱별로 한 번에 비우려면 [알림 접근] 허용이 필요합니다.\n"
                    + "- 이 앱은 알림 내용을 읽지 않고, 어느 앱이 몇 개 보냈는지만 셉니다.\n"
                    + "- 인터넷 권한이 없어서 밖으로 나가지 않습니다.\n"
                    + "- 이 권한은 악성 앱도 노리는 권한이라, 쓰고 나면 다시 꺼 두기를 권합니다.", 14, Ui.INK, false);
            t.setLineSpacing(0, 1.2f);
            stackBox.addView(t);
            Button b = Ui.button(this, "알림 접근 허용하러 가기", Ui.AMBER, Color.WHITE);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(NotifActivity.this)
                            .setTitle("알림 접근 허용")
                            .setMessage("다음 화면에서 [VZK 폰클린]을 눌러 켜 주세요. 켠 뒤 이 화면으로 돌아오세요.\n"
                                    + "다 쓰면 같은 곳에서 꺼 두시면 됩니다.")
                            .setPositiveButton("설정 열기", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Ui.openFirst(NotifActivity.this,
                                            new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                                            new Intent(Settings.ACTION_SETTINGS));
                                }
                            })
                            .setNegativeButton("취소", null)
                            .show();
                }
            });
            stackBox.addView(b);
            return;
        }
        NotifListener nl = NotifListener.instance;
        if (nl == null) {
            stackBox.addView(Ui.text(this, "연결하는 중입니다. 잠시 후 다시 들어와 주세요.", 14, Ui.GRAY, false));
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            StatusBarNotification[] list = nl.getActiveNotifications();
            if (list != null) {
                for (StatusBarNotification sb : list) {
                    if (sb.getPackageName().equals(getPackageName())) continue;
                    if (!sb.isClearable()) continue;
                    Integer c = counts.get(sb.getPackageName());
                    counts.put(sb.getPackageName(), c == null ? 1 : c + 1);
                }
            }
        } catch (Exception ignored) {
        }
        if (counts.isEmpty()) {
            stackBox.addView(Ui.text(this, "지금 쌓인 알림이 없습니다.", 15, Ui.GREEN, true));
            return;
        }
        int total = 0;
        for (int v : counts.values()) total += v;
        stackBox.addView(Ui.text(this, "앱 " + counts.size() + "개에서 알림 " + total + "개가 쌓여 있습니다.", 15, Ui.INK, true));
        final Set<String> choose = new HashSet<>(counts.keySet());
        for (final Map.Entry<String, Integer> en : counts.entrySet()) {
            CheckBox cb = new CheckBox(this);
            cb.setText(labelOf(en.getKey()) + "  (" + en.getValue() + "개)");
            cb.setTextSize(15);
            cb.setChecked(true);
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    if (on) choose.add(en.getKey());
                    else choose.remove(en.getKey());
                }
            });
            stackBox.addView(cb);
        }
        Button clear = Ui.button(this, "선택한 앱의 알림 비우기", Ui.CORAL, Color.WHITE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotifListener l = NotifListener.instance;
                if (l == null) return;
                int n = 0;
                try {
                    StatusBarNotification[] list = l.getActiveNotifications();
                    if (list != null) {
                        for (StatusBarNotification sb : list) {
                            if (choose.contains(sb.getPackageName()) && sb.isClearable()) {
                                l.cancelNotification(sb.getKey());
                                n++;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                Ui.toast(NotifActivity.this, n + "개를 비웠습니다.");
                renderStack();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 6), 0, 0);
        stackBox.addView(clear, lp);
    }

    private void renderApps() {
        appBox.removeAllViews();
        if (apps.isEmpty()) {
            appBox.addView(Ui.text(this, "표시할 앱이 없습니다.", 14, Ui.GRAY, false));
            return;
        }
        int shown = 0;
        for (final AppEntry e : apps) {
            if (shown >= 60) break;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.round(Color.WHITE, 12, this));
            row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
            ImageView iv = new ImageView(this);
            try {
                iv.setImageDrawable(getPackageManager().getApplicationIcon(e.pkg));
            } catch (Exception ignored) {
            }
            row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
            mid.addView(Ui.text(this, e.label, 15, Ui.INK, true));
            mid.addView(Ui.text(this, e.levelText(), 12, e.level() == AppEntry.LEVEL_HIGH ? Ui.RED
                    : e.level() == AppEntry.LEVEL_WARN ? Ui.AMBER : Ui.GRAY, false));
            row.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button b = Ui.button(this, "알림 끄기", Ui.NAVY, Color.WHITE);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.toast(NotifActivity.this, "[알림 허용] 스위치 하나만 끄고 뒤로 가기를 누르세요.");
                    Intent a = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    a.putExtra(Settings.EXTRA_APP_PACKAGE, e.pkg);
                    Intent fb = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
                    Ui.openFirst(NotifActivity.this, a, fb);
                }
            });
            row.addView(b);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, Ui.dp(this, 6));
            appBox.addView(row, rlp);
        }
    }
}
