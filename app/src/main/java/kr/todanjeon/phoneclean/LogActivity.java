package kr.todanjeon.phoneclean;

import android.app.AlertDialog;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 화면 공유 기록(누가·언제·얼마나). 화면 내용은 저장하지 않는다. 부모는 지울 수 있고 90일 뒤 자동 삭제된다. */
public class LogActivity extends BaseActivity {
    private LinearLayout box;
    private final List<DocumentSnapshot> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Auth.uid() == null) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, getString(R.string.log_title), getString(R.string.log_note)));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 20));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        load();
    }

    private void load() {
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        rows.clear();
        db.collection("sessions").whereEqualTo("parentUid", Auth.uid()).get().addOnSuccessListener(a -> {
            rows.addAll(a.getDocuments());
            db.collection("sessions").whereEqualTo("childUid", Auth.uid()).get().addOnSuccessListener(c -> {
                for (DocumentSnapshot d : c.getDocuments()) {
                    boolean dup = false;
                    for (DocumentSnapshot r : rows) if (r.getId().equals(d.getId())) dup = true;
                    if (!dup) rows.add(d);
                }
                Collections.sort(rows, new Comparator<DocumentSnapshot>() {
                    @Override
                    public int compare(DocumentSnapshot x, DocumentSnapshot y) {
                        return Long.compare(ms(y.getTimestamp("createdAt")), ms(x.getTimestamp("createdAt")));
                    }
                });
                render();
            });
        });
    }

    private static long ms(Timestamp t) {
        return t == null ? 0 : t.toDate().getTime();
    }

    private void render() {
        box.removeAllViews();
        if (rows.isEmpty()) {
            box.addView(Ui.text(this, getString(R.string.log_empty), 15, Ui.GRAY, false));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault());
        boolean anyMine = false;
        for (final DocumentSnapshot d : rows) {
            Timestamp st = d.getTimestamp("startedAt"), en = d.getTimestamp("endedAt");
            String dur = st == null ? getString(R.string.log_requested_only)
                    : getString(R.string.log_min, (int) Math.max(1, ((en == null ? System.currentTimeMillis() : en.toDate().getTime()) - st.toDate().getTime()) / 60000));
            String when = f.format(new Date(ms(st != null ? st : d.getTimestamp("createdAt"))));
            TextView t = Ui.text(this, getString(R.string.log_row, nz(d.getString("childName")), nz(d.getString("parentName")), when, dur), 14, Ui.INK, false);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
            box.addView(t);
            if (Auth.uid().equals(d.getString("parentUid"))) {
                anyMine = true;
                Button del = Ui.button(this, getString(R.string.btn_delete), Color.WHITE, Ui.RED);
                del.setTextSize(12);
                del.setOnClickListener(v -> d.getReference().delete().addOnSuccessListener(x -> load()));
                box.addView(del, new LinearLayout.LayoutParams(-2, -2));
            }
        }
        if (anyMine) {
            Button all = Ui.button(this, getString(R.string.btn_delete_all), Ui.RED, Color.WHITE);
            all.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage(R.string.btn_delete_all)
                    .setPositiveButton(R.string.btn_ok, (x, w) -> {
                        for (DocumentSnapshot d : rows) if (Auth.uid().equals(d.getString("parentUid"))) d.getReference().delete();
                        box.postDelayed(this::load, 800);
                    }).setNegativeButton(R.string.btn_cancel, null).show());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 20);
            box.addView(all, lp);
        }
    }

    private static String nz(String s) {
        return s == null ? "?" : s;
    }
}
