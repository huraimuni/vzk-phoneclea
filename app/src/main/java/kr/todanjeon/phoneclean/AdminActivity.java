package kr.todanjeon.phoneclean;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 관리자 전용 화면(한국어만). 사용자 수·연결·세션 현황, 무료 지정(grants) 관리. 서버 규칙이 관리자만 허용한다. */
public class AdminActivity extends BaseActivity {
    private FirebaseFirestore db;
    private LinearLayout body;
    private TextView stats;
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM.dd HH:mm", Locale.KOREA);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = FirebaseFirestore.getInstance();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.fit(root);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "관리자", "사용자 · 연결 · 화면 공유 · 무료 지정"));
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        stats = Ui.text(this, "불러오는 중...", 15, Ui.INK, true);
        body.addView(stats);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        count("users", null, null, "사용자");
        db.collection("links").whereEqualTo("status", "active").count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> add("활성 가족 연결", s.getCount()));
        db.collection("sessions").count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> add("화면 공유 기록", s.getCount()));
        db.collection("grants").count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> add("무료 지정", s.getCount()));
        db.collection("entitlements").count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> add("구독 기록", s.getCount()));
        // 목록은 통계 아래에 새로 그린다
        while (body.getChildCount() > 1) body.removeViewAt(1);
        stats.setText("");
        sectionUsers();
        sectionGrants();
        sectionSessions();
    }

    private void count(String col, String f, Object v, final String label) {
        db.collection(col).count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> add(label, s.getCount()))
                .addOnFailureListener(e -> stats.setText("권한이 없습니다: " + Auth.msg(e)));
    }

    private void add(String label, long n) {
        stats.setText(stats.getText() + label + " " + n + "   ");
    }

    private TextView head(String s) {
        TextView t = Ui.text(this, s, 17, Ui.CORAL_DARK, true);
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
        return t;
    }

    private TextView row(String s) {
        TextView t = Ui.text(this, s, 14, Ui.INK, false);
        t.setBackground(Ui.round(android.graphics.Color.WHITE, 10, this));
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        t.setLayoutParams(lp);
        return t;
    }

    private String when(DocumentSnapshot d, String f) {
        Timestamp t = d.getTimestamp(f);
        return t == null ? "-" : fmt.format(t.toDate());
    }

    private void sectionUsers() {
        body.addView(head("사용자 (최근 100명 · 눌러서 관리)"));
        db.collection("users").orderBy("updatedAt", Query.Direction.DESCENDING).limit(100).get().addOnSuccessListener(q -> {
            for (final DocumentSnapshot d : q.getDocuments()) {
                String nm = d.getString("name");
                TextView r = row((nm == null ? "?" : nm) + " · " + d.getString("provider") + " · " + d.getString("lang")
                        + " · " + when(d, "updatedAt") + "\n" + d.getId().substring(0, Math.min(8, d.getId().length())));
                r.setOnClickListener(v -> userMenu(d.getId(), nm));
                body.addView(r);
            }
        }).addOnFailureListener(e -> body.addView(row("불러오지 못했습니다: " + Auth.msg(e))));
    }

    private void sectionGrants() {
        body.addView(head("무료 지정 목록"));
        db.collection("grants").get().addOnSuccessListener(q -> {
            if (q.isEmpty()) body.addView(row("없음"));
            for (final DocumentSnapshot d : q.getDocuments()) {
                Timestamp e = d.getTimestamp("expiresAt");
                final String uid = d.getId();
                TextView r = row(uid.substring(0, Math.min(8, uid.length())) + " · " + (e == null ? "만료 없음" : "~" + fmt.format(e.toDate())) + "  (눌러서 해제)");
                r.setOnClickListener(v -> revoke(uid));
                body.addView(r);
            }
        });
    }

    private void sectionSessions() {
        body.addView(head("최근 화면 공유 20건"));
        db.collection("sessions").orderBy("createdAt", Query.Direction.DESCENDING).limit(20).get().addOnSuccessListener(q -> {
            if (q.isEmpty()) body.addView(row("없음"));
            for (DocumentSnapshot d : q.getDocuments()) {
                body.addView(row(nz(d.getString("childName")) + " → " + nz(d.getString("parentName")) + " · " + d.getString("status") + " · " + when(d, "createdAt")));
            }
        });
    }

    private static String nz(String s) {
        return s == null ? "?" : s;
    }

    private void userMenu(final String uid, String name) {
        new AlertDialog.Builder(this).setTitle(nz(name))
                .setItems(new String[]{"무료 지정 30일", "무료 지정 1년", "무료 지정 (만료 없음)", "무료 해제", "구독 상태 보기"}, (d, w) -> {
                    if (w == 0) grant(uid, 30);
                    else if (w == 1) grant(uid, 365);
                    else if (w == 2) grant(uid, 0);
                    else if (w == 3) revoke(uid);
                    else showSub(uid);
                }).show();
    }

    private void grant(String uid, int days) {
        Map<String, Object> m = new HashMap<>();
        m.put("by", Auth.uid());
        m.put("createdAt", Timestamp.now());
        if (days > 0) m.put("expiresAt", new Timestamp(new Date(System.currentTimeMillis() + days * 86400_000L)));
        db.collection("grants").document(uid).set(m)
                .addOnSuccessListener(x -> {
                    Ui.toast(this, "무료로 지정했습니다.");
                    load();
                })
                .addOnFailureListener(e -> Ui.toast(this, "실패: " + Auth.msg(e)));
    }

    private void revoke(final String uid) {
        new AlertDialog.Builder(this).setTitle("무료 지정을 해제할까요?")
                .setPositiveButton("해제", (d, w) -> db.collection("grants").document(uid).delete()
                        .addOnSuccessListener(x -> {
                            Ui.toast(this, "해제했습니다.");
                            load();
                        })
                        .addOnFailureListener(e -> Ui.toast(this, "실패: " + Auth.msg(e))))
                .setNegativeButton(Tr.t("취소"), null).show();
    }

    private void showSub(String uid) {
        db.collection("entitlements").document(uid).get().addOnSuccessListener(d -> {
            String s = d.exists() ? "구독 있음\n만료: " + when(d, "expiresAt") : "구독 기록 없음";
            new AlertDialog.Builder(this).setTitle("구독 상태").setMessage(s).setPositiveButton(Tr.t("확인"), null).show();
        }).addOnFailureListener(e -> Ui.toast(this, "실패: " + Auth.msg(e)));
    }
}
