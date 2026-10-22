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
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "저장공간 관리", "겹치는 사진·영상과 연락처를 찾아 정리합니다"));

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

        addCard(body, "중복 사진·영상",
                "같은 내용인데 여러 번 저장된 사진과 영상을 묶어서 보여 줍니다. "
                        + "남길 파일 1개만 빼고 나머지를 휴지통으로 보낼 수 있습니다. (휴지통 30일 보관, 안드로이드 11 이상)",
                "중복 사진·영상 찾기", Ui.CORAL, new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(StorageActivity.this, DupMediaActivity.class));
                    }
                });

        addCard(body, "중복 연락처",
                "같은 전화번호가 여러 연락처에 들어 있는 경우를 묶어서 보여 줍니다. "
                        + "삭제하면 되돌릴 수 없으니, 먼저 구글 연락처 앱에서 내보내기(백업)를 해 두세요.",
                "중복 연락처 찾기", Ui.GREEN, new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(StorageActivity.this, DupContactsActivity.class));
                    }
                });

        TextView note = Ui.text(this,
                "참고\n- 같은 파일인지는 파일 크기와 내용 일부를 비교해서 추정합니다. 지우기 전에 미리보기로 꼭 확인하세요.\n"
                        + "- 이 앱은 인터넷 권한이 없어서, 사진·연락처가 밖으로 나가지 않습니다.",
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
