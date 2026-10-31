p='app/src/main/java/kr/todanjeon/phoneclean/MainActivity.java'
s=open(p,encoding='utf-8').read()
def rep(o,n):
    global s
    assert s.count(o)==1,(o[:50],s.count(o)); s=s.replace(o,n)
rep('for (Button b : new Button[]{del, safeBtn, info2, aiBtn, naver, google}) {',
    'Button[] btns = Lang.isKoreaUser(this)\n                ? new Button[]{del, safeBtn, info2, aiBtn, naver}\n                : new Button[]{del, safeBtn, info2, aiBtn, google};\n        for (Button b : btns) {')
rep('Button aiBtn = Ui.button(this, Tr.t("AI에게 물어보기 (질문 복사 + 네이버 검색)"),',
    'Button aiBtn = Ui.button(this, Lang.isKoreaUser(this) ? Tr.t("AI에게 물어보기 (질문 복사 + 네이버 검색)") : Tr.t("AI에게 물어보기 (질문 복사 + 구글 검색)"),')
rep('Ui.toast(this, Tr.t("네이버 검색으로 연결합니다. (질문도 복사해 두었습니다)"));',
    'Ui.toast(this, Lang.isKoreaUser(this) ? Tr.t("네이버 검색으로 연결합니다. (질문도 복사해 두었습니다)") : Tr.t("구글 검색으로 연결합니다. (질문도 복사해 두었습니다)"));')
rep('        try {\n            String sq = "앱 " + e.label',
    '        if (!Lang.isKoreaUser(this)) {\n            Ui.openUrl(this, "https://www.google.com/search?q=" + Uri.encode(e.label + " " + e.pkg + " safe to delete?"));\n            return;\n        }\n        try {\n            String sq = "앱 " + e.label')
open(p,'w',encoding='utf-8').write(s)
