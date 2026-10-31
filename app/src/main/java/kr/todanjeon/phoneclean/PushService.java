package kr.todanjeon.phoneclean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** 자녀 폰: 부모 폰의 위험 앱 설치 알림(푸시)을 받아 표시한다. */
public class PushService extends FirebaseMessagingService {
    static final String CH = "report";

    @Override
    public void onNewToken(String token) {
        Report.saveToken(Auth.uid(), token);
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        if (!"alert".equals(d.get("type"))) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH, Tr.t("부모님 폰 점검 알림"), NotificationManager.IMPORTANCE_HIGH));
        String who = d.get("parentName") == null ? "" : d.get("parentName");
        String app = d.get("label") == null ? "" : d.get("label");
        String why = d.get("reason") == null ? "" : d.get("reason");
        Intent open = new Intent(this, ReportActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(Tr.f("%s님 폰에 의심 앱이 설치됐어요", who))
                .setContentText(app + (why.isEmpty() ? "" : " · " + why))
                .setStyle(new Notification.BigTextStyle().bigText(app + (why.isEmpty() ? "" : "\n" + why)
                        + "\n" + Tr.t("눌러서 확인하고 전화나 화면 도움으로 도와주세요.")))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
        } catch (Exception ignored) {
        }
    }
}
