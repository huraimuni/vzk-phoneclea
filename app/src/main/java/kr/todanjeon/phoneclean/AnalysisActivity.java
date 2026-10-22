package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 새로 설치된 앱 하나의 분석 결과를 팝업(대화상자 모양)으로 보여 줍니다. */
public class AnalysisActivity extends Activity {

    private LinearLayout box;
    private String pkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pkg = getIntent().getStringExtra("pkg");

        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 12));
        sv.addView(box);
        setContentView(sv);
        setTitle("새 앱 분석");

        box.addView(Ui.text(this, "분석하는 중입니다...", 16, Ui.INK, true));
        if (pkg == null) {
            finish();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<AppEntry> list = Scanner.scan(AnalysisActivity.this);
                AppEntry found = null;
                for (AppEntry e : list) {
                    if (e.pkg.equals(pkg)) {
                        found = e;
                        break;
                    }
                }
                final AppEntry result = found;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        render(result);
                    }
                });
            }
        }).start();
    }

    private void render(final AppEntry e) {
        box.removeAllViews();
        if (e == null) {
            box.addView(Ui.text(this, "이 앱은 이미 삭제됐거나 분석 대상이 아닙니다.", 16, Ui.INK, true));
            addClose();
            return;
        }
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(e.pkg);
            iv.setImageDrawable(d);
        } catch (Exception ignored) {
        }
        head.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        TextView name = Ui.text(this, "  " + e.label, 20, Ui.INK, true);
        head.addView(name);
        box.addView(head);

        int color = e.level() == AppEntry.LEVEL_HIGH ? Ui.RED
                : e.level() == AppEntry.LEVEL_WARN ? Ui.AMBER : Ui.GREEN;
        TextView verdict = Ui.text(this, "판정: " + e.levelText()
                + (e.tagText().isEmpty() ? "" : "\n" + e.tagText()), 18, Color.WHITE, true);
        verdict.setBackground(Ui.round(color, 12, this));
        verdict.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 8));
        box.addView(verdict, vlp);

        String date = e.installTime > 0
                ? new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date(e.installTime)) : "모름";
        TextView info = Ui.text(this, "설치 경로: " + e.installerText + "\n설치 시각: " + date, 14, Ui.GRAY, false);
        box.addView(info);

        StringBuilder sb = new StringBuilder();
        if (e.reasons.isEmpty()) {
            sb.append("의심스러운 징후가 발견되지 않았습니다.");
        } else {
            sb.append("분석 결과\n");
            for (String r : e.reasons) sb.append("- ").append(r).append("\n");
        }
        sb.append("\n내가 직접 설치한 앱이 아니라면 삭제를 권합니다.");
        TextView why = Ui.text(this, sb.toString(), 15, Ui.INK, false);
        why.setLineSpacing(0, 1.2f);
        why.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 8));
        box.addView(why);

        Button del = Ui.button(this, "이 앱 삭제", Ui.RED, Color.WHITE);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg)));
                } catch (Exception ex) {
                    Ui.toast(AnalysisActivity.this, "삭제 창을 열 수 없습니다.");
                }
            }
        });
        Button pause = Ui.button(this, "일시 중지 (백그라운드 종료)", Ui.CORAL, Color.WHITE);
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    am.killBackgroundProcesses(e.pkg);
                    Ui.toast(AnalysisActivity.this, "정지 명령을 보냈습니다. (임시 조치)");
                } catch (Exception ex) {
                    Ui.toast(AnalysisActivity.this, "일시 중지하지 못했습니다.");
                }
            }
        });
        Button safe = Ui.button(this, "내가 아는 안전한 앱 (표시하고 닫기)", Ui.GREEN, Color.WHITE);
        safe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences sp = getSharedPreferences(NewAppJob.PREFS, MODE_PRIVATE);
                Set<String> cur = new HashSet<>();
                Set<String> old = sp.getStringSet("safe_pkgs", null);
                if (old != null) cur.addAll(old);
                cur.add(e.pkg);
                sp.edit().putStringSet("safe_pkgs", cur).apply();
                Ui.toast(AnalysisActivity.this, "안전한 앱으로 표시했습니다.");
                finish();
            }
        });
        Button info2 = Ui.button(this, "앱 정보 열기", Color.rgb(226, 230, 244), Ui.NAVY);
        info2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.openFirst(AnalysisActivity.this,
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg)), null);
            }
        });
        Button[] bs = {del, pause, safe, info2};
        for (Button b : bs) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            box.addView(b, lp);
        }
        addClose();
    }

    private void addClose() {
        Button close = Ui.button(this, "닫기", Ui.NAVY, Color.WHITE);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 4), 0, 0);
        box.addView(close, lp);
    }
}
