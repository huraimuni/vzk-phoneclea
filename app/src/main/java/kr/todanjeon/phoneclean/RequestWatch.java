package kr.todanjeon.phoneclean;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Set;

/** 앱이 열려 있는 동안 자녀의 화면공유 요청을 받아 허용/거부 창을 띄우고, '가능한지 확인' 신호에 응답한다. */
final class RequestWatch {
    private static final Set<String> shown = new HashSet<>();
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static final long FRESH_MS = 3 * 60_000L;

    private RequestWatch() {
    }

    static ListenerRegistration start(final Context c) {
        String me = Auth.uid();
        if (me == null) return null;
        return FirebaseFirestore.getInstance().collection("sessions")
                .whereEqualTo("parentUid", me).whereEqualTo("status", "requested")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    for (DocumentSnapshot d : snap.getDocuments()) handle(c, d);
                });
    }

    static void handle(Context c, final DocumentSnapshot d) {
        Timestamp ts = d.getTimestamp("createdAt");
        if (ts != null && System.currentTimeMillis() - ts.toDate().getTime() > FRESH_MS) return;
        if ("ping".equals(d.getString("kind"))) {
            synchronized (shown) {
                if (!shown.add(d.getId())) return;
            }
            d.getReference().update("status", "pong");
            h.postDelayed(() -> d.getReference().delete(), 20_000L);
            return;
        }
        if (ShareService.running) return;
        synchronized (shown) {
            if (!shown.add(d.getId())) return;
        }
        String n = d.getString("childName");
        c.startActivity(new Intent(c, ConsentActivity.class)
                .putExtra(ConsentActivity.EXTRA_SESSION, d.getId())
                .putExtra(ConsentActivity.EXTRA_NAME, n == null || n.isEmpty() ? "?" : n)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
