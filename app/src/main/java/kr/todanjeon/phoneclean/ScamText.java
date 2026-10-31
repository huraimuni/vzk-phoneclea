package kr.todanjeon.phoneclean;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 의심 문자·링크 점검(폰 안에서만 계산, 외부로 보내지 않음). 참고용 점검이며 정확한 판정이 아니다. */
final class ScamText {
    static final class Result {
        int score;
        final List<String> reasons = new ArrayList<>();
        final List<String> links = new ArrayList<>();

        int level() {
            return score >= 7 ? 2 : (score >= 4 ? 1 : 0);
        }
    }

    private static final Pattern URL = Pattern.compile(
            "(?i)((?:https?://)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::\\d+)?(?:/[^\\s)\\]\"'>]*)?)");
    private static final String[] SHORTENERS = {"bit.ly", "me2.do", "vo.la", "url.kr", "han.gl", "t.ly", "tinyurl.com",
            "goo.gl", "is.gd", "buly.kr", "c11.kr", "rb.gy", "cutt.ly", "t.co", "ow.ly", "shorturl.at", "zrr.kr", "naver.me", "kakaolink.com"};
    private static final String[] BAD_TLD = {".xyz", ".top", ".icu", ".vip", ".click", ".work", ".cyou", ".monster", ".sbs", ".cfd", ".live", ".buzz"};
    // 브랜드 이름이 들어 있는데 공식 도메인이 아니면 사칭 의심
    private static final String[][] BRANDS = {
            {"cj", "cjlogistics.com", "cj.net"}, {"hanjin", "hanjin.com", "hanjin.co.kr"}, {"lotte", "lotteglogis.com", "lotte.com"},
            {"epost", "epost.go.kr"}, {"kbstar", "kbstar.com"}, {"kakaobank", "kakaobank.com"}, {"kakao", "kakao.com", "kakaocorp.com", "kakaopay.com", "kakaobank.com"},
            {"naver", "naver.com", "naver.me", "navercorp.com"}, {"toss", "toss.im", "tossbank.com", "tosspayments.com"},
            {"shinhan", "shinhan.com"}, {"nonghyup", "nonghyup.com", "nhbank.com"}, {"hanabank", "hanabank.com"}, {"wooribank", "wooribank.com"},
            {"hometax", "hometax.go.kr"}, {"nhis", "nhis.or.kr"}, {"coupang", "coupang.com"}, {"police", "police.go.kr"}, {"spo", "spo.go.kr"},
            {"samsung", "samsung.com"}, {"kftc", "kftc.or.kr"}, {"fss", "fss.or.kr"}};

    private static final String[][] WORDS = {
            {"택배·배송 사칭", "주소 불명|주소지 불일치|주소 확인|배송 보류|배송 불가|미배송|반송|택배 보관|물품 보관"},
            {"수사·기관 사칭", "검찰|경찰|수사관|출석|범죄 연루|금융감독원|금감원|계좌 동결|명의 도용|사건 번호"},
            {"대출·투자 권유", "대출 승인|저금리|한도 조회|무방문|당일 대출|투자 수익|리딩방|원금 보장|고수익"},
            {"결제 사칭", "해외 결제|결제 승인|결제 완료|본인이 아닐 경우|본인 아니면|카드 발급|자동 결제"},
            {"경조사 사칭", "부고|청첩장|돌잔치|모바일 초대장|조문"},
            {"세금·환급 사칭", "환급|국세청|건강보험|미납|체납|과태료|범칙금|교통 위반"},
            {"가족·지인 사칭", "엄마|아빠|폰 고장|액정|새 번호|번호 바뀌|문화상품권|핸드폰이 고장|인증 좀|대신 결제"},
            {"인증정보 요구", "인증번호|OTP|보안카드|비밀번호|주민번호|계좌번호 알려"},
            {"링크·앱 설치 유도", "아래 링크|링크 클릭|클릭하세요|확인하세요|앱 설치|설치하세요|접속하세요|바로가기"}};

    private ScamText() {
    }

    static Result analyze(String text) {
        Result r = new Result();
        if (text == null) return r;
        String t = text.trim();
        Matcher m = URL.matcher(t);
        boolean anyUrl = false;
        while (m.find()) {
            String u = m.group(1);
            if (u.length() < 4) continue;
            anyUrl = true;
            r.links.add(u);
            check(u, r);
        }
        int cats = 0;
        for (String[] w : WORDS) {
            if (Pattern.compile(w[1]).matcher(t).find()) {
                cats++;
                r.score += 2;
                r.reasons.add(w[0] + " 표현이 들어 있습니다.");
            }
        }
        if (anyUrl && cats > 0) r.score += 1;
        if (!anyUrl && cats == 0 && t.length() > 0) r.reasons.add("링크와 대표적인 사기 문구가 발견되지 않았습니다.");
        return r;
    }

    private static void check(String url, Result r) {
        String low = url.toLowerCase();
        String noScheme = low.replaceFirst("^https?://", "");
        String host = noScheme.split("[/?#]")[0].replaceFirst(":\\d+$", "");
        String path = noScheme.length() > host.length() ? noScheme.substring(host.length()) : "";
        for (String s : SHORTENERS) {
            if (host.equals(s) || host.endsWith("." + s)) {
                r.score += 3;
                r.reasons.add("단축 주소(" + host + ")라서 실제 도착지를 알 수 없습니다.");
                break;
            }
        }
        if (path.contains(".apk")) {
            r.score += 5;
            r.reasons.add("앱 설치 파일(.apk)을 받게 하는 링크입니다. 사기 문자의 전형적인 수법입니다.");
        }
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            r.score += 4;
            r.reasons.add("숫자 주소(IP)로 연결되는 링크입니다.");
        }
        for (String tld : BAD_TLD) {
            if (host.endsWith(tld)) {
                r.score += 2;
                r.reasons.add("사기 사이트에 자주 쓰이는 주소 끝(" + tld + ")입니다.");
                break;
            }
        }
        if (low.contains("@")) {
            r.score += 2;
            r.reasons.add("주소 안에 '@'가 있어 다른 곳으로 속이는 형태일 수 있습니다.");
        }
        if (host.length() > 35) {
            r.score += 1;
            r.reasons.add("주소가 비정상적으로 깁니다.");
        }
        String[] parts = host.split("[.\\-]");
        for (String[] b : BRANDS) {
            String token = b[0];
            boolean has = token.length() >= 5 ? host.contains(token) : contains(parts, token);
            if (!has) continue;
            boolean official = false;
            for (int i = 1; i < b.length; i++) {
                if (host.equals(b[i]) || host.endsWith("." + b[i])) official = true;
            }
            if (host.endsWith(".go.kr") && (token.equals("police") || token.equals("spo") || token.equals("hometax"))) official = true;
            if (!official) {
                r.score += 4;
                r.reasons.add("'" + token + "' 이름을 따라 한 비공식 주소(" + host + ")로 보입니다.");
                break;
            }
        }
    }

    private static boolean contains(String[] parts, String token) {
        for (String p : parts) if (p.equals(token)) return true;
        return false;
    }
}
