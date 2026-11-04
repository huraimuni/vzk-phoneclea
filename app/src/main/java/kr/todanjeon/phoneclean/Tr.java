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

    /** 한국어 화면에서 안내문 속 버튼 이름을 실제 버튼 이름과 맞춘다. */
    private static final String[][] RELABEL = {
            {"[1단계 원클릭 전체 일시 중지]", "[1단계 다른 앱 모두 잠시 멈추기]"},
            {"[원클릭 전체 일시 중지]", "[1단계 다른 앱 모두 잠시 멈추기]"},
            {"[2단계 삭제]", "[2단계 고른 앱 지우기]"},
            {"[2단계 ▸ 삭제]", "[2단계 고른 앱 지우기]"},
            {"[2단계 ▸ 선택한 앱 삭제]", "[2단계 고른 앱 지우기]"},
            {"[광고·알고리즘 정리]", "[광고 줄이기]"},
            {"[전체 앱 보기]", "[모든 앱 보기]"},
            {"[더보기 > 사용 기록 허용]", "[다른 메뉴 > 더 정확하게 찾도록 허용하기]"},
            {"[더보기 > 안전 표시한 앱 관리]", "[다른 메뉴 > 안전하다고 알려둔 앱 보기]"},
            {"[더보기 > 새 앱 즉시 분석 알림]", "[다른 메뉴 > 새 앱이 깔리면 바로 알려주기]"},
            {"[더보기 > 알림 정리]", "[다른 메뉴 > 알림 정리하기]"},
            {"[더보기 > 응급 가이드]", "[다른 메뉴 > 폰이 이상할 때]"},
            {"[안전 앱으로 표시]", "[안전한 앱으로 알려두기]"},
            {"[저장공간 관리]", "[폰 공간 정리]"},
            {"[기본 홈 화면 되돌리기]", "[원래 홈 화면으로 되돌리기]"},
            {"[더보기 > 가족 연결 · 화면 도움]", "[다른 메뉴 > 가족과 연결 · 화면 보며 도움받기]"},
            {"[화면 보여달라고 요청]", "[부모님 화면 보여달라고 부탁하기]"},
            {"[허용/거부]", "[허용하기/거절하기]"},
            {"[중지]", "[그만하기]"},
            {"[연결 해제 요청]", "[연결 끊기 요청]"},
            {"최대 15분 후", "최대 30분 후"}
    };

    private static String relabel(String s) {
        for (String[] p : RELABEL) {
            if (s.contains(p[0])) s = s.replace(p[0], p[1]);
        }
        return s;
    }

    /** 한국어 원문 -> 현재 언어 문구 */
    static String t(String ko) {
        try {
            HashMap<String, String> m = map();
            if (m == null) return ko;
            String v = m.get(ko);
            return v != null ? v : relabel(ko);
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
