package kr.todanjeon.phoneclean;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/** 부모 폰: 화면을 보여 줄지 묻는 큰 허용/거부 화면. 허용을 눌러야만 화면 공유가 시작된다. */
public class ConsentActivity extends BaseActivity {
    static final String EXTRA_SESSION = "session";
    static final String EXTRA_NAME = "name";
    private static final int RC_PROJ = 501;
    private String sid, name;
    private DocumentReference ref;
    private boolean askedOverlay, waitingOverlay;
    private com.google.firebase.firestore.ListenerRegistration watch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sid = getIntent().getStringExtra(EXTRA_SESSION);
        name = getIntent().getStringExtra(EXTRA_NAME);
        if (sid == null || Auth.uid() == null) {
            finish();
            return;
        }
        if (name == null) name = "?";
        ref = FirebaseFirestore.getInstance().collection("sessions").document(sid);
        StandbyService.clearRequestNotification(this);
        // 자녀가 요청을 취소/종료하면 이 화면도 자동으로 닫는다.
        watch = ref.addSnapshotListener((d, e) -> {
            if (d != null && d.exists() && !"requested".equals(d.getString("status")) && !ShareService.running && !waitingOverlay
                    && !projecting) finish();
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 26), Ui.dp(this, 18), Ui.dp(this, 18));

        TextView t = Ui.text(this, getString(R.string.consent_title, name), 24, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t);
        TextView w = Ui.text(this, getString(R.string.consent_warn), 17, Ui.RED, true);
        w.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 10));
        root.addView(w);
        TextView p = Ui.text(this, getString(R.string.consent_privacy), 15, Ui.GRAY, false);
        ScrollView sv = new ScrollView(this);
        sv.addView(p);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        Button no = Ui.button(this, getString(R.string.btn_deny), Ui.RED, Color.WHITE);
        no.setTextSize(24);
        no.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 26));
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ref.update("status", "denied");
                finish();
            }
        });
        Button ok = Ui.button(this, getString(R.string.btn_allow), Ui.GREEN, Color.WHITE);
        ok.setTextSize(24);
        ok.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 26));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Guide.allowed(ConsentActivity.this) && !askedOverlay) {
                    // 설정 화면으로 바로 보낸다. 돌아오면 onResume에서 자동으로 이어서 진행한다.
                    askedOverlay = true;
                    waitingOverlay = true;
                    Ui.toast(ConsentActivity.this, getString(R.string.overlay_need));
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName())));
                    return;
                }
                startProjection();
            }
        });
        row.addView(no, Ui.weight(this, 4));
        row.addView(ok, Ui.weight(this, 4));
        root.addView(row);
        setContentView(root);
    }

    private boolean projecting;

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingOverlay) {
            waitingOverlay = false;
            // 설정에서 돌아왔으면(허용했든 아니든) 이어서 화면 공유 허용 창으로 간다.
            startProjection();
        }
    }

    private void startProjection() {
        if (projecting) return;
        projecting = true;
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent it;
        if (Build.VERSION.SDK_INT >= 34) {
            // 안드로이드 14 이상: '한 앱'이 아니라 '전체 화면'이 기본(그것만 선택 가능)으로 나온다.
            it = mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            it = mpm.createScreenCaptureIntent();   // 안드로이드 13 이하는 원래 전체 화면 공유
        }
        startActivityForResult(it, RC_PROJ);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watch != null) watch.remove();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != RC_PROJ) return;
        projecting = false;
        if (res == RESULT_OK && data != null) {
            Intent s = new Intent(this, ShareService.class)
                    .putExtra(ShareService.EXTRA_SESSION, sid)
                    .putExtra(ShareService.EXTRA_HELPER, name)
                    .putExtra(ShareService.EXTRA_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
            else startService(s);
            finish();
        } else {
            ref.update("status", "denied");
            finish();
        }
    }
}
