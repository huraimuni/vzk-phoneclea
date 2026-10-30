package kr.todanjeon.phoneclean;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;

import java.util.Locale;

/** 앱 언어 선택(ko/en/ja/es/pt-BR/id/hi). 선택값은 기기에만 저장한다. */
final class Lang {
    static final String[] TAGS = {"ko", "en", "ja", "es", "pt-BR", "id", "hi"};
    static final String[] NAMES = {"한국어", "English", "日本語", "Español", "Português (Brasil)", "Bahasa Indonesia", "हिन्दी"};
    private static final String PREFS = "lang";

    private Lang() {
    }

    static boolean isChosen(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("tag");
    }

    static String tag(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String t = sp.getString("tag", null);
        if (t != null) return t;
        String sys = Locale.getDefault().getLanguage();
        if ("in".equals(sys)) sys = "id";
        for (String x : TAGS) if (x.equals(sys) || (x.startsWith(sys + "-") && sys.equals("pt"))) return x;
        return "en";
    }

    static void set(Context c, String tag) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tag", tag).apply();
    }

    static Context wrap(Context c) {
        Locale l = Locale.forLanguageTag(tag(c));
        Locale.setDefault(l);
        Configuration cfg = new Configuration(c.getResources().getConfiguration());
        cfg.setLocale(l);
        return c.createConfigurationContext(cfg);
    }

    /** 한국 사용자인지(로그인 방식 노출용): SIM·통신사 국가가 KR 이거나 앱 언어가 한국어. */
    static boolean isKoreaUser(Context c) {
        try {
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String sim = tm.getSimCountryIso();
                String net = tm.getNetworkCountryIso();
                if ("kr".equalsIgnoreCase(sim) || "kr".equalsIgnoreCase(net)) return true;
            }
        } catch (Exception ignored) {
        }
        return "ko".equals(tag(c)) || "KR".equals(Locale.getDefault().getCountry());
    }
}
