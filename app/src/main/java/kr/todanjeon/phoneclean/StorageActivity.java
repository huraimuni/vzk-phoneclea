package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import android.content.Intent;

/** 저장공간 관리 메뉴: 저장공간 현황 + 중복 사진·영상 / 중복 연락처 찾기. */
public class StorageActivity extends Activity {

    private TextView usage;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "저장공간 관리", "저장공간 사용량을 보고 정리하러 갑니다"));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        usage = Ui.text(this, "", 16, Ui.INK, true);
        body.addView(usage);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 14));
        blp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        body.addView(bar, blp);

        addCard(body, "저장공간 정리",
                "폰이 제공하는 저장공간 화면에서 큰 파일, 안 쓰는 앱, 중복 파일을 확인하고 정리할 수 있습니다. "
                        + "삼성 폰은 [설정 > 배터리 및 디바이스 케어 > 저장공간]에서도 볼 수 있습니다.",
                "폰 저장공간 화면 열기", Ui.CORAL, new Runnable() {
                    @Override
                    public void run() {
                        Ui.openFirst(StorageActivity.this,
                                new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                                new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                });

        TextView note = Ui.text(this,
                "참고\n- 이 앱은 사진·연락처 같은 개인 자료에 접근하지 않고, 저장공간 사용량만 보여 줍니다.\n"
                        + "- 인터넷 권한이 없어서 폰 밖으로 아무것도 나가지 않습니다.",
                13, Ui.GRAY, false);
        note.setLineSpacing(0, 1.2f);
        body.addView(note);

        Button back = Ui.button(this, "돌아가기", Ui.NAVY, Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 14), 0, 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        body.addView(back, lp);

        setContentView(root);
        refreshUsage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsage();
    }

    private void refreshUsage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            long used = total - free;
            usage.setText("저장공간  사용 " + fmt(used) + " / 전체 " + fmt(total) + "  (남은 " + fmt(free) + ")");
            bar.setProgress(total > 0 ? (int) (used * 1000 / total) : 0);
        } catch (Exception e) {
            usage.setText("저장공간 정보를 읽지 못했습니다.");
        }
    }

    static String fmt(long b) {
        double gb = b / 1073741824.0;
        if (gb >= 1) return String.format(java.util.Locale.KOREA, "%.1fGB", gb);
        double mb = b / 1048576.0;
        if (mb >= 1) return String.format(java.util.Locale.KOREA, "%.0fMB", mb);
        return (b / 1024) + "KB";
    }

    private void addCard(LinearLayout parent, String title, String desc, String btnText, int color, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(Color.WHITE, 14, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.addView(Ui.text(this, title, 17, Ui.CORAL_DARK, true));
        TextView d = Ui.text(this, desc, 14, Ui.INK, false);
        d.setLineSpacing(0, 1.2f);
        d.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        card.addView(d);
        Button b = Ui.button(this, btnText, color, Color.WHITE);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        card.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 10));
        parent.addView(card, lp);
    }
}
