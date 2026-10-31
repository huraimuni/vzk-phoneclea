package kr.todanjeon.phoneclean;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/** 부모 폰: 자녀가 보낸 '앱 업데이트 요청' 확인 화면. [업데이트]를 누르면 새 버전을 받는 화면으로 바로 이동한다. */
public class UpdateRequestActivity extends BaseActivity {
    static final String EXTRA_SESSION = "session";
    static final String EXTRA_NAME = "name";
    private DocumentReference ref;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String sid = getIntent().getStringExtra(EXTRA_SESSION);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (sid == null || Auth.uid() == null) {
            finish();
            return;
        }
        if (name == null) name = "?";
        StandbyService.clearRequestNotification(this);
        ref = FirebaseFirestore.getInstance().collection("sessions").document(sid);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 26), Ui.dp(this, 18), Ui.dp(this, 18));

        TextView t = Ui.text(this, getString(R.string.update_req_title, name), 24, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t);
        TextView s = Ui.text(this, getString(R.string.update_req_sub, BuildConfig.VERSION_NAME), 17, Ui.GRAY, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 26));
        root.addView(s);

        Button ok = Ui.button(this, getString(R.string.btn_update_now), Ui.GREEN, Color.WHITE);
        ok.setTextSize(26);
        ok.setPadding(0, Ui.dp(this, 30), 0, Ui.dp(this, 30));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ref.update("status", "accepted");
                Updater.now(UpdateRequestActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });
        root.addView(ok);
        Button no = Ui.button(this, getString(R.string.btn_later), Color.WHITE, Ui.INK);
        no.setTextSize(18);
        no.setOnClickListener(v -> {
            ref.update("status", "denied");
            finish();
        });
        root.addView(no);
        setContentView(root);
    }
}
