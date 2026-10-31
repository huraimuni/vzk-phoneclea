package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 업데이트 확인: 공개 저장소의 최신 버전 번호와 비교해 새 버전이 있으면 안내한다. */
final class Updater {
    private Updater() {
    }

    static void check(final Activity a) {
        Ui.toast(a, Tr.t("업데이트를 확인하는 중입니다..."));
        new Thread(() -> {
            int code = -1;
            String name = "";
            try {
                URL u = new URL("https://raw.githubusercontent.com/huraimuni/vzk-phoneclea/" + BuildConfig.UPDATE_BRANCH + "/app/build.gradle");
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null && sb.length() < 20000) sb.append(line).append('\n');
                r.close();
                Matcher m1 = Pattern.compile("versionCode\\s+(\\d+)").matcher(sb);
                Matcher m2 = Pattern.compile("versionName\\s+'([^']+)'").matcher(sb);
                if (m1.find()) code = Integer.parseInt(m1.group(1));
                if (m2.find()) name = m2.group(1);
            } catch (Throwable ignored) {
            }
            final int fc = code;
            final String fn = name;
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (fc < 0) {
                    Ui.toast(a, Tr.t("업데이트 정보를 가져오지 못했습니다. 인터넷 연결을 확인해 주세요."));
                } else if (fc <= BuildConfig.VERSION_CODE) {
                    Ui.toast(a, Tr.f("최신 버전입니다. (v%s)", BuildConfig.VERSION_NAME));
                } else {
                    new AlertDialog.Builder(a)
                            .setTitle(Tr.t("새 버전이 있습니다"))
                            .setMessage(Tr.f("현재 v%s → 최신 v%s\n\n받으러 갈까요?", BuildConfig.VERSION_NAME, fn))
                            .setNegativeButton(Tr.t("나중에"), null)
                            .setPositiveButton(Tr.t("받으러 가기"), (d, w) -> open(a))
                            .show();
                }
            });
        }).start();
    }

    private static void open(Activity a) {
        String installer = null;
        try {
            installer = a.getPackageManager().getInstallerPackageName(a.getPackageName());
        } catch (Throwable ignored) {
        }
        if ("com.android.vending".equals(installer)) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + a.getPackageName())));
                return;
            } catch (Throwable ignored) {
            }
        }
        Ui.openUrl(a, BuildConfig.UPDATE_URL);
    }
}
