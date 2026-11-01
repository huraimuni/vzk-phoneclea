package kr.todanjeon.phoneclean;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 관리자 전용 화면(한국어만). 사용자 수·연결·세션 현황, 무료 지정(grants) 관리. 서버 규칙이 관리자만 허용한다. */
public class AdminActivity extends BaseActivity {
    private FirebaseFirestore db;
    private LinearLayout body;
    private TextView stats;
    private final List<DocumentSnapshot> users = new ArrayList<>();
    private final Map<String, DocumentSnapshot> grantMap = new HashMap<>();
    private final Map<String, DocumentSnapshot> subMap = new HashMap<>();
    private final Map<String, String> noteMap = new HashMap<>();
    private final List<String> adminUids = new ArrayList<>();
    private LinearLayout userList;
    private String query = "";
    private int mode = 0; // 0 전체, 1 무료 지정, 2 구독, 3 둘 다 아님
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

    private boolean grantValid(DocumentSnapshot g) {
        if (g == null || !g.exists()) return false;
        Timestamp e = g.getTimestamp("expiresAt");
        return e == null || e.toDate().after(new Date());
    }

    private boolean subValid(DocumentSnapshot g) {
        if (g == null || !g.exists()) return false;
        Timestamp e = g.getTimestamp("expiresAt");
        return e != null && e.toDate().after(new Date());
    }

    private String nameOf(String uid) {
        for (DocumentSnapshot d : users) if (d.getId().equals(uid)) return nz(d.getString("name"));
        return uid.substring(0, Math.min(8, uid.length()));
    }

