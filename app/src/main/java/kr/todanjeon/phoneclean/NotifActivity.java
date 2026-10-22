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
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "알림 정리", "알림이 자꾸 뜨는 앱을 찾아 끕니다"));

        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        body.addView(Ui.text(this, "앱별 알림 끄기", 17, Ui.CORAL_DARK, true));
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
    }

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
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
