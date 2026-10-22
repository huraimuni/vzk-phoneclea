package kr.todanjeon.phoneclean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

/** 새 앱 분석 알림(화면 위에 뜨는 알림 + 누르면 분석 창). */
final class Alerts {
    static final String CHANNEL = "newapp";

    private Alerts() {
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "새 앱 설치 알림", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("새 앱이 설치되면 바로 분석 결과를 알려 줍니다");
        nm.createNotificationChannel(ch);
    }

    static void notifyNewApp(Context c, AppEntry e) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(c);
        int id = e.pkg.hashCode();

        Intent open = new Intent(c, AnalysisActivity.class);
        open.putExtra("pkg", e.pkg);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg));
        del.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent delPi = PendingIntent.getActivity(c, id + 1, del,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(e.levelText()).append("]");
        if (!e.tagText().isEmpty()) sb.append(" ").append(e.tagText());
        sb.append("\n");
        if (e.reasons.isEmpty()) {
            sb.append("특이 징후가 발견되지 않았습니다.");
        } else {
            for (int i = 0; i < e.reasons.size() && i < 3; i++) {
                sb.append("- ").append(e.reasons.get(i)).append("\n");
            }
        }
        sb.append("\n눌러서 분석 결과를 확인하세요. 내가 설치한 게 아니면 삭제하세요.");

        String shortText = "[" + e.levelText() + "] "
                + (e.reasons.isEmpty() ? "특이 징후 없음" : e.reasons.get(0));

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("새 앱이 설치됐어요: " + e.label)
                .setContentText(shortText)
                .setStyle(new Notification.BigTextStyle().bigText(sb.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, android.R.drawable.ic_menu_delete), "삭제", delPi).build());
        try {
            nm.notify(id, b.build());
        } catch (Exception ignored) {
        }
    }
}
