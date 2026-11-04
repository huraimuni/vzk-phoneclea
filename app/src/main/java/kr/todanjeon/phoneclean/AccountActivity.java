package kr.todanjeon.phoneclean;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/** 내 계정: 로그인 방식·이름·이메일·계정 ID 확인, 로그아웃, 계정 삭제. */
public class AccountActivity extends BaseActivity {
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, Tr.t("내 계정"), Tr.t("로그인 정보 확인 · 로그아웃 · 계정 삭제")));
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private String provider(FirebaseUser u) {
        String id = u.getUid();
        if (id.startsWith("kakao:")) return Tr.t("카카오");
        if (id.startsWith("naver:")) return Tr.t("네이버");
        for (UserInfo i : u.getProviderData()) if ("google.com".equals(i.getProviderId())) return Tr.t("구글");
        return Tr.t("기타");
    }

    private void row(String label, String value) {
        body.addView(Ui.text(this, label, 12, Ui.GRAY, false));
        TextView t = Ui.text(this, value, 17, Ui.INK, true);
        t.setPadding(0, 0, 0, Ui.dp(this, 12));
        t.setTextIsSelectable(true);
        body.addView(t);
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 10);
        return p;
    }

    private void render() {
        body.removeAllViews();
        final FirebaseUser u = Auth.user();
        if (u == null) {
            body.addView(Ui.text(this, Tr.t("로그인하지 않았습니다."), 16, Ui.INK, true));
            android.widget.Button go = Ui.button(this, Tr.t("로그인"), Ui.CORAL, Color.WHITE);
            go.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            body.addView(go, lp());
            return;
        }
        row(Tr.t("로그인 방식"), provider(u));
        row(Tr.t("이름"), Auth.myName(this));
        String mail = u.getEmail();
        if (mail != null && !mail.isEmpty()) row(Tr.t("이메일"), mail);
        row(Tr.t("계정 ID"), u.getUid());
        if (Admin.is) row(Tr.t("권한"), "👑 " + Tr.t("관리자"));
        body.addView(Ui.text(this, Tr.t("계정 ID는 문의하실 때 알려 주시면 확인이 빠릅니다."), 12, Ui.GRAY, false));

        android.widget.Button out = Ui.button(this, getString(R.string.btn_signout), Color.WHITE, Ui.INK);
        out.setOnClickListener(v -> {
            Auth.signOut(this);
            finish();
        });
        LinearLayout.LayoutParams l = lp();
        l.topMargin = Ui.dp(this, 24);
        body.addView(out, l);
        android.widget.Button del = Ui.button(this, Tr.t("계정 삭제"), Color.WHITE, Ui.RED);
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(Tr.t("계정을 삭제할까요?"))
                .setMessage(Tr.t("내 계정 정보와 가족 연결이 삭제되며 되돌릴 수 없습니다."))
                .setNegativeButton(Tr.t("취소"), null)
                .setPositiveButton(Tr.t("삭제"), (d, w) -> deleteAccount(u))
                .show());
        body.addView(del, lp());
    }

    private void deleteAccount(final FirebaseUser u) {
        final String uid = u.getUid();
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        // 부모로서 남긴 점검 리포트 기록도 함께 지운다
        db.collection("alerts").whereEqualTo("parentUid", uid).get().addOnSuccessListener(qa -> {
            for (QueryDocumentSnapshot d : qa) d.getReference().delete();
        });
        db.collection("links").whereEqualTo("parentUid", uid).get().addOnCompleteListener(t1 -> {
            if (t1.isSuccessful()) for (QueryDocumentSnapshot d : t1.getResult()) d.getReference().delete();
            db.collection("links").whereEqualTo("childUid", uid).get().addOnCompleteListener(t2 -> {
                if (t2.isSuccessful()) for (QueryDocumentSnapshot d : t2.getResult()) d.getReference().delete();
                db.collection("users").document(uid).delete().addOnCompleteListener(t3 -> u.delete().addOnCompleteListener(t4 -> {
                    if (t4.isSuccessful()) {
                        Auth.signOut(this);
                        Ui.toast(this, Tr.t("계정을 삭제했습니다."));
                        finish();
                    } else {
                        Ui.toast(this, Tr.t("삭제하지 못했습니다. 로그아웃 후 다시 로그인한 뒤 시도해 주세요."));
                    }
                }));
            });
        });
    }
}
