package kr.todanjeon.phoneclean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.DataChannel;
import org.webrtc.MediaStream;

import java.util.Collections;
import java.util.List;

/**
 * 도움받는 사람(부모) 폰에서 화면을 보내는 서비스.
 * 화면 영상은 WebRTC(DTLS-SRTP 암호화)로 상대 폰에 바로 가며, 서버에 저장하지 않는다.
 * 상단 알림은 공유 내내 떠 있고, [중지] 버튼으로 언제든 끊을 수 있다.
 */
public class ShareService extends Service {
    static final String ACTION_STOP = "kr.todanjeon.phoneclean.STOP_SHARE";
    static final String EXTRA_SESSION = "session";
    static final String EXTRA_DATA = "projData";
    static final String EXTRA_HELPER = "helper";
    static final long MAX_MS = 30 * 60_000L;
    private static final String CH = "share";
    private static final int NID = 77;
    static volatile boolean running;

    private final Handler h = new Handler(Looper.getMainLooper());
    private FirebaseFirestore db;
    private DocumentReference ref;
    private String sid;
    private EglBase egl;
    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private ScreenCapturerAndroid capturer;
    private SurfaceTextureHelper sth;
    private VideoSource source;
    private ListenerRegistration l1, l2;
    private boolean ended;
    private Guide guide;
    private ListenerRegistration l3;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in != null && ACTION_STOP.equals(in.getAction())) {
            finish("parent");
            return START_NOT_STICKY;
        }
        if (in == null || running) return START_NOT_STICKY;
        sid = in.getStringExtra(EXTRA_SESSION);
        Intent data = in.getParcelableExtra(EXTRA_DATA);
        String helper = in.getStringExtra(EXTRA_HELPER);
        if (sid == null || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startFg(helper == null ? "" : helper);
        running = true;
        guide = new Guide(this);
        db = FirebaseFirestore.getInstance();
        ref = db.collection("sessions").document(sid);
        start(data);
        h.postDelayed(() -> finish("timeout"), MAX_MS);
        return START_NOT_STICKY;
    }

    private void startFg(String helper) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, getString(R.string.share_channel), NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Intent stop = new Intent(this, ShareService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.share_ongoing))
                .setContentText(getString(R.string.share_ongoing_sub, helper))
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.share_stop), pi).build());
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NID, n);
    }

    private void start(final Intent projData) {
        egl = EglBase.create();
        factory = Rtc.factory(this, egl);
        Rtc.iceServers(servers -> h.post(() -> connect(servers, projData)));
    }

    private void connect(List<PeerConnection.IceServer> servers, Intent projData) {
        if (ended) return;
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(servers);
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        pc = factory.createPeerConnection(cfg, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState s) { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                if (s == PeerConnection.IceConnectionState.FAILED) h.post(() -> finish("failed"));
            }
            @Override public void onIceConnectionReceivingChange(boolean b) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
            @Override public void onIceCandidate(IceCandidate c) {
                ref.collection("cand").add(Rtc.cand("parent", c));
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] c) { }
            @Override public void onAddStream(MediaStream m) { }
            @Override public void onRemoveStream(MediaStream m) { }
            @Override public void onDataChannel(DataChannel d) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(RtpReceiver r, MediaStream[] m) { }
        });
        if (pc == null) {
            finish("failed");
            return;
        }
        capturer = new ScreenCapturerAndroid(projData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                h.post(() -> finish("parent"));
            }
        });
        source = factory.createVideoSource(true);
        sth = SurfaceTextureHelper.create("cap", egl.getEglBaseContext());
        capturer.initialize(sth, this, source.getCapturerObserver());
        // 화면 전체의 실제 크기(상태바·내비게이션 바 포함)로 맞춘다. 앱 영역 크기를 쓰면 가로세로 비율이 어긋나 화면이 늘어나 보인다.
        int w, hh;
        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect bnd = wm.getMaximumWindowMetrics().getBounds();
            w = bnd.width();
            hh = bnd.height();
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            w = dm.widthPixels;
            hh = dm.heightPixels;
        }
        float k = 1080f / Math.min(w, hh);
        if (k < 1f) {
            w = Math.round(w * k);
            hh = Math.round(hh * k);
        }
        w &= ~1;
        hh &= ~1;
        capturer.startCapture(w, hh, 15);
        VideoTrack track = factory.createVideoTrack("screen", source);
        pc.addTrack(track, Collections.singletonList("s"));

        MediaConstraints mc = new MediaConstraints();
        pc.createOffer(new Sdp() {
            @Override
            public void onCreateSuccess(final SessionDescription sd) {
                pc.setLocalDescription(new Sdp(), sd);
                ref.update("offer", sd.description, "status", "active", "startedAt", FieldValue.serverTimestamp());
            }
        }, mc);

        l1 = ref.addSnapshotListener((s, e) -> {
            if (s == null || !s.exists()) return;
            String st = s.getString("status");
            if ("ended".equals(st)) h.post(() -> finish(null));
            String ans = s.getString("answer");
            if (ans != null && pc != null && pc.getRemoteDescription() == null) {
                pc.setRemoteDescription(new Sdp(), new SessionDescription(SessionDescription.Type.ANSWER, ans));
            }
        });
        l3 = ref.collection("pings").addSnapshotListener((qs, e) -> {
            if (qs == null) return;
            for (DocumentChange dc : qs.getDocumentChanges()) {
                if (dc.getType() != DocumentChange.Type.ADDED) continue;
                // 접속 이전의 오래된 안내는 무시(3초 이내 것만)
                com.google.firebase.Timestamp ts = dc.getDocument().getTimestamp("at");
                if (ts != null && System.currentTimeMillis() - ts.toDate().getTime() > 10_000) continue;
                String kind = dc.getDocument().getString("kind");
                if ("circle".equals(kind)) {
                    Double x = dc.getDocument().getDouble("x"), y = dc.getDocument().getDouble("y");
                    if (x != null && y != null && x >= 0 && x <= 1 && y >= 0 && y <= 1) guide.circle(x.floatValue(), y.floatValue());
                } else if ("say".equals(kind)) {
                    Long id = dc.getDocument().getLong("phrase");
                    int[] ids = {R.string.phrase_tap, R.string.phrase_back, R.string.phrase_scroll,
                            R.string.phrase_wait, R.string.phrase_ok, R.string.phrase_nothing};
                    if (id != null && id >= 0 && id < ids.length) guide.say(getString(ids[id.intValue()]));
                }
            }
        });
        l2 = ref.collection("cand").addSnapshotListener((qs, e) -> {
            if (qs == null || pc == null) return;
            for (DocumentChange dc : qs.getDocumentChanges()) {
                if (dc.getType() != DocumentChange.Type.ADDED) continue;
                if ("parent".equals(dc.getDocument().getString("from"))) continue;
                Long idx = dc.getDocument().getLong("sdpMLineIndex");
                pc.addIceCandidate(new IceCandidate(dc.getDocument().getString("sdpMid"),
                        idx == null ? 0 : idx.intValue(), dc.getDocument().getString("sdp")));
            }
        });
    }

    private void finish(String by) {
        if (ended) return;
        ended = true;
        h.removeCallbacksAndMessages(null);
        try {
            if (ref != null && by != null) ref.update("status", "ended", "endedAt", FieldValue.serverTimestamp(), "endedBy", by);
        } catch (Throwable ignored) {
        }
        cleanup();
        running = false;
        stopForeground(true);
        stopSelf();
    }

    private void cleanup() {
        try { if (l1 != null) l1.remove(); } catch (Throwable ignored) { }
        try { if (l2 != null) l2.remove(); } catch (Throwable ignored) { }
        try { if (l3 != null) l3.remove(); } catch (Throwable ignored) { }
        try { if (guide != null) guide.clear(); } catch (Throwable ignored) { }
        try { if (capturer != null) { capturer.stopCapture(); capturer.dispose(); } } catch (Throwable ignored) { }
        try { if (pc != null) pc.close(); } catch (Throwable ignored) { }
        try { if (source != null) source.dispose(); } catch (Throwable ignored) { }
        try { if (sth != null) sth.dispose(); } catch (Throwable ignored) { }
        try { if (factory != null) factory.dispose(); } catch (Throwable ignored) { }
        try { if (egl != null) egl.release(); } catch (Throwable ignored) { }
        pc = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!ended) {
            ended = true;
            cleanup();
            running = false;
        }
    }

    /** SdpObserver 기본 구현 */
    static class Sdp implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sd) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String s) { }
        @Override public void onSetFailure(String s) { }
    }
}
