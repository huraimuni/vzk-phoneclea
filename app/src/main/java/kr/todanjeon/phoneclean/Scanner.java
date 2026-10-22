package kr.todanjeon.phoneclean;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 설치된 앱을 훑어서 의심 징후 점수를 매깁니다. 자체 악성코드 DB는 없고, 징후(휴리스틱)만 봅니다. */
final class Scanner {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static final Set<String> TRUSTED_INSTALLERS = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.skt.skaf.A000Z00040"));

    private static final String[] TRUSTED_PREFIXES = {
            "com.google.", "com.samsung.", "com.sec.", "com.android.", "com.kakao.",
            "com.nhn.android.", "com.naver.", "com.skt.", "com.kt.", "com.lguplus.",
            "com.microsoft.", "com.facebook.", "com.instagram.", "com.whatsapp",
            "viva.republica.toss", "com.coupang.", "net.daum.", "com.sktelecom."
    };

    /** 겁주기식·유틸리티 위장 앱에서 흔한 이름들 (강한 단서) */
    private static final String[] KW_STRONG = {
            "바이러스", "백신", "클리너", "청소", "부스터", "booster", "cleaner", "antivirus",
            "최적화", "배터리 절약", "battery saver", "phone clean", "junk", "memory boost"
    };

    /** 광고로 자주 깔리는 도구형 앱 이름들 (약한 단서) */
    private static final String[] KW_WEAK = {
            "pdf", "뷰어", "viewer", "리더", "reader", "문서", "스캐너", "scanner", "document",
            "손전등", "flashlight", "나침반", "compass", "배경화면", "wallpaper", "qr", "vpn",
            "파일 관리", "file manager", "file explorer"
    };

    private Scanner() {
    }

