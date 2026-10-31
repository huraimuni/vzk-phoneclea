package kr.todanjeon.phoneclean;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/** 기존 화면의 한국어 문구를 앱 언어에 맞게 바꿔 준다. 한국어 원문이 키이며, 없으면 원문을 그대로 쓴다. */
final class Tr {
    private static String loadedTag;
    private static HashMap<String, String> map = new HashMap<>();

    private Tr() {
    }

    private static synchronized HashMap<String, String> map() {
        Context c = App.ctx;
        if (c == null) return null;
        String tag = Lang.tag(c);
        if (tag.equals(loadedTag)) return map;
        HashMap<String, String> m = new HashMap<>();
        if (!"ko".equals(tag)) {
            try {
                InputStream in = c.getAssets().open("tr/" + tag + ".json");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                in.close();
                JSONObject o = new JSONObject(bo.toString("UTF-8"));
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, o.getString(k));
                }
            } catch (Exception ignored) {
            }
        }
        map = m;
        loadedTag = tag;
        return map;
    }

    /** 한국어 원문 -> 현재 언어 문구 */
    static String t(String ko) {
        try {
            HashMap<String, String> m = map();
            if (m == null) return ko;
            String v = m.get(ko);
            return v != null ? v : ko;
        } catch (Throwable e) {
            return ko;
        }
    }

    /** 자리 표시자(%s, %d)가 있는 문구 */
    static String f(String ko, Object... args) {
        try {
            return String.format(Locale.getDefault(), t(ko), args);
        } catch (Throwable e) {
            try {
                return String.format(Locale.getDefault(), ko, args);
            } catch (Throwable e2) {
                return ko;
            }
        }
    }
}
