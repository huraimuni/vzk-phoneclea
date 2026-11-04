package kr.todanjeon.phoneclean;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 자녀(구독자): 연결된 부모님 폰에서 올라온 위험 앱 설치 기록. */
public class ReportActivity extends BaseActivity {
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Auth.uid() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, Tr.t("부모님 폰 확인 결과"), getString(R.string.version_line, BuildConfig.VERSION_NAME)));
        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 20));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Report.registerToken();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        box.removeAllViews();
        box.addView(Ui.text(this, Tr.t("연결된 부모님 폰에 위험이 의심되는 앱이 설치되면 여기에 기록되고 알림이 옵니다."), 14, Ui.GRAY, false));
        FirebaseFirestore.getInstance().collection("alerts").whereArrayContains("childUids", Auth.uid()).limit(50).get()
                .addOnSuccessListener(snap -> {
                    List<DocumentSnapshot> l = new ArrayList<>(snap.getDocuments());
                    Collections.sort(l, (x, y) -> {
                        Timestamp a = x.getTimestamp("ts"), c = y.getTimestamp("ts");
                        long av = a == null ? Long.MAX_VALUE : a.toDate().getTime();
                        long cv = c == null ? Long.MAX_VALUE : c.toDate().getTime();
                        return Long.compare(cv, av);
                    });
                    if (l.isEmpty()) {
                        box.addView(Ui.text(this, Tr.t("아직 기록이 없습니다. 좋은 소식이에요."), 16, Ui.GREEN, true));
                        return;
                    }
                    SimpleDateFormat f = new SimpleDateFormat("M/d HH:mm", Locale.getDefault());
                    for (DocumentSnapshot d : l) {
                        Timestamp t = d.getTimestamp("ts");
                        String when = t == null ? "" : f.format(new Date(t.toDate().getTime()));
                        String name = d.getString("parentName") == null ? "" : d.getString("parentName");
                        TextView h = Ui.text(this, "⚠ " + name + " · " + d.getString("label") + "   " + when, 16, Ui.INK, true);
                        h.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 2));
                        box.addView(h);
                        Object rs = d.get("reasons");
                        if (rs instanceof List) {
                            for (Object r : (List<?>) rs) box.addView(Ui.text(this, "- " + r, 13, Ui.GRAY, false));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    box.addView(Ui.text(this, Tr.t("점검 리포트는 구독자 전용입니다."), 16, Ui.AMBER, true));
                    Button sub = Ui.button(this, getString(R.string.btn_subscription), Ui.CORAL, Color.WHITE);
                    sub.setOnClickListener(v -> startActivity(new Intent(this, SubscribeActivity.class)));
                    box.addView(sub);
                });
    }
}
