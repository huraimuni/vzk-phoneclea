package kr.todanjeon.phoneclean;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.RendererCommon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 자녀 폰: 부모에게 화면 공유를 요청하고, 허용되면 부모 화면을 본다. */
public class ViewerActivity extends BaseActivity {
    static final String EXTRA_PARENT_UID = "parentUid";
    static final String EXTRA_PARENT_NAME = "parentName";
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private FirebaseFirestore db;
    private DocumentReference ref;
    private String parentName;
    private TextView status;
    private SurfaceViewRenderer view;
    private EglBase egl;
    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private ListenerRegistration l1, l2;
    private boolean answered, ended;
    private int vw = 0, vh = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String parentUid = getIntent().getStringExtra(EXTRA_PARENT_UID);
        parentName = getIntent().getStringExtra(EXTRA_PARENT_NAME);
        if (parentUid == null || Auth.uid() == null) {
            finish();
            return;
        }
        db = FirebaseFirestore.getInstance();
        egl = EglBase.create();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new SurfaceViewRenderer(this);
        view.init(egl.getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override public void onFirstFrameRendered() { }
            @Override public void onFrameResolutionChanged(int w, int hh, int rot) {
                if (rot == 90 || rot == 270) { vw = hh; vh = w; } else { vw = w; vh = hh; }
            }
        });
        view.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) sendCircle(ev.getX(), ev.getY());
            return true;
        });
        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        status = Ui.text(this, getString(R.string.viewer_waiting, parentName), 18, Color.WHITE, true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));

        Button end = Ui.button(this, getString(R.string.btn_end), Ui.RED, Color.WHITE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), 0);
        root.addView(end, lp);
        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endSession();
            }
        });
        // 안내 문구 버튼(아래쪽 가로 스크롤)
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        int[] ids = {R.string.phrase_tap, R.string.phrase_back, R.string.phrase_scroll,
                R.string.phrase_wait, R.string.phrase_ok, R.string.phrase_nothing};
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            Button pb = Ui.button(this, getString(ids[i]), Color.argb(230, 32, 40, 66), Color.WHITE);
            pb.setTextSize(17);
            pb.setOnClickListener(v -> {
                Map<String, Object> m2 = new HashMap<>();
                m2.put("kind", "say");
                m2.put("phrase", idx);
                m2.put("at", Timestamp.now());
                if (ref != null) ref.collection("pings").add(m2);
            });
            bar.addView(pb, Ui.weight(this, 3));
        }
        hs.addView(bar);
        root.addView(hs, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        String sid = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        ref = db.collection("sessions").document(sid);
        Map<String, Object> m = new HashMap<>();
        m.put("parentUid", parentUid);
        m.put("childUid", Auth.uid());
        m.put("childName", Auth.myName(this));
        m.put("parentName", parentName);
        m.put("status", "requested");
        m.put("createdAt", FieldValue.serverTimestamp());
        ref.set(m).addOnSuccessListener(x -> listen()).addOnFailureListener(e -> {
            Ui.toast(this, getString(R.string.err_generic, Auth.msg(e)));
            finish();
        });
        // 2분 안에 허용이 없으면 요청을 닫는다.
        h.postDelayed(() -> {
            if (!answered && !ended) {
                endSession();
            }
        }, 120_000);
    }

    private void listen() {
        l1 = ref.addSnapshotListener((s, e) -> {
            if (s == null || !s.exists() || ended) return;
            String st = s.getString("status");
            if ("denied".equals(st) || "ended".equals(st)) {
                ended = true;
                status.setVisibility(View.VISIBLE);
                status.setText("denied".equals(st) ? R.string.viewer_denied : R.string.viewer_ended);
                teardown();
                return;
            }
            String offer = s.getString("offer");
            if (offer != null && !answered) {
                answered = true;
                status.setText(R.string.viewer_connecting);
                begin(offer);
            }
        });
    }

    private void begin(final String offer) {
        factory = Rtc.factory(this, egl);
        Rtc.iceServers(servers -> h.post(() -> {
            if (ended) return;
            PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(servers);
            cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            pc = factory.createPeerConnection(cfg, new PeerConnection.Observer() {
                @Override public void onSignalingChange(PeerConnection.SignalingState s) { }
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) { }
                @Override public void onIceConnectionReceivingChange(boolean b) { }
                @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
                @Override public void onIceCandidate(IceCandidate c) {
                    ref.collection("cand").add(Rtc.cand("child", c));
                }
                @Override public void onIceCandidatesRemoved(IceCandidate[] c) { }
                @Override public void onAddStream(MediaStream m) { }
                @Override public void onRemoveStream(MediaStream m) { }
                @Override public void onDataChannel(DataChannel d) { }
                @Override public void onRenegotiationNeeded() { }
                @Override
                public void onTrack(RtpTransceiver t) {
                    if (t.getReceiver().track() instanceof VideoTrack) {
                        final VideoTrack vt = (VideoTrack) t.getReceiver().track();
                        h.post(() -> {
                            vt.addSink(view);
                            status.setVisibility(View.GONE);
                        });
                    }
                }
            });
            if (pc == null) return;
            pc.setRemoteDescription(new ShareService.Sdp() {
                @Override
                public void onSetSuccess() {
                    pc.createAnswer(new ShareService.Sdp() {
                        @Override
                        public void onCreateSuccess(SessionDescription sd) {
                            pc.setLocalDescription(new ShareService.Sdp(), sd);
                            ref.update("answer", sd.description);
                        }
                    }, new MediaConstraints());
                }
            }, new SessionDescription(SessionDescription.Type.OFFER, offer));
            l2 = ref.collection("cand").addSnapshotListener((qs, e) -> {
                if (qs == null || pc == null) return;
                for (DocumentChange dc : qs.getDocumentChanges()) {
                    if (dc.getType() != DocumentChange.Type.ADDED) continue;
                    if ("child".equals(dc.getDocument().getString("from"))) continue;
                    Long idx = dc.getDocument().getLong("sdpMLineIndex");
                    pc.addIceCandidate(new IceCandidate(dc.getDocument().getString("sdpMid"),
                            idx == null ? 0 : idx.intValue(), dc.getDocument().getString("sdp")));
                }
            });
        }));
    }

    /** 터치 위치를 '영상 안에서의 비율(0~1)'로 바꿔 보낸다(해상도가 달라도 같은 자리에 표시됨). */
    private void sendCircle(float tx, float ty) {
        if (ref == null || vw == 0 || vh == 0) return;
        float W = view.getWidth(), H = view.getHeight();
        float s = Math.min(W / vw, H / vh);
        float dw = vw * s, dh = vh * s;
        float ox = (W - dw) / 2f, oy = (H - dh) / 2f;
        float x = (tx - ox) / dw, y = (ty - oy) / dh;
        if (x < 0 || x > 1 || y < 0 || y > 1) return;
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "circle");
        m.put("x", (double) x);
        m.put("y", (double) y);
        m.put("at", Timestamp.now());
        ref.collection("pings").add(m);
    }

    private void endSession() {
        if (ref != null && !ended) {
            ended = true;
            ref.update("status", "ended", "endedAt", FieldValue.serverTimestamp(), "endedBy", "child");
        }
        teardown();
        finish();
    }

    private void teardown() {
        try { if (l1 != null) l1.remove(); } catch (Throwable ignored) { }
        try { if (l2 != null) l2.remove(); } catch (Throwable ignored) { }
        l1 = null;
        l2 = null;
        try { if (pc != null) pc.close(); } catch (Throwable ignored) { }
        pc = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        if (!ended && ref != null) ref.update("status", "ended", "endedAt", FieldValue.serverTimestamp(), "endedBy", "child");
        teardown();
        try { if (view != null) view.release(); } catch (Throwable ignored) { }
        try { if (factory != null) factory.dispose(); } catch (Throwable ignored) { }
        try { if (egl != null) egl.release(); } catch (Throwable ignored) { }
    }
}
