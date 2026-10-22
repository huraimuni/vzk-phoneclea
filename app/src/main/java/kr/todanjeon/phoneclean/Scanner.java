package kr.todanjeon.phoneclean;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.SigningInfo;
import android.content.pm.Signature;
import android.content.pm.ResolveInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
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

    /** 분석 진행 상황 (화면에서 몇 개째인지 보여 주기 위함) */
    static volatile int progressDone = 0;
    static volatile int progressTotal = 0;

    private static final Set<String> TRUSTED_INSTALLERS = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.skt.skaf.A000Z00040",
            "com.sec.android.easyMover"));

    /** 제조사가 출고 때 설치해 주는 앱의 설치 프로그램 */
    private static final Set<String> PRELOAD_INSTALLERS = new HashSet<>(Arrays.asList(
            "com.sec.android.preloadinstaller",
            "com.samsung.android.app.omcagent",
            "com.lge.appbox.client"));

    private static final String[] TRUSTED_PREFIXES = {
            "com.lge.", "com.qualcomm.",
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

    /** 광고 SDK 패키지 이름 (광고가 있다는 뜻일 뿐, 그 자체가 악성이라는 뜻은 아님) */
    private static final String[] AD_SDKS = {
            "com.google.android.gms.ads", "com.facebook.ads", "com.unity3d.services.ads", "com.applovin",
            "com.ironsource", "com.mbridge", "com.vungle", "com.chartboost", "com.inmobi", "com.adcolony",
            "com.bytedance.sdk.openadsdk", "com.mopub", "com.startapp", "net.pubnative", "com.fyber",
            "com.tapjoy", "com.smaato", "com.kakao.adfit", "com.fsn.cauly", "com.igaworks",
            "com.mocoplex.adlib", "com.buzzvil", "com.pincrux", "com.nextapps"
    };

    private static final String[] SENSITIVE_PERMS = {
            "android.permission.READ_CONTACTS", "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
            "android.permission.READ_CALL_LOG", "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.RECORD_AUDIO"
    };

    private Scanner() {
    }

    private static boolean opAllowed(Context ctx, String op, int uid, String pkg) {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            return ops.checkOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 사용 기록 접근이 허용돼 있는가 (설정에서 사용자가 직접 켜는 권한) */
    static boolean hasUsageAccess(Context ctx) {
        return opAllowed(ctx, "android:get_usage_stats", Process.myUid(), ctx.getPackageName());
    }

    private static Map<String, Long> lastUsedMap(Context ctx, long now) {
        Map<String, Long> out = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            Map<String, UsageStats> m = usm.queryAndAggregateUsageStats(now - 90 * DAY, now);
            if (m != null) {
                for (Map.Entry<String, UsageStats> en : m.entrySet()) {
                    out.put(en.getKey(), en.getValue().getLastTimeUsed());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static int countAdSdks(PackageManager pm, String pkg) {
        Set<String> found = new HashSet<>();
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS);
            if (pi.activities != null) for (ActivityInfo a : pi.activities) matchAd(a.name, found);
            if (pi.services != null) for (ServiceInfo sv : pi.services) matchAd(sv.name, found);
            if (pi.receivers != null) for (ActivityInfo r : pi.receivers) matchAd(r.name, found);
        } catch (Exception ignored) {
        }
        return found.size();
    }

    private static void matchAd(String name, Set<String> found) {
        if (name == null) return;
        for (String p : AD_SDKS) {
            if (name.startsWith(p)) {
                found.add(p);
                return;
            }
        }
    }

    private static String signerHash(PackageManager pm, String pkg) {
        if (Build.VERSION.SDK_INT < 28) return null;
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo si = pi.signingInfo;
            if (si == null) return null;
            Signature[] sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            if (sigs == null || sigs.length == 0) return null;
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 지금 기본 홈 화면(런처) 앱. 지정 안 돼 있으면 null */
    static String homePackage(PackageManager pm) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return null;
            String p = ri.activityInfo.packageName;
            return "android".equals(p) ? null : p;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isSystemPkg(PackageManager pm, String pkg) {
        try {
            return (pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 폰에 원래 들어 있는(시스템) 홈 화면 앱 목록: {패키지, 이름} */
    static List<String[]> systemHomes(PackageManager pm) {
        List<String[]> out = new ArrayList<>();
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (isSystemPkg(pm, p)) {
                    CharSequence l = ri.loadLabel(pm);
                    out.add(new String[]{p, l == null ? p : l.toString()});
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 일시 중지에서 빼야 하는 필수 앱: 키보드, 홈 화면, 기본 문자, 기본 전화 */
    static Set<String> essentialPackages(Context ctx) {
        Set<String> out = new HashSet<>();
        try {
            String ime = Settings.Secure.getString(ctx.getContentResolver(), "default_input_method");
            if (ime != null) {
                ComponentName cn = ComponentName.unflattenFromString(ime);
                if (cn != null) out.add(cn.getPackageName());
            }
        } catch (Exception ignored) {
        }
        String home = homePackage(ctx.getPackageManager());
        if (home != null) out.add(home);
        try {
            String sms = Telephony.Sms.getDefaultSmsPackage(ctx);
            if (sms != null) out.add(sms);
        } catch (Exception ignored) {
        }
        try {
            TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            String dialer = tm == null ? null : tm.getDefaultDialerPackage();
            if (dialer != null) out.add(dialer);
        } catch (Exception ignored) {
        }
        return out;
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

        // 홈 화면이 기본 앱이 아닌 다른 앱으로 바뀌었는지
        String homeRaw = homePackage(pm);
        String homePkg = (homeRaw != null && !isSystemPkg(pm, homeRaw)) ? homeRaw : null;

        // 출고 때 같이 설치된 앱 구분용 기준 시각 (안드로이드 시스템 자체의 최초 설치 시각)
        long systemTime = 0;
        try {
            systemTime = pm.getPackageInfo("android", 0).firstInstallTime;
        } catch (Exception ignored) {
        }

        Map<String, Long> usage = hasUsageAccess(ctx) ? lastUsedMap(ctx, now) : new HashMap<String, Long>();
        boolean usageKnown = !usage.isEmpty();
        PowerManager power = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);

        List<AppEntry> list = new ArrayList<>();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        progressDone = 0;
        progressTotal = apps.size();
        for (ApplicationInfo ai : apps) {
            progressDone++;
            try {
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
            } else if ("com.sec.android.easyMover".equals(installer)) {
                e.installerText = "삼성 스마트 스위치 (폰 옮기기)";
            } else if (installer.contains("packageinstaller")) {
                e.installerText = "파일 설치 (브라우저·파일앱 등에서 직접 설치)";
            } else {
                e.installerText = "다른 설치 프로그램 (" + installer + ")";
            }

            e.accEnabled = accSet.contains(ai.packageName);
            e.adminActive = adminSet.contains(ai.packageName);
            e.isHome = ai.packageName.equals(homePkg);

            // 출고 시 기본 설치된 앱(제조사·통신사 기본 앱)은 의심 대상에서 뺀다
            boolean preloaded = (installer != null && PRELOAD_INSTALLERS.contains(installer))
                    || (e.installTime > 0 && systemTime > 0 && e.installTime <= systemTime + 3 * DAY);
            e.preloaded = preloaded;
            if (preloaded && !e.accEnabled && !e.adminActive && !e.isHome) {
                e.installerText = "출고 시 기본 설치된 앱으로 보임 (제조사·통신사 기본 앱)";
                e.trusted = true;
                e.score = 0;
                list.add(e);
                continue;
            }

            e.trusted = trustedInstaller && hasTrustedPrefix(ai.packageName) && !e.isHome;
            if (e.trusted) {
                e.score = 0;
                list.add(e);
                continue;
            }

            int score = 0;
            int hard = 0;
            if (sideload && !preloaded) {
                score += 3;
                hard++;
                e.reasons.add("플레이스토어·갤럭시 스토어가 아닌 곳에서 설치됨 [" + e.installerText + "]");
            }

            if (e.isHome) {
                score += 4;
                hard++;
                e.reasons.add("홈 화면(런처)이 폰 기본 앱이 아닌 이 앱으로 바뀌어 있음");
            }

            if (e.accEnabled) {
                score += 4;
                hard++;
                e.reasons.add("접근성 권한이 켜져 있음 (화면을 읽고 대신 조작할 수 있음)");
            }

            if (e.adminActive) {
                score += 4;
                hard++;
                e.reasons.add("기기 관리자로 등록됨 (그냥은 삭제가 안 됨)");
            }

            if (listenerSet.contains(ai.packageName)) {
                score += 1;
                e.reasons.add("알림 읽기 권한이 켜져 있음");
            }

            e.overlay = perms.contains("android.permission.SYSTEM_ALERT_WINDOW")
                    && overlayGranted(ctx, ai.uid, ai.packageName);
            if (e.overlay) {
                score += 2;
                hard++;
                e.reasons.add("다른 앱 위에 화면을 띄울 수 있음 (광고 팝업에 쓰이는 권한)");
            }

            if (pm.getLaunchIntentForPackage(ai.packageName) == null) {
                score += 2;
                e.reasons.add("실행 아이콘이 없는 앱 (화면에 안 보이고 숨어 있을 수 있음)");
            }

            boolean requestsInstall = perms.contains("android.permission.REQUEST_INSTALL_PACKAGES");
            e.installAllowed = requestsInstall
                    && opAllowed(ctx, "android:request_install_packages", ai.uid, ai.packageName);
            if (e.installAllowed) {
                score += 3;
                hard++;
                e.reasons.add("'다른 앱 설치 허용'이 켜져 있음 (또 다른 앱을 깔 수 있음)");
            }
            long apkSize = 0;
            try {
                apkSize = new File(ai.sourceDir).length();
            } catch (Exception ignored) {
            }
            if (requestsInstall && apkSize > 0 && apkSize < 3L * 1024 * 1024) {
                e.tiny = true;
                score += 2;
                e.reasons.add("아주 작은 파일인데 다른 앱 설치 권한을 요청함");
            }

            if (perms.contains("android.permission.RECEIVE_SMS") || perms.contains("android.permission.READ_SMS")) {
                score += 1;
                e.reasons.add("문자 읽기·수신 권한을 요청함");
            }

            String hay = (e.label + " " + e.pkg).toLowerCase(Locale.ROOT);
            boolean strong = containsAny(hay, KW_STRONG);
            boolean weak = !strong && containsAny(hay, KW_WEAK);
            if (strong) {
                e.keyStrong = true;
                score += 2;
                e.reasons.add("겁주기·청소 앱에 흔한 이름 (바이러스·백신·청소·최적화 등)");
            } else if (weak) {
                score += 1;
                e.reasons.add("광고로 자주 깔리는 도구형 앱 이름 (PDF·뷰어·스캐너 등)");
            }

            int sens = 0;
            for (String sp : SENSITIVE_PERMS) if (perms.contains(sp)) sens++;
            if ((strong || weak) && sens >= 1) {
                e.mismatch = true;
                score += 2;
                hard++;
                e.reasons.add("이름은 단순 도구인데 연락처·문자·위치·마이크 같은 권한을 요청함");
            }

            e.adCount = countAdSdks(pm, ai.packageName);
            if (e.adCount >= 3) {
                score += 2;
                hard++;
                e.reasons.add("광고 프로그램(SDK)이 " + e.adCount + "종 들어 있음");
            } else if (e.adCount == 2) {
                score += 1;
                e.reasons.add("광고 프로그램(SDK)이 2종 들어 있음");
            }

            boolean boot = perms.contains("android.permission.RECEIVE_BOOT_COMPLETED");
            boolean batteryReq = perms.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
            boolean fullScreen = perms.contains("android.permission.USE_FULL_SCREEN_INTENT");
            if (e.adCount >= 1 && boot && (e.overlay || fullScreen || batteryReq)) {
                e.bootCombo = true;
                score += 2;
                hard++;
                e.reasons.add("광고 + 폰 켤 때 자동 실행 + 화면 위 표시·상시 실행 조합");
            }
            try {
                if (batteryReq && power != null && power.isIgnoringBatteryOptimizations(ai.packageName)) {
                    score += 1;
                    e.reasons.add("배터리 제한이 풀려서 항상 켜져 있도록 허용됨");
                }
            } catch (Exception ignored) {
            }

            if (ai.targetSdkVersion < 23 && sideload && !preloaded) {
                score += 2;
                e.reasons.add("오래된 방식으로 만든 앱 (권한 창 없이 권한이 한꺼번에 부여됨)");
            }

            if (e.installTime > 0 && now - e.installTime <= 30 * DAY) {
                score += 1;
                e.reasons.add("최근 30일 안에 설치됨");
            }

            if (usageKnown) {
                e.usageKnown = true;
                Long lu = usage.get(ai.packageName);
                long last = lu == null ? 0 : lu;
                e.lastUsed = last;
                if (last >= now - 7 * DAY) {
                    e.usedRecently = true;
                } else if (last == 0) {
                    if (e.installTime >= now - 90 * DAY && now - e.installTime > 6L * 60 * 60 * 1000) {
                        e.neverOpened = true;
                        score += 3;
                        hard++;
                        e.reasons.add("설치한 뒤 한 번도 열어 보지 않음 (광고로 자동 설치된 앱의 흔한 특징)");
                    } else {
                        e.longUnused = true;
                        score += 1;
                        e.reasons.add("최근 90일 동안 한 번도 쓰지 않음");
                    }
                } else if (now - last > 60 * DAY) {
                    e.longUnused = true;
                    score += 1;
                    e.reasons.add("60일 넘게 쓰지 않음");
                }
            }

            e.signer = signerHash(pm, ai.packageName);
            e.score = score;
            e.hard = hard;
            list.add(e);
                    } catch (Throwable t) {
                // 한 앱을 읽다 오류가 나도 나머지 앱 분석은 계속한다
                CrashLog.record(ctx, "앱 분석", t);
            }
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

        // 같은 개발사(서명)가 만든 앱이 3개 이상이고, 그 앱에 다른 의심 근거가 있으면 가산
        Map<String, List<AppEntry>> bySigner = new HashMap<>();
        for (AppEntry a : list) {
            if (a.trusted || a.signer == null) continue;
            List<AppEntry> l = bySigner.get(a.signer);
            if (l == null) {
                l = new ArrayList<>();
                bySigner.put(a.signer, l);
            }
            l.add(a);
        }
        for (List<AppEntry> l : bySigner.values()) {
            if (l.size() < 3) continue;
            for (AppEntry a : l) {
                if (a.score > 0) {
                    a.clustered = true;
                    a.score += 2;
                    a.hard++;
                    a.reasons.add("같은 개발사(서명)가 만든 앱이 " + l.size() + "개 설치돼 있음");
                }
            }
        }

        // 판정 유형 이름 붙이기 + 최근에 직접 열어 본 앱은 안전 후보로
        for (AppEntry a : list) {
            if (a.trusted) continue;
            if (a.usedRecently && !a.accEnabled && !a.adminActive && !a.isHome && !a.installAllowed) {
                a.autoSafe = true;
                a.reasons.add(0, "최근 7일 안에 직접 열어 본 앱이라 안전 후보로 분류함");
            }
            if (a.accEnabled || a.adminActive || a.overlay || a.isHome) a.tags.add("권한 장악형");
            if (a.adCount >= 3 || (a.adCount >= 2 && (a.overlay || a.neverOpened || a.bootCombo))) a.tags.add("광고 도배형");
            if (a.neverOpened || a.longUnused) a.tags.add("방치형");
            if (a.clustered) a.tags.add("한 패거리형");
            if (a.installAllowed || a.tiny) a.tags.add("설치 도우미형");
            if (a.mismatch || a.keyStrong) a.tags.add("도구·겁주기 위장형");
        }

        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry x, AppEntry y) {
                if (x.sortScore() != y.sortScore()) return y.sortScore() - x.sortScore();
                return Long.compare(y.installTime, x.installTime);
            }
        });
        return list;
    }
}