    private void sectionUsers() {
        body.addView(head("사용자 (눌러서 무료 지정·메모)"));
        final EditText search = new EditText(this);
        search.setHint("이름·ID·메모로 찾기");
        search.setSingleLine(true);
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { }
            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.ROOT);
                renderUsers();
            }
        });
        body.addView(search);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"전체", "🎁 무료", "⭐ 구독", "해당 없음"};
        for (int i = 0; i < names.length; i++) {
            final int m = i;
            Button bt = Ui.button(this, names[i], mode == i ? Ui.NAVY : Color.WHITE, mode == i ? Color.WHITE : Ui.INK);
            bt.setTextSize(13);
            bt.setOnClickListener(v -> {
                mode = m;
                load();
            });
            bar.addView(bt, Ui.weight(this, 2));
        }
        body.addView(bar);
        userList = new LinearLayout(this);
        userList.setOrientation(LinearLayout.VERTICAL);
        body.addView(userList);
        userList.addView(row("불러오는 중..."));
        users.clear();
        grantMap.clear();
        subMap.clear();
        noteMap.clear();
        adminUids.clear();
        final int[] left = {5};
        final Runnable done = () -> {
            if (--left[0] == 0) {
                renderUsers();
                renderGrants();
            }
        };
        db.collection("users").orderBy("updatedAt", Query.Direction.DESCENDING).limit(500).get().addOnSuccessListener(q -> {
            users.addAll(q.getDocuments());
            done.run();
        }).addOnFailureListener(e -> {
            userList.removeAllViews();
            userList.addView(row("불러오지 못했습니다: " + Auth.msg(e)));
        });
        db.collection("grants").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot d : q.getDocuments()) grantMap.put(d.getId(), d);
            done.run();
        }).addOnFailureListener(e -> done.run());
        db.collection("entitlements").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot d : q.getDocuments()) subMap.put(d.getId(), d);
            done.run();
        }).addOnFailureListener(e -> done.run());
        db.collection("adminNotes").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot d : q.getDocuments()) noteMap.put(d.getId(), nz2(d.getString("text")));
            done.run();
        }).addOnFailureListener(e -> done.run());
        db.collection("config").document("admin").get().addOnSuccessListener(d -> {
            Object o = d.get("uids");
            if (o instanceof List) for (Object x : (List<?>) o) adminUids.add(String.valueOf(x));
            done.run();
        }).addOnFailureListener(e -> done.run());
    }

    private static String nz2(String s) {
        return s == null ? "" : s;
    }

    private String badge(String uid) {
        StringBuilder sb = new StringBuilder();
        if (adminUids.contains(uid)) sb.append("👑 ");
        DocumentSnapshot g = grantMap.get(uid);
        if (grantValid(g)) {
            Timestamp e = g.getTimestamp("expiresAt");
            sb.append("🎁무료").append(e == null ? "(무기한) " : "(~" + fmt.format(e.toDate()) + ") ");
        }
        DocumentSnapshot sdoc = subMap.get(uid);
        if (subValid(sdoc)) sb.append("⭐구독(~").append(fmt.format(sdoc.getTimestamp("expiresAt").toDate())).append(") ");
        return sb.toString();
    }

    private void renderUsers() {
        if (userList == null) return;
        userList.removeAllViews();
        int n = 0;
        for (final DocumentSnapshot d : users) {
            final String uid = d.getId();
            boolean free = grantValid(grantMap.get(uid)), sub = subValid(subMap.get(uid));
            if (mode == 1 && !free) continue;
            if (mode == 2 && !sub) continue;
            if (mode == 3 && (free || sub)) continue;
            String note = noteMap.containsKey(uid) ? noteMap.get(uid) : "";
            String hay = (nz(d.getString("name")) + " " + nz2(d.getString("email")) + " " + uid + " " + note).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !hay.contains(query)) continue;
            n++;
            if (n > 150) break;
            final String nm = d.getString("name");
            String b = badge(uid);
            TextView r = row((b.isEmpty() ? "" : b + "\n") + nz(nm) + " · " + d.getString("provider") + " · " + when(d, "updatedAt")
                    + (note.isEmpty() ? "" : "\n📝 " + note) + "\nID " + uid.substring(0, Math.min(10, uid.length())));
            r.setOnClickListener(v -> userMenu(uid, nm));
            userList.addView(r);
        }
        if (n == 0) userList.addView(row("해당하는 사용자가 없습니다."));
    }

    private LinearLayout grantBox;

    private void sectionGrants() {
        body.addView(head("무료 지정 목록"));
        grantBox = new LinearLayout(this);
        grantBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(grantBox);
    }

    private void renderGrants() {
        if (grantBox == null) return;
        grantBox.removeAllViews();
        if (grantMap.isEmpty()) grantBox.addView(row("없음"));
        for (final DocumentSnapshot d : grantMap.values()) {
            Timestamp e = d.getTimestamp("expiresAt");
            final String uid = d.getId();
            TextView r = row(nameOf(uid) + " · " + (e == null ? "만료 없음" : "~" + fmt.format(e.toDate())) + "  (눌러서 해제)");
            r.setOnClickListener(v -> revoke(uid));
            grantBox.addView(r);
        }
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
                .setItems(new String[]{"무료 지정 30일", "무료 지정 1년", "무료 지정 (만료 없음)", "무료 해제", "메모 쓰기 (누구인지 표시)", "전체 ID 복사", "구독 상태 보기"}, (d, w) -> {
                    if (w == 0) grant(uid, 30);
                    else if (w == 1) grant(uid, 365);
                    else if (w == 2) grant(uid, 0);
                    else if (w == 3) revoke(uid);
                    else if (w == 4) editNote(uid);
                    else if (w == 5) {
                        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("uid", uid));
                        Ui.toast(this, "ID를 복사했습니다.");
                    } else showSub(uid);
                }).show();
    }

    private void editNote(final String uid) {
        final EditText et = new EditText(this);
        et.setText(noteMap.containsKey(uid) ? noteMap.get(uid) : "");
        et.setHint("예: 김OO 사장님 지인, 이벤트 당첨");
        new AlertDialog.Builder(this).setTitle("메모 (" + nameOf(uid) + ")").setView(et)
                .setPositiveButton("저장", (d, w) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("text", et.getText().toString().trim());
                    m.put("ts", Timestamp.now());
                    db.collection("adminNotes").document(uid).set(m)
                            .addOnSuccessListener(x -> load())
                            .addOnFailureListener(e -> Ui.toast(this, "실패: " + Auth.msg(e)));
                })
                .setNegativeButton(Tr.t("취소"), null).show();
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
