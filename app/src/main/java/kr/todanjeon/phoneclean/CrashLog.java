package kr.todanjeon.phoneclean;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 오류 기록. 인터넷 권한이 없으므로 자동으로 보내지 않고, 폰 안에만 저장해 두었다가
 * 사용자가 직접 [오류 내용 보내기]를 눌렀을 때만 카톡·문자 등으로 공유합니다.
 * 앱 이름이나 개인 정보는 담지 않습니다.
 */
final class CrashLog {
    private static final String CRASH = "crash.txt";
    private static final String EVENTS = "events.txt";
    private static final int MAX_EVENT_BYTES = 4000;
    private static volatile int recorded = 0;

    private CrashLog() {
    }

    static void install(final Context app) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    write(app, CRASH, stamp() + " 앱이 오류로 꺼짐 (스레드 " + t.getName() + ")\n" + trace(e), false);
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /** 분석 중 앱 하나를 못 읽는 것처럼 앱이 꺼지지는 않은 오류 기록 (앞쪽 몇 건만) */
    static void record(Context ctx, String where, Throwable e) {
        if (recorded >= 5) return;
        recorded++;
        try {
            String first = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            write(ctx, EVENTS, stamp() + " [" + where + "] " + first + "\n", true);
        } catch (Throwable ignored) {
        }
    }

    static boolean hasCrash(Context ctx) {
        return new File(ctx.getFilesDir(), CRASH).exists();
    }

    static void clearCrash(Context ctx) {
        new File(ctx.getFilesDir(), CRASH).delete();
    }

    static String buildReport(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("[VZK 폰클린 오류 보고]\n");
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            sb.append("앱 버전: ").append(pi.versionName).append("\n");
        } catch (Throwable ignored) {
        }
        sb.append("기종: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("안드로이드: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("분석 진행: ").append(Scanner.progressDone).append(" / ").append(Scanner.progressTotal).append("\n");
        String c = read(ctx, CRASH);
        if (c != null) sb.append("\n[꺼짐 오류]\n").append(c).append("\n");
        String ev = read(ctx, EVENTS);
        if (ev != null) sb.append("\n[분석 중 오류]\n").append(ev).append("\n");
        if (c == null && ev == null) sb.append("\n기록된 오류 없음. 증상을 아래에 적어 주세요.\n");
        sb.append("\n증상: ");
        return sb.toString();
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(new Date());
    }

    private static String trace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 3000 ? s.substring(0, 3000) : s;
    }

    private static void write(Context ctx, String name, String text, boolean append) throws Exception {
        File f = new File(ctx.getFilesDir(), name);
        if (append && f.exists() && f.length() > MAX_EVENT_BYTES) return;
        FileOutputStream out = new FileOutputStream(f, append);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static String read(Context ctx, String name) {
        try {
            File f = new File(ctx.getFilesDir(), name);
            if (!f.exists()) return null;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[(int) Math.min(f.length(), 6000)];
            int n = in.read(b);
            in.close();
            return n <= 0 ? null : new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }
}
