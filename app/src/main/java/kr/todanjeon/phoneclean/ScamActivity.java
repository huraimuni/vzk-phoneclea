package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 보이스피싱·스미싱 점검(한국어 전용): 통화 중 점검표, 의심 문자·링크 점검, 내 폰 위험 징후 점검. 모두 폰 안에서만 계산한다. */
public class ScamActivity extends BaseActivity {
    private static final String[] REMOTE = {"com.anydesk", "com.teamviewer", "com.carriez.flutter_hbb", "com.rustdesk", "com.splashtop",
            "com.rsupport", "com.logmein", "com.microsoft.rdc", "com.realvnc", "net.christianbeier.droidvnc", "com.ammyy", "com.islonline",
            "com.sand.airdroid", "com.airdroid", "com.google.chromeremotedesktop"};

    private LinearLayout body;
    private LinearLayout callResult;
    private LinearLayout textResult;
    private LinearLayout phoneResult;
    private EditText input;
    private Button phoneBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "사기 전화·문자 확인하기", "참고용입니다. 마지막 판단은 직접 하세요"));
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 28));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        buildCall();
        buildCallCheck();
        buildText();
        buildPhone();
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleShare(i);
    }

    private void handleShare(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t != null && input != null) {
            input.setText(t);
            runText();
        }
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Ui.dp(this, topDp);
        return p;
    }

    private TextView section(String title) {
        TextView t = Ui.text(this, title, 18, Ui.INK, true);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 4));
        body.addView(t);
        return t;
    }

    // ------------------------------------------------------------ 1. 통화 중 점검표
    private void buildCall() {
        TextView t = section("📞 지금 통화 중이세요?");
        t.setPadding(0, 0, 0, Ui.dp(this, 4));
        body.addView(Ui.text(this, "해당하는 것을 눌러 보세요.", 14, Ui.GRAY, false));
        final String[] items = {
                "검찰·경찰·금융감독원이라며 전화가 왔다",
                "앱이나 파일을 설치하라고 한다 (특히 원격 앱)",
                "내 폰 화면을 보여 달라고 하거나 원격으로 연결하라고 한다",
                "통장·카드에서 돈을 옮기라고 하거나 현금을 맡기라고 한다",
                "전화를 끊지 말라고 하거나 가족에게 알리지 말라고 한다",
                "저금리 대출을 위해 기존 대출을 먼저 갚으라고 한다",
                "자녀·가족이라며 폰이 고장 났으니 돈이나 상품권을 보내 달라고 한다",
                "인증번호나 OTP, 비밀번호를 알려 달라고 한다"};
        callResult = new LinearLayout(this);
        callResult.setOrientation(LinearLayout.VERTICAL);
        final List<CheckBox> boxes = new ArrayList<>();
        for (String s : items) {
            CheckBox c = new CheckBox(this);
            c.setText(s);
            c.setTextSize(18);
            c.setTextColor(Ui.INK);
            c.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), 0, Ui.dp(this, 10));
            c.setOnCheckedChangeListener((v, on) -> updateCall(boxes));
            boxes.add(c);
            body.addView(c);
        }
        body.addView(callResult, lp(8));
    }

    private void updateCall(List<CheckBox> boxes) {
        callResult.removeAllViews();
        int n = 0;
        for (CheckBox c : boxes) if (c.isChecked()) n++;
        if (n == 0) return;
        TextView t = Ui.text(this, "⚠ 보이스피싱일 가능성이 매우 큽니다.\n지금 바로 전화를 끊으세요.", 20, Color.WHITE, true);
        t.setBackgroundColor(Ui.RED);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        callResult.addView(t);
        callResult.addView(Ui.text(this, "• 돈을 보냈거나 앱을 설치했다면 즉시 112 또는 금융감독원 1332로 연락해 지급 정지를 요청하세요.\n"
                + "• 은행·카드사 공식 번호로 직접 걸어서 확인하세요. (문자나 통화로 받은 번호는 쓰지 마세요)\n"
                + "• 수사기관은 전화로 돈이나 계좌 이체를 요구하지 않습니다.", 15, Ui.INK, false), lp(8));
        Button call = Ui.button(this, "112 전화하기", Ui.RED, Color.WHITE);
        call.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));
        callResult.addView(call, lp(8));
        Button fss = Ui.button(this, "금융감독원 1332 전화하기", Color.WHITE, Ui.INK);
        fss.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:1332"))));
        callResult.addView(fss, lp(4));
    }

    // ------------------------------------------------------------ 2. 의심 문자·링크
    private Button callCheckBtn;

    /** 통화가 끝나면 연락처에 없는 번호만 검색 알림으로 알려 준다(전체 기능판). */
    private void buildCallCheck() {
        section("📲 통화 후 번호 확인 알림");
        body.addView(Ui.text(this, "모르는 번호(연락처에 없는 번호)로 전화가 걸려 와서 끝나면, 그 번호를 검색해 볼 수 있는 알림을 띄웁니다. 번호는 폰 밖으로 보내지 않고, 알림을 눌렀을 때만 검색창이 열립니다.", 14, Ui.GRAY, false));
        callCheckBtn = Ui.button(this, "", Ui.GREEN, Color.WHITE);
        callCheckBtn.setOnClickListener(v -> {
            if (CallCheck.on(this)) {
                CallCheck.setOn(this, false);
                updateCallCheck();
            } else if (CallCheck.granted(this)) {
                CallCheck.setOn(this, true);
                updateCallCheck();
            } else {
                requestPermissions(CallCheck.perms(), 71);
            }
        });
        body.addView(callCheckBtn, lp(8));
        updateCallCheck();
    }

    private void updateCallCheck() {
        boolean on = CallCheck.on(this) && CallCheck.granted(this);
        callCheckBtn.setText(on ? "통화 후 번호 확인: 켜짐 (끄기)" : "통화 후 번호 확인 켜기");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 71) {
            if (CallCheck.granted(this)) CallCheck.setOn(this, true);
            else Ui.toast(this, "통화 기록·연락처·알림 권한을 모두 허용해야 켤 수 있습니다.");
            updateCallCheck();
        }
    }

    private void buildText() {
        section("💬 의심 문자·링크 점검");
        body.addView(Ui.text(this, "받은 문자를 아래에 붙여 넣거나, 문자 앱에서 [공유]로 이 앱에 보내세요. 문자 내용은 폰 밖으로 나가지 않습니다. 링크는 누르지 마세요.", 14, Ui.GRAY, false));
        input = new EditText(this);
        input.setHint("여기에 문자 내용을 붙여 넣으세요");
        input.setMinLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setTextSize(17);
        input.setBackground(Ui.round(Color.WHITE, 10, this));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        body.addView(input, lp(8));
        Button go = Ui.button(this, "확인하기", Ui.CORAL, Color.WHITE);
        go.setOnClickListener(v -> runText());
        body.addView(go, lp(8));
        textResult = new LinearLayout(this);
        textResult.setOrientation(LinearLayout.VERTICAL);
        body.addView(textResult, lp(8));
    }

    private void runText() {
        textResult.removeAllViews();
        String s = input.getText().toString();
        if (s.trim().isEmpty()) {
            Ui.toast(this, "점검할 문자 내용을 붙여 넣어 주세요.");
            return;
        }
        ScamText.Result r = ScamText.analyze(s);
        int lv = r.level();
        String head = lv == 2 ? "🔴 사기 문자로 의심됩니다" : (lv == 1 ? "🟠 주의가 필요합니다" : "🟢 뚜렷한 징후는 없습니다");
        TextView t = Ui.text(this, head, 18, Color.WHITE, true);
        t.setBackgroundColor(lv == 2 ? Ui.RED : (lv == 1 ? Ui.AMBER : Ui.GREEN));
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        textResult.addView(t);
        StringBuilder sb = new StringBuilder();
        for (String x : r.reasons) sb.append("• ").append(x).append('\n');
        if (!r.links.isEmpty()) {
            sb.append("\n문자 속 링크\n");
            for (String l : r.links) sb.append("  ").append(l).append('\n');
        }
        if (lv >= 1) {
            sb.append("\n링크를 누르거나 앱을 설치하지 마세요. 은행·택배사는 공식 앱이나 공식 번호로 직접 확인하세요.\n"
                    + "이미 눌렀거나 설치했다면 [내 폰 위험 징후 점검]을 해 보시고, 118(불법스팸대응센터)이나 112에 신고하세요.");
        } else {
            sb.append("\n※ 징후가 없어도 안전하다는 뜻은 아닙니다. 모르는 번호의 링크는 누르지 마세요.");
        }
        textResult.addView(Ui.text(this, sb.toString(), 15, Ui.INK, false), lp(8));
    }

    // ------------------------------------------------------------ 3. 내 폰 위험 징후
    private void buildPhone() {
        section("🔍 내 폰 위험 징후 점검");
        body.addView(Ui.text(this, "보이스피싱 앱이 흔히 쓰는 수법(원격 제어 앱, 접근성·기기 관리자·화면 덮기 권한, 출처 불명 설치)을 찾습니다. 결과는 폰 밖으로 나가지 않습니다.", 14, Ui.GRAY, false));
        phoneBtn = Ui.button(this, "지금 확인하기", Ui.CORAL, Color.WHITE);
        phoneBtn.setOnClickListener(v -> runPhone());
        body.addView(phoneBtn, lp(8));
        phoneResult = new LinearLayout(this);
        phoneResult.setOrientation(LinearLayout.VERTICAL);
        body.addView(phoneResult, lp(8));
    }

    private static final class Finding {
        AppEntry e;
        int sev;
        final List<String> why = new ArrayList<>();
    }

    private boolean isRemote(String pkg) {
        String p = pkg.toLowerCase();
        for (String r : REMOTE) if (p.startsWith(r)) return true;
        return false;
    }

    private void runPhone() {
        phoneBtn.setEnabled(false);
        phoneResult.removeAllViews();
        phoneResult.addView(Ui.text(this, "점검하는 중입니다...", 15, Ui.GRAY, false));
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            List<Finding> out = new ArrayList<>();
            try {
                List<AppEntry> list = Scanner.scan(app);
                String unknown = Tr.t("알 수 없음 (파일·링크로 직접 설치)");
                String file = Tr.t("파일 설치 (브라우저·파일앱 등에서 직접 설치)");
                for (AppEntry e : list) {
                    if (e.preloaded && !e.accEnabled && !e.adminActive) continue;
                    Finding f = new Finding();
                    f.e = e;
                    boolean side = e.installerText != null && (e.installerText.startsWith(unknown) || e.installerText.startsWith(file));
                    if (isRemote(e.pkg)) {
                        f.sev = 2;
                        f.why.add("원격 제어 앱입니다. 본인이 직접 설치한 것이 아니라면 바로 삭제하세요. 통화 중 설치하라고 하면 사기입니다.");
                    }
                    if (e.accEnabled) {
                        f.sev = Math.max(f.sev, side ? 2 : 1);
                        f.why.add("접근성 서비스가 켜져 있습니다. 화면을 읽고 대신 누를 수 있는 권한입니다." + (side ? " 출처 불명 설치라 위험합니다." : ""));
                    }
                    if (e.adminActive && !e.trusted) {
                        f.sev = Math.max(f.sev, side ? 2 : 1);
                        f.why.add("기기 관리자 권한이 켜져 있어 삭제가 막힐 수 있습니다.");
                    }
                    if (e.overlay && side) {
                        f.sev = Math.max(f.sev, 1);
                        f.why.add("다른 앱 위에 화면을 덮을 수 있습니다. (가짜 은행 화면에 쓰입니다)");
                    }
                    if (e.installAllowed && side) {
                        f.sev = Math.max(f.sev, 1);
                        f.why.add("다른 앱을 설치할 수 있는 권한이 있습니다.");
                    }
                    if (f.sev == 0 && side && !e.trusted) {
                        f.sev = 1;
                        f.why.add("스토어가 아닌 곳(링크·파일)에서 설치된 앱입니다.");
                    }
                    if (f.sev > 0) out.add(f);
                }
            } catch (Exception ignored) {
            }
            Collections.sort(out, new Comparator<Finding>() {
                @Override
                public int compare(Finding a, Finding b) {
                    return b.sev - a.sev;
                }
            });
            final List<Finding> res = out;
            runOnUiThread(() -> showPhone(res));
        }).start();
    }

    private void showPhone(List<Finding> res) {
        phoneBtn.setEnabled(true);
        phoneResult.removeAllViews();
        if (res.isEmpty()) {
            TextView t = Ui.text(this, "🟢 뚜렷한 위험 징후가 발견되지 않았습니다.", 17, Color.WHITE, true);
            t.setBackgroundColor(Ui.GREEN);
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            phoneResult.addView(t);
            phoneResult.addView(Ui.text(this, "※ 100% 안전하다는 뜻은 아닙니다. 모르는 앱은 설치하지 마세요.", 14, Ui.GRAY, false), lp(6));
            return;
        }
        phoneResult.addView(Ui.text(this, "확인이 필요한 앱 " + res.size() + "개", 17, Ui.INK, true));
        for (final Finding f : res) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.round(Color.WHITE, 10, this));
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            card.addView(Ui.text(this, (f.sev == 2 ? "🔴 " : "🟠 ") + f.e.label, 16, Ui.INK, true));
            StringBuilder sb = new StringBuilder();
            for (String w : f.why) sb.append("• ").append(w).append('\n');
            card.addView(Ui.text(this, sb.toString().trim(), 14, Ui.INK, false));
            Button del = Ui.button(this, "지우기", Color.WHITE, Ui.RED);
            del.setTextSize(17);
            del.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + f.e.pkg))));
            card.addView(del, lp(6));
            phoneResult.addView(card, lp(8));
        }
        phoneResult.addView(Ui.text(this, "내가 쓰는 앱이면 그대로 두세요. 모르는 앱이거나 통화·문자 때문에 설치한 앱이면 삭제하세요. 삭제가 막히면 설정에서 기기 관리자·접근성을 먼저 끄세요.", 14, Ui.GRAY, false), lp(10));
    }
}
