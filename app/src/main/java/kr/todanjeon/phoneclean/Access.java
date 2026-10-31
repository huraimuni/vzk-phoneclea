package kr.todanjeon.phoneclean;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;

/** 화면 도움 요청 권한(구독 또는 운영자 무료 지정) 확인. 최종 판단은 서버 규칙이 한다. */
final class Access {
    static final String PRODUCT_ID = "family_help_monthly";

    interface Result {
        void done(boolean ok, String via);
    }

    private Access() {
    }

    static void check(final Result r) {
        final String me = Auth.uid();
        if (me == null) {
            r.done(false, null);
            return;
        }
        if (Admin.is) {
            r.done(true, "grant");   // 운영자는 구독 없이 화면 도움을 쓸 수 있다(서버 규칙도 동일)
            return;
        }
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("grants").document(me).get().addOnCompleteListener(g -> {
            if (g.isSuccessful() && valid(g.getResult(), true)) {
                r.done(true, "grant");
                return;
            }
            db.collection("entitlements").document(me).get().addOnCompleteListener(e -> {
                if (e.isSuccessful() && valid(e.getResult(), false)) r.done(true, "sub");
                else r.done(false, null);
            });
        });
    }

    /** 운영자 무료 지정(grants)은 만료일이 없으면 계속 유효, 구독(entitlements)은 만료일이 미래여야 유효. */
    private static boolean valid(DocumentSnapshot d, boolean grant) {
        if (d == null || !d.exists()) return false;
        Timestamp exp = d.getTimestamp("expiresAt");
        if (exp == null) return grant;
        return exp.toDate().after(new Date());
    }
}
