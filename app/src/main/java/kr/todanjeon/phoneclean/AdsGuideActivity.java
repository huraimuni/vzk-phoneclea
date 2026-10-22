package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 광고·알고리즘 정리 안내 화면.
 * 구글 계정 설정은 앱이 대신 바꿀 수 없어서, 해당 화면까지 바로 이동시키고 순서를 안내합니다.
 */
public class AdsGuideActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "광고·알고리즘 정리", "이상한 광고가 계속 나올 때 순서대로 따라 하세요"));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView intro = Ui.text(this,
                "유튜브에서 PDF 뷰어, 청소 앱 같은 광고가 계속 보이는 건, 구글이 쌓아 둔 '광고 취향'이 그쪽으로 기울어서입니다. "
                        + "아래 순서대로 취향을 끊고 기록을 비우면 광고가 서서히 바뀝니다. "
                        + "(하루 만에 바뀌지는 않고, 며칠~몇 주 걸릴 수 있습니다.)",
                15, Ui.INK, false);
        intro.setLineSpacing(0, 1.2f);
        body.addView(intro);
        body.addView(Ui.space(this, 10));

        addStep(body, "1단계. 광고 ID 삭제",
                "광고 취향을 폰에 묶어 두는 번호입니다. 삭제하면 지금까지 쌓인 맞춤 광고 연결이 끊깁니다. "
                        + "열리는 화면에서 [광고 ID 삭제]를 누르세요. (기기에 따라 [광고 ID 재설정]일 수 있습니다.)",
                "광고 ID 설정 열기", new Runnable() {
                    @Override
                    public void run() {
                        Ui.openFirst(AdsGuideActivity.this,
                                new Intent("com.google.android.gms.settings.ADS_PRIVACY"),
                                new Intent(Settings.ACTION_PRIVACY_SETTINGS));
                    }
                });

        addStep(body, "2단계. 구글 맞춤 광고 끄기",
                "구글 광고 센터에서 [맞춤 광고]를 끕니다. 로그인한 구글 계정으로 열립니다.",
                "광고 센터 열기", new Runnable() {
                    @Override
                    public void run() {
                        Ui.openUrl(AdsGuideActivity.this, "https://myadcenter.google.com");
                    }
                });

        addStep(body, "3단계. 유튜브 기록 지우기",
                "유튜브 시청 기록과 검색 기록을 지우고, 기록 저장을 일시중지하세요. "
                        + "열리는 화면에서 [삭제]와 [사용 중지]를 누르면 됩니다.",
                "유튜브 기록 관리 열기", new Runnable() {
                    @Override
                    public void run() {
                        Ui.openUrl(AdsGuideActivity.this, "https://myactivity.google.com/product/youtube");
                    }
                });

        addStep(body, "4단계. 플레이 프로텍트 켜기",
                "구글이 앱을 검사해 주는 기능입니다. 켜져 있는지 확인하세요.\n"
                        + "버튼이 바로 안 열리면: 플레이스토어 > 오른쪽 위 내 프로필 사진 > [Play 프로텍트] > 오른쪽 위 톱니바퀴.",
                "플레이 프로텍트 열기", new Runnable() {
                    @Override
                    public void run() {
                        Intent play = getPackageManager().getLaunchIntentForPackage("com.android.vending");
                        Intent direct = new Intent("com.google.android.gms.security.settings.VerifyAppsSettingsActivity");
                        try {
                            startActivity(direct);
                        } catch (Exception e) {
                            if (play != null) {
                                Ui.toast(AdsGuideActivity.this, "플레이스토어가 열립니다. 프로필 사진 > Play 프로텍트를 눌러 주세요.");
                            }
                            Ui.openFirst(AdsGuideActivity.this, play != null ? play : new Intent(Settings.ACTION_SECURITY_SETTINGS),
                                    new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    }
                });

        addStep(body, "5단계. 앱 설치 허용 확인",
                "'출처를 알 수 없는 앱 설치'가 허용된 앱이 있으면 광고를 누르는 것만으로 앱이 깔릴 수 있습니다. "
                        + "목록에서 허용된 앱이 있으면 [허용 안 함]으로 바꾸세요.",
                "설치 허용 앱 확인", new Runnable() {
                    @Override
                    public void run() {
                        Ui.openFirst(AdsGuideActivity.this,
                                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
                                new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                });

        addStep(body, "6단계. 설치가 막힐 때 (삼성 폰)",
                "삼성 폰의 [보안 위험 자동 차단]이 켜져 있으면 앱 설치 자체가 막힙니다. "
                        + "이 앱이 대신 끌 수는 없고, 직접 끄셔야 합니다.\n"
                        + "경로: 설정 > 보안 및 개인정보 보호 > 보안 위험 자동 차단 > 끄기. "
                        + "(설치를 마친 뒤에는 다시 켜 두는 것이 안전합니다.)",
                "보안 설정 열기", new Runnable() {
                    @Override
                    public void run() {
                        Ui.toast(AdsGuideActivity.this, "보안 및 개인정보 보호 > 보안 위험 자동 차단을 찾아 주세요.");
                        Ui.openFirst(AdsGuideActivity.this,
                                new Intent(Settings.ACTION_PRIVACY_SETTINGS),
                                new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                });

        TextView tip = Ui.text(this,
                "평소 습관\n"
                        + "- 유튜브 광고는 눌러서 설치하지 말고, 광고 오른쪽 위 [i] 또는 점 세 개에서 [광고 신고]를 누르세요.\n"
                        + "- 앱은 플레이스토어에서 직접 검색해서만 설치하세요.\n"
                        + "- 처음 보는 앱이 '접근성'이나 '기기 관리자' 권한을 달라고 하면 거절하세요.",
                14, Ui.INK, false);
        tip.setLineSpacing(0, 1.25f);
        tip.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        tip.setBackground(Ui.round(Color.rgb(255, 240, 230), 12, this));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        body.addView(tip, tlp);

        Button back = Ui.button(this, "돌아가기", Ui.NAVY, Color.WHITE);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        body.addView(back);

        setContentView(root);
    }

    private void addStep(LinearLayout parent, String title, String desc, String btnText, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(Color.WHITE, 14, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));

        TextView t = Ui.text(this, title, 17, Ui.CORAL_DARK, true);
        card.addView(t);
        TextView d = Ui.text(this, desc, 14, Ui.INK, false);
        d.setLineSpacing(0, 1.2f);
        d.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        card.addView(d);
        Button b = Ui.button(this, btnText, Ui.CORAL, Color.WHITE);
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
