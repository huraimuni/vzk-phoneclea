package kr.todanjeon.phoneclean;

import android.content.Context;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** WebRTC 공통: 공장·ICE 서버(STUN 기본, TURN은 Firestore config/turn 에서 읽음)·신호 주고받기. */
final class Rtc {
    private static boolean inited;
    private Rtc() {
    }

    static synchronized PeerConnectionFactory factory(Context c, EglBase egl) {
        if (!inited) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c.getApplicationContext())
                    .createInitializationOptions());
            inited = true;
        }
        return PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    interface IceReady {
        void run(List<PeerConnection.IceServer> servers);
    }

    /** STUN + (있으면) TURN. TURN 비밀번호는 코드에 넣지 않고 서버 설정 문서에서 받는다. */
    @SuppressWarnings("unchecked")
    static void iceServers(final IceReady cb) {
        final List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        FirebaseFirestore.getInstance().collection("config").document("turn").get().addOnCompleteListener(t -> {
            try {
                if (t.isSuccessful() && t.getResult() != null && t.getResult().exists()) {
                    DocumentSnapshot d = t.getResult();
                    List<String> urls = (List<String>) d.get("urls");
                    if (urls != null && !urls.isEmpty()) {
                        PeerConnection.IceServer.Builder b = PeerConnection.IceServer.builder(urls);
                        if (d.getString("username") != null) b.setUsername(d.getString("username"));
                        if (d.getString("credential") != null) b.setPassword(d.getString("credential"));
                        list.add(b.createIceServer());
                    }
                }
            } catch (Throwable ignored) {
            }
            cb.run(list);
        });
    }

    static Map<String, Object> cand(String from, IceCandidate c) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("from", from);
        m.put("sdpMid", c.sdpMid);
        m.put("sdpMLineIndex", c.sdpMLineIndex);
        m.put("sdp", c.sdp);
        return m;
    }
}
