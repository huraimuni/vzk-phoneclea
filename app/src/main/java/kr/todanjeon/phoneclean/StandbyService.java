package kr.todanjeon.phoneclean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Set;

/**
 * 도움받는 사람(부모) 폰의 '대기 상태'.
 * 앱 화면을 닫아도 계속 떠 있으면서, 자녀가 보낸 화면 공유·앱 업데이트 요청을 받아 허용 화면을 바로 띄운다.
 * 상단에 작은 상시 알림이 표시되며, 설정에서 언제든 끌 수 있다.
 */
public class StandbyService extends Service {
    static final String PREFS = "standby";
    static final String KEY_ON = "standby_on";       // 사용자가 대기 모드를 켰는지(기본 켬)
    static final String KEY_PARENT = "is_parent";    // 도움받는 사람으로 연결된 가족이 있는지
    private static final String CH_STANDBY = "standby";
    private static final String CH_REQ = "request";
    private static final int NID = 78;
    private static final int NID_REQ = 79;
    private static final long FRESH_MS = 3 * 60_000L;
    private static final Set<String> handled = new HashSet<>();

    private final Handler h = new Handler(Looper.getMainLooper());
    private ListenerRegistration reg;

    static boolean enabled(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        return sp.getBoolean(KEY_ON, true) && sp.getBoolean(KEY_PARENT, false);
    }

    static void setOn(Context c, boolean on) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply();
        if (on) ensure(c);
        else c.stopService(new Intent(c, StandbyService.class));
    }

    static void setParent(Context c, boolean parent) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_PARENT, parent).apply();
    }

    /** 로그인되어 있고 대기 모드가 켜져 있으면 서비스를 띄운다(이미 떠 있으면 아무 일 없음). */
    static void ensure(Context c) {
        try {
            if (Auth.uid() == null || !enabled(c)) return;
            Intent i = new Intent(c, StandbyService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CH_STANDBY, getString(R.string.standby_channel), NotificationManager.IMPORTANCE_MIN));
            nm.createNotificationChannel(new NotificationChannel(CH_REQ, getString(R.string.request_channel), NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_STANDBY) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.standby_on_title))
                .setContentText(getString(R.string.standby_on_sub))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NID, n);
        } catch (Throwable t) {
            stopSelf();
            return;
        }
        listen();
    }

    private void listen() {
        String me = Auth.uid();
        if (me == null) {
            stopSelf();
            return;
        }
        reg = FirebaseFirestore.getInstance().collection("sessions")
                .whereEqualTo("parentUid", me).whereEqualTo("status", "requested")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (snap.isEmpty() && nm != null) nm.cancel(NID_REQ);   // 더 이상 대기 중인 요청이 없음
                    for (DocumentChange dc : snap.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) continue;
                        handle(dc.getDocument());
                    }
                });
    }

    private void handle(DocumentSnapshot d) {
        if (ShareService.running) return;
        // 오래된 요청(자녀가 이미 포기한 것)은 무시
        Timestamp ts = d.getTimestamp("createdAt");
        if (ts != null && System.currentTimeMillis() - ts.toDate().getTime() > FRESH_MS) return;
        synchronized (handled) {
            if (!handled.add(d.getId())) return;
        }
        String name = d.getString("childName");
        if (name == null) name = "?";
        boolean update = "update".equals(d.getString("kind"));
        Intent i = update ? new Intent(this, UpdateRequestActivity.class)
                .putExtra(UpdateRequestActivity.EXTRA_SESSION, d.getId())
                .putExtra(UpdateRequestActivity.EXTRA_NAME, name)
                : new Intent(this, ConsentActivity.class)
                .putExtra(ConsentActivity.EXTRA_SESSION, d.getId())
                .putExtra(ConsentActivity.EXTRA_NAME, name);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 1) 알림(전체 화면 알림 포함) — 직접 띄우기가 막혀도 눌러서 들어갈 수 있다.
        PendingIntent pi = PendingIntent.getActivity(this, d.getId().hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_REQ) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(update ? R.string.req_update_title : R.string.req_share_title, name))
                .setContentText(getString(R.string.req_tap_to_open))
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_CALL);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NID_REQ, b.build());

        // 2) 화면을 바로 띄운다(앱이 열려 있거나 [다른 앱 위에 표시]가 허용돼 있으면 곧바로 뜬다).
        final Intent direct = i;
        h.post(() -> {
            try {
                startActivity(direct);
            } catch (Throwable ignored) {
            }
        });
    }

    static void clearRequestNotification(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NID_REQ);
    }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (reg != null) reg.remove();
    }
}
