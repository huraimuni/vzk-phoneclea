package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 폰이 멈춰서 조작이 안 될 때의 응급 순서 안내. */
public class EmergencyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "응급 가이드", "폰이 멈추고 조작이 안 될 때 순서대로"));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        step(body, "1단계. 폰 다시 켜기",
                "화면이 안 움직이면 전원 버튼을 길게 눌러 끄고 다시 켭니다. 안 꺼지면 전원 버튼과 볼륨 아래 버튼을 "
                        + "7초 이상 같이 누릅니다. (기종마다 다를 수 있습니다.)", null, null);

        step(body, "2단계. 안전 모드로 켜기",
                "안전 모드에서는 내가 설치한 앱이 꺼진 채로 켜져서 훨씬 가볍게 조작할 수 있습니다.\n"
                        + "- 방법 A: 전원 버튼을 길게 눌러 전원 끄기 메뉴를 띄운 뒤, [전원 끄기] 아이콘을 길게 눌러 [안전 모드]를 선택합니다.\n"
                        + "- 방법 B: 폰을 끄고 다시 켠 뒤, 로고가 나올 때 볼륨 아래 버튼을 계속 누르고 있습니다. "
                        + "화면 왼쪽 아래에 '안전 모드'라고 나오면 성공입니다.\n"
                        + "일반 모드로 돌아오려면 그냥 재부팅하면 됩니다.", null, null);

        step(body, "3단계. 최근에 깔린 앱부터 삭제",
                "안전 모드에서 이 앱(VZK 폰클린)을 열어 [1단계 원클릭 전체 일시 중지]와 [2단계 삭제]를 쓰거나, "
                        + "설정 > 앱에서 모르는 앱을 하나씩 삭제합니다. 플레이스토어의 [앱 및 기기 관리 > 관리]에서 "
                        + "여러 개를 한꺼번에 체크해 지울 수도 있습니다.", null, null);

        step(body, "4단계. 삭제가 안 되는 앱",
                "[기기 관리자]나 [접근성]에 등록된 앱은 거기서 먼저 끄고 나서 삭제해야 합니다.",
                "기기 관리자 설정 열기", new Runnable() {
                    @Override
                    public void run() {
                        Ui.openFirst(EmergencyActivity.this, new Intent("android.settings.DEVICE_ADMIN_SETTINGS"),
                                new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                });
        step(body, "", "", "접근성 설정 열기", new Runnable() {
            @Override
            public void run() {
                Ui.openFirst(EmergencyActivity.this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), null);
            }
        });

        step(body, "5단계. 정리가 끝난 뒤 점검",
                "- 구글 계정 비밀번호를 바꿉니다.\n"
                        + "- 모르는 결제 문자나 이상한 인증 문자가 왔는지 확인합니다. 의심되면 카드사·은행에 먼저 연락하세요.\n"
                        + "- [광고·알고리즘 정리]를 따라 해서 같은 광고가 다시 나오지 않게 합니다.\n"
                        + "- 인터넷 침해·금융 피해가 걱정되면 한국인터넷진흥원 118 상담센터(전화 118)에 문의할 수 있습니다.",
                null, null);

        step(body, "6단계. 그래도 안 될 때",
                "중요한 사진과 연락처를 백업한 뒤 서비스센터에서 점검받거나, 설정의 초기화를 고려하세요. "
                        + "초기화하면 폰 안의 모든 자료가 지워지니 백업이 먼저입니다.", null, null);

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

    private void step(LinearLayout parent, String title, String desc, String btnText, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(Color.WHITE, 14, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        if (!title.isEmpty()) card.addView(Ui.text(this, title, 17, Ui.CORAL_DARK, true));
        if (!desc.isEmpty()) {
            TextView d = Ui.text(this, desc, 14, Ui.INK, false);
            d.setLineSpacing(0, 1.25f);
            d.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
            card.addView(d);
        }
        if (btnText != null) {
            Button b = Ui.button(this, btnText, Ui.CORAL, Color.WHITE);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    action.run();
                }
            });
            card.addView(b);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 10));
        parent.addView(card, lp);
    }
}
