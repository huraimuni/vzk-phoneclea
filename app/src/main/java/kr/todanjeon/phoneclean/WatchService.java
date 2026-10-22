package kr.todanjeon.phoneclean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.util.List;

/** 상시 감시 모드: 새 앱이 설치되는 순간을 바로 받아서 분석 알림을 띄웁니다. */
public class WatchService extends Service {

    private static final int NOTI_ID = 4001;
    private static final String CH = "watch";
    private BroadcastReceiver rx;

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, WatchService.class));
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, WatchService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CH, "감시 중 표시", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("VZK 폰클린 감시 중")
                .setContentText("새 앱이 설치되면 바로 분석해서 알려 드립니다")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTI_ID, n, 0x40000000); // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                startForeground(NOTI_ID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return;
        }

        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return; // 업데이트는 제외
                Uri d = intent.getData();
                if (d == null) return;
                final String pkg = d.getSchemeSpecificPart();
                if (pkg == null || pkg.equals(context.getPackageName())) return;
                final Context app = context.getApplicationContext();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1500);
                            List<AppEntry> list = Scanner.scan(app);
                            for (AppEntry e : list) {
                                if (e.pkg.equals(pkg)) {
                                    Alerts.notifyNewApp(app, e);
                                    break;
                                }
                            }
                            NewAppJob.saveBaseline(app);
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(rx, f);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (rx != null) unregisterReceiver(rx);
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
