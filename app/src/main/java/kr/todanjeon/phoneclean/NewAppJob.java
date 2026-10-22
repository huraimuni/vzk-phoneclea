package kr.todanjeon.phoneclean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 15분마다 새로 설치된 앱이 있는지 확인해서 알려 줍니다. (설치를 막지는 못하고, 설치 뒤에 알립니다) */
public class NewAppJob extends JobService {

    private static final int JOB_ID = 7001;
    private static final String CHANNEL = "newapp";
    static final String PREFS = "phoneclean";
    private static final String KEY_KNOWN = "known_pkgs";

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo ji = new JobInfo.Builder(JOB_ID, new ComponentName(c, NewAppJob.class))
                .setPeriodic(15L * 60 * 1000)
                .setPersisted(true)
                .build();
        js.schedule(ji);
    }

    static void cancel(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }

    static Set<String> currentUserPackages(Context c) {
        Set<String> out = new HashSet<>();
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            out.add(ai.packageName);
        }
        return out;
    }

    /** 지금 설치된 앱을 기준선으로 저장 (이후 새로 생긴 앱만 알림) */
    static void saveBaseline(Context c) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_KNOWN, currentUserPackages(c)).apply();
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check(getApplicationContext());
                } catch (Exception ignored) {
                }
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private static void check(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> known = sp.getStringSet(KEY_KNOWN, null);
        Set<String> now = currentUserPackages(c);
        if (known == null) {
            sp.edit().putStringSet(KEY_KNOWN, now).apply();
            return;
        }
        Set<String> added = new HashSet<>(now);
        added.removeAll(known);
        sp.edit().putStringSet(KEY_KNOWN, now).apply();
        if (added.isEmpty()) return;

        List<AppEntry> scanned = Scanner.scan(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "새 앱 설치 알림",
                    NotificationManager.IMPORTANCE_HIGH));
        }
        int shown = 0;
        for (AppEntry e : scanned) {
            if (!added.contains(e.pkg)) continue;
            if (shown >= 5) break;
            shown++;
            String detail = e.reasons.isEmpty() ? "특이 징후 없음" : e.reasons.get(0);
            notifyOne(c, nm, e.pkg.hashCode(), "새 앱이 설치됐어요: " + e.label,
                    "[" + e.levelText() + "] " + detail + "\n눌러서 확인하고, 모르는 앱이면 삭제하세요.");
        }
    }

    private static void notifyOne(Context c, NotificationManager nm, int id, String title, String text) {
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(id, n);
        } catch (Exception ignored) {
        }
    }
}
