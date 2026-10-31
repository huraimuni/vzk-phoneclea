package kr.todanjeon.phoneclean;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;

/** 통화가 끝나면, 연락처에 없는 번호만 골라 "이 번호 검색해 보기" 알림을 띄운다(전체 기능판, 한국어). 번호는 폰 밖으로 보내지 않고 검색은 사용자가 알림을 눌렀을 때만 한다. */
public class CallCheck extends BroadcastReceiver {
    static final String PREFS = "phoneclean";
    static final String KEY_ON = "call_check_on";
    private static final String CH = "callcheck";
    private static String last = TelephonyManager.EXTRA_STATE_IDLE;

    static boolean on(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false);
    }

    static void setOn(Context c, boolean v) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, v).apply();
    }

    static String[] perms() {
        return new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS};
    }

    static boolean granted(Context c) {
        for (String p : perms()) {
            if (android.os.Build.VERSION.SDK_INT < 33 && p.equals(Manifest.permission.POST_NOTIFICATIONS)) continue;
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        String st = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (st == null) return;
        String prev = last;
        last = st;
        if (!TelephonyManager.EXTRA_STATE_IDLE.equals(st) || TelephonyManager.EXTRA_STATE_IDLE.equals(prev)) return;
        if (!on(ctx) || !granted(ctx)) return;
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 통화 기록에 올라올 때까지 잠깐 기다린다
                check(ctx.getApplicationContext());
            } catch (Throwable ignored) {
            } finally {
                pr.finish();
            }
        }).start();
    }

    private static void check(Context c) {
        String number = null;
        int type = 0;
        long date = 0;
        try (Cursor cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE}, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 1")) {
            if (cur != null && cur.moveToFirst()) {
                number = cur.getString(0);
                type = cur.getInt(1);
                date = cur.getLong(2);
            }
        }
        if (number == null || number.trim().isEmpty()) return;
        if (System.currentTimeMillis() - date > 3 * 60 * 1000L) return;
        if (type != CallLog.Calls.INCOMING_TYPE && type != CallLog.Calls.MISSED_TYPE) return; // 걸려 온 전화만
        if (inContacts(c, number)) return;
        notifyNumber(c, number);
    }

    private static boolean inContacts(Context c, String number) {
        Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cur = c.getContentResolver().query(u, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return cur != null && cur.getCount() > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void notifyNumber(Context c, String number) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH, "통화 후 번호 확인", NotificationManager.IMPORTANCE_HIGH));
        String q = number + " 보이스피싱 사기";
        String url = Lang.useNaver(c)
                ? "https://m.search.naver.com/search.naver?query=" + Uri.encode(q)
                : "https://www.google.com/search?q=" + Uri.encode(q);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, number.hashCode(), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent scam = new Intent(c, ScamActivity.class);
        scam.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent sp = PendingIntent.getActivity(c, 1, scam, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("방금 모르는 번호와 통화했어요")
                .setContentText(number + " · 눌러서 검색해 보세요")
                .setStyle(new Notification.BigTextStyle().bigText(number + "\n연락처에 없는 번호입니다. 눌러서 보이스피싱·사기 신고 글이 있는지 검색해 보세요. 돈이나 앱 설치를 요구받았다면 112·1332에 문의하세요."))
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "점검 화면", sp).build())
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(7100, n);
        } catch (Exception ignored) {
        }
    }
}
