package kr.todanjeon.phoneclean;

import android.content.Context;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 점검 리포트(부모 폰 → 자녀): 위험한 새 앱이 깔리면 연결된 자녀(구독자)에게 알린다. 부모가 동의해야 켜진다. */
final class Report {
    static final String PREFS = "phoneclean";
    static final String KEY_ON = "report_on";

    private Report() {
    }

    static boolean on(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false);
    }

    static void setOn(Context c, boolean v) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, v).apply();
        String me = Auth.uid();
        if (me == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("reportOn", v);
        FirebaseFirestore.getInstance().collection("users").document(me)
                .set(m, com.google.firebase.firestore.SetOptions.merge());
    }

    /** 위험 의심(또는 접근성·기기관리자 권한을 가진 주의 앱)만 보낸다. 사진·메시지 등은 보내지 않고 앱 이름과 이유만 보낸다. */
    static void send(Context c, AppEntry e) {
        try {
            if (!on(c)) return;
            boolean risky = e.level() == AppEntry.LEVEL_HIGH
                    || (e.level() == AppEntry.LEVEL_WARN && (e.accEnabled || e.adminActive));
            if (!risky) return;
            final String me = Auth.uid();
            if (me == null) return;
            final String parentName = Auth.myName(c);
            final String label = e.label == null ? e.pkg : e.label;
            final String pkg = e.pkg;
            final long level = e.level();
            final List<String> reasons = new ArrayList<>();
            for (int i = 0; i < e.reasons.size() && i < 3; i++) reasons.add(e.reasons.get(i));
            final FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("links").whereEqualTo("parentUid", me).whereEqualTo("status", "active").get()
                    .addOnSuccessListener(snap -> {
                        List<String> kids = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                            String k = d.getString("childUid");
                            if (k != null && kids.size() < 10) kids.add(k);
                        }
                        if (kids.isEmpty()) return;
                        Map<String, Object> m = new HashMap<>();
                        m.put("parentUid", me);
                        m.put("parentName", parentName);
                        m.put("childUids", kids);
                        m.put("label", label);
                        m.put("pkg", pkg);
                        m.put("level", level);
                        m.put("reasons", reasons);
                        m.put("ts", FieldValue.serverTimestamp());
                        db.collection("alerts").add(m);
                    });
        } catch (Throwable ignored) {
        }
    }

    /** 자녀 폰: 푸시를 받을 수 있도록 이 기기의 토큰을 내 계정에 등록한다. */
    static void registerToken() {
        try {
            final String me = Auth.uid();
            if (me == null) return;
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> saveToken(me, t));
        } catch (Throwable ignored) {
        }
    }

    static void saveToken(String uid, String token) {
        if (uid == null || token == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("fcmTokens", FieldValue.arrayUnion(token));
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(m, com.google.firebase.firestore.SetOptions.merge());
    }
}