    static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String installerOf(PackageManager pm, String pkg) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo si = pm.getInstallSourceInfo(pkg);
                String s = si.getInstallingPackageName();
                if (s == null) s = si.getInitiatingPackageName();
                return s;
            }
            return pm.getInstallerPackageName(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> packagesFromComponentList(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null) out.add(cn.getPackageName());
        }
        return out;
    }

    static Set<String> enabledAccessibilityPackages(Context ctx) {
        try {
            return packagesFromComponentList(Settings.Secure.getString(
                    ctx.getContentResolver(), "enabled_accessibility_services"));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    static Set<String> enabledListenerPackages(Context ctx) {
        try {
            return packagesFromComponentList(Settings.Secure.getString(
                    ctx.getContentResolver(), "enabled_notification_listeners"));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    static Set<String> activeAdminPackages(Context ctx) {
        Set<String> out = new HashSet<>();
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            List<ComponentName> admins = dpm == null ? null : dpm.getActiveAdmins();
            if (admins != null) {
                for (ComponentName cn : admins) out.add(cn.getPackageName());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean hasTrustedPrefix(String pkg) {
        for (String p : TRUSTED_PREFIXES) {
            if (pkg.startsWith(p)) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    private static boolean overlayGranted(Context ctx, int uid, String pkg) {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow("android:system_alert_window", uid, pkg);
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static List<AppEntry> scan(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        long now = System.currentTimeMillis();
        Set<String> accSet = enabledAccessibilityPackages(ctx);
        Set<String> listenerSet = enabledListenerPackages(ctx);
        Set<String> adminSet = activeAdminPackages(ctx);

        List<AppEntry> list = new ArrayList<>();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(ctx.getPackageName())) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            AppEntry e = new AppEntry();
            e.pkg = ai.packageName;
            CharSequence lbl = pm.getApplicationLabel(ai);
            e.label = lbl == null ? ai.packageName : lbl.toString();

            String[] requested = null;
            try {
                PackageInfo pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_PERMISSIONS);
                e.installTime = pi.firstInstallTime;
                requested = pi.requestedPermissions;
            } catch (Exception ex) {
                // 정보를 못 읽어도 목록에는 남긴다
            }
            if (requested == null) requested = new String[0];
            Set<String> perms = new HashSet<>(Arrays.asList(requested));

            String installer = installerOf(pm, ai.packageName);
            boolean trustedInstaller = installer != null && TRUSTED_INSTALLERS.contains(installer);
            boolean sideload = !trustedInstaller;
            if (installer == null) {
                e.installerText = "알 수 없음 (파일·링크로 직접 설치)";
            } else if ("com.android.vending".equals(installer)) {
                e.installerText = "구글 플레이스토어";
            } else if ("com.sec.android.app.samsungapps".equals(installer)) {
                e.installerText = "갤럭시 스토어";
            } else if ("com.skt.skaf.A000Z00040".equals(installer)) {
                e.installerText = "원스토어";
            } else if (installer.contains("packageinstaller")) {
                e.installerText = "파일 설치 (브라우저·파일앱 등에서 직접 설치)";
            } else {
                e.installerText = "다른 설치 프로그램 (" + installer + ")";
            }

            e.trusted = trustedInstaller && hasTrustedPrefix(ai.packageName);
            if (e.trusted) {
                e.score = 0;
                list.add(e);
                continue;
            }

            int score = 0;
            if (sideload) {
                score += 3;
                e.reasons.add("플레이스토어·갤럭시 스토어가 아닌 곳에서 설치됨");
            }

            e.accEnabled = accSet.contains(ai.packageName);
            if (e.accEnabled) {
                score += 4;
                e.reasons.add("접근성 권한이 켜져 있음 (화면을 읽고 대신 조작할 수 있음)");
            }

            e.adminActive = adminSet.contains(ai.packageName);
            if (e.adminActive) {
                score += 4;
                e.reasons.add("기기 관리자로 등록됨 (그냥은 삭제가 안 됨)");
            }

            if (listenerSet.contains(ai.packageName)) {
                score += 1;
                e.reasons.add("알림 읽기 권한이 켜져 있음");
            }

            if (perms.contains("android.permission.SYSTEM_ALERT_WINDOW")
                    && overlayGranted(ctx, ai.uid, ai.packageName)) {
                score += 2;
                e.reasons.add("다른 앱 위에 화면을 띄울 수 있음 (광고 팝업에 쓰이는 권한)");
            }

            if (pm.getLaunchIntentForPackage(ai.packageName) == null) {
                score += 2;
                e.reasons.add("실행 아이콘이 없는 앱 (화면에 안 보이고 숨어 있을 수 있음)");
            }

            if (perms.contains("android.permission.REQUEST_INSTALL_PACKAGES")) {
                score += 1;
                e.reasons.add("다른 앱을 설치할 수 있는 권한을 요청함");
            }
            if (perms.contains("android.permission.RECEIVE_SMS") || perms.contains("android.permission.READ_SMS")) {
                score += 1;
                e.reasons.add("문자 읽기·수신 권한을 요청함");
            }

            String hay = (e.label + " " + e.pkg).toLowerCase(Locale.ROOT);
            if (containsAny(hay, KW_STRONG)) {
                score += 2;
                e.reasons.add("겁주기·청소 앱에 흔한 이름 (바이러스·백신·청소·최적화 등)");
            } else if (containsAny(hay, KW_WEAK)) {
                score += 1;
                e.reasons.add("광고로 자주 깔리는 도구형 앱 이름 (PDF·뷰어·스캐너 등)");
            }

            if (e.installTime > 0 && now - e.installTime <= 30 * DAY) {
                score += 1;
                e.reasons.add("최근 30일 안에 설치됨");
            }

            e.score = score;
            list.add(e);
        }

        // 짧은 기간에 몰려서 설치된 앱 찾기 (48시간 안에 5개 이상)
        for (AppEntry a : list) {
            if (a.trusted || a.installTime <= 0) continue;
            int near = 0;
            for (AppEntry b : list) {
                if (b.installTime > 0 && Math.abs(a.installTime - b.installTime) <= 2 * DAY) near++;
            }
            if (near >= 5) {
                a.score += 1;
                a.reasons.add("비슷한 시기에 앱이 " + near + "개 몰려서 설치됨");
            }
        }

        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry x, AppEntry y) {
                if (x.score != y.score) return y.score - x.score;
                return Long.compare(y.installTime, x.installTime);
            }
        });
        return list;
    }
}
