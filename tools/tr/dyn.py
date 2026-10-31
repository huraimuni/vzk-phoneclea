# (file, old_java, new_java_template_with_{K1} placeholders, [ko keys])  — see apply_dyn.py
# Each edit: file, old (exact java), new (java using Tr.* with KEY(n) refs), keys list
E = []
def e(f, old, new, *keys): E.append((f, old, new, keys))

# ---- Alerts
e('Alerts', '"새 앱이 설치됐어요: " + e.label', 'Tr.f(@0, e.label)', '새 앱이 설치됐어요: %s')
# ---- Analysis
e('AnalysisActivity', '"판정: " + e.levelText()\n                + (e.tagText()', 'Tr.t(@0) + e.levelText()\n                + (e.tagText()', '판정: ')
e('AnalysisActivity', '"설치 경로: " + e.installerText + "\\n설치 시각: " + date', 'Tr.t(@0) + e.installerText + Tr.t(@1) + date', '설치 경로: ', '\n설치 시각: ')
# ---- Main
e('MainActivity', '"앱을 분석하는 중입니다... " + Scanner.progressDone', 'Tr.t(@0) + Scanner.progressDone', '앱을 분석하는 중입니다... ')
e('MainActivity', '"분석 중 오류가 났습니다: " + ex.getClass()', 'Tr.t(@0) + ex.getClass()', '분석 중 오류가 났습니다: ')
e('MainActivity', '+ "\\n[전체 앱 보기]를 눌러 다시 시도해 보세요.")', '+ Tr.t(@0))', '\n[전체 앱 보기]를 눌러 다시 시도해 보세요.')
e('MainActivity', '"직접 설치한 앱 " + all.size() + "개 중  위험 의심 " + high + "개 / 주의 " + warn + "개"', 'Tr.f(@0, all.size(), high, warn)', '직접 설치한 앱 %d개 중 위험 의심 %d개 / 주의 %d개')
e('MainActivity', 'Tr.t("2단계 ▸ 선택한 앱 삭제") : "2단계 ▸ 선택한 앱 " + n + "개 삭제"', 'Tr.t("2단계 ▸ 선택한 앱 삭제") : Tr.f(@0, n)', '2단계 ▸ 선택한 앱 %d개 삭제')
e('MainActivity', 'date + " 설치" +', 'date + Tr.t(@0) +', ' 설치')
e('MainActivity', '.append("\\n외 ").append(e.reasons.size() - 2).append("건")', '.append(Tr.f(@0, e.reasons.size() - 2))', '\n외 %d건')
e('MainActivity', '''.setMessage("직접 설치한 앱 " + pkgs.size() + "개의 백그라운드 실행을 멈추게 했습니다. "
                        + "(시스템 앱, 안전 표시한 앱, 키보드·홈 화면·문자·전화 앱은 제외)\\n\\n"
                        + "폰이 조금 가벼워졌는지 확인해 보세요. 이건 임시 조치라서, 앱이 다시 켜질 수 있습니다. "
                        + "카톡 같은 앱은 새 알림이 오면 다시 켜집니다. 다시 느려지면 한 번 더 누르세요.\\n\\n"
                        + "다음 단계로 지울 앱을 고르시겠어요?")''',
  '.setMessage(Tr.f(@0, pkgs.size()))',
  '직접 설치한 앱 %d개의 백그라운드 실행을 멈추게 했습니다. (시스템 앱, 안전 표시한 앱, 키보드·홈 화면·문자·전화 앱은 제외)\n\n폰이 조금 가벼워졌는지 확인해 보세요. 이건 임시 조치라서, 앱이 다시 켜질 수 있습니다. 카톡 같은 앱은 새 알림이 오면 다시 켜집니다. 다시 느려지면 한 번 더 누르세요.\n\n다음 단계로 지울 앱을 고르시겠어요?')
e('MainActivity', 'pkgs.size() + "개 앱을 " + (safe ? Tr.t("안전한 앱으로 표시했습니다.") : Tr.t("안전 표시에서 뺐습니다."))',
  'Tr.f(safe ? @0 : @1, pkgs.size())', '%d개 앱을 안전한 앱으로 표시했습니다.', '%d개 앱을 안전 표시에서 뺐습니다.')
e('MainActivity', '.setTitle(pkgs.size() + "개를 안전한 앱으로 표시할까요?")', '.setTitle(Tr.f(@0, pkgs.size()))', '%d개를 안전한 앱으로 표시할까요?')
e('MainActivity', '''String q = "안드로이드 앱 '" + e.label + "' (패키지명: " + e.pkg + ")이 안전한 앱인가요? "
                + "유튜브 광고를 누르다가 설치된 것 같은데, 지워도 되는지 알려 주세요.";''',
  'String q = Tr.f(@0, e.label, e.pkg);', "안드로이드 앱 '%s' (패키지명: %s)이 안전한 앱인가요? 유튜브 광고를 누르다가 설치된 것 같은데, 지워도 되는지 알려 주세요.")
e('MainActivity', '"홈 화면이 폰 기본 앱이 아닌 [" + name + "](으)로 바뀌어 있습니다.\\n눌러서 기본 홈으로 되돌리기"', 'Tr.f(@0, name)', '홈 화면이 폰 기본 앱이 아닌 [%s](으)로 바뀌어 있습니다.\n눌러서 기본 홈으로 되돌리기')
e('MainActivity', '"판정: " + e.levelText() + (e.tagText().isEmpty() ? "" : "  ("', 'Tr.t(@0) + e.levelText() + (e.tagText().isEmpty() ? "" : "  ("', '판정: ')
e('MainActivity', '"설치일: " + date + "\\n설치 경로: " + e.installerText + "\\n패키지: " + e.pkg', 'Tr.t(@0) + date + Tr.t(@1) + e.installerText + Tr.t(@2) + e.pkg', '설치일: ', '\n설치 경로: ', '\n패키지: ')
e('MainActivity', '.setTitle(pkgs.size() + "개 앱을 삭제할까요?")', '.setTitle(Tr.f(@0, pkgs.size()))', '%d개 앱을 삭제할까요?')
e('MainActivity', '''+ "\\n앱마다 안드로이드의 삭제 확인 창이 하나씩 뜹니다. 그때마다 [확인]을 눌러 주세요.\\n"
                        + "내가 쓰는 앱이 섞여 있지 않은지 한 번 더 보세요.")''',
  '+ Tr.t(@0))', '\n앱마다 안드로이드의 삭제 확인 창이 하나씩 뜹니다. 그때마다 [확인]을 눌러 주세요.\n내가 쓰는 앱이 섞여 있지 않은지 한 번 더 보세요.')
e('MainActivity', '.setTitle("삭제되지 않은 앱 " + bad.size() + "개")', '.setTitle(Tr.f(@0, bad.size()))', '삭제되지 않은 앱 %d개')
e('MainActivity', '''+ "\\n삭제 확인 창에서 [취소]를 눌렀거나, 앱이 스스로를 지키고 있는 경우입니다.\\n\\n"
                        + "[기기 관리자]나 [접근성]으로 등록된 앱은 그 권한을 먼저 끄고 나서 삭제해야 합니다.\\n"
                        + "1) 아래 [기기 관리자 설정]이나 [접근성 설정]에서 해당 앱을 끄기\\n"
                        + "2) 앱 정보를 열어 [삭제] 누르기\\n"
                        + "그래도 안 지워지면 폰을 안전 모드로 켜서 삭제해 보세요.")''',
  '+ Tr.t(@0))', '\n삭제 확인 창에서 [취소]를 눌렀거나, 앱이 스스로를 지키고 있는 경우입니다.\n\n[기기 관리자]나 [접근성]으로 등록된 앱은 그 권한을 먼저 끄고 나서 삭제해야 합니다.\n1) 아래 [기기 관리자 설정]이나 [접근성 설정]에서 해당 앱을 끄기\n2) 앱 정보를 열어 [삭제] 누르기\n그래도 안 지워지면 폰을 안전 모드로 켜서 삭제해 보세요.')
e('MainActivity', '.setTitle("VZK 폰클린 v" + BuildConfig.VERSION_NAME)', '.setTitle(Tr.f(@0, BuildConfig.VERSION_NAME))', 'VZK 폰클린 v%s')
# ---- Notif
e('NotifActivity', '"앱 " + counts.size() + "개에서 알림 " + total + "개가 쌓여 있습니다."', 'Tr.f(@0, counts.size(), total)', '앱 %d개에서 알림 %d개가 쌓여 있습니다.')
e('NotifActivity', '"  (" + en.getValue() + "개)"', 'Tr.f(@0, en.getValue())', '  (%d개)')
e('NotifActivity', 'n + "개를 비웠습니다."', 'Tr.f(@0, n)', '%d개를 비웠습니다.')
# ---- Scanner
e('Scanner', '"다른 설치 프로그램 (" + installer + ")"', 'Tr.f(@0, installer)', '다른 설치 프로그램 (%s)')
e('Scanner', '"플레이스토어·갤럭시 스토어가 아닌 곳에서 설치됨 [" + e.installerText + "]"', 'Tr.f(@0, e.installerText)', '플레이스토어·갤럭시 스토어가 아닌 곳에서 설치됨 [%s]')
e('Scanner', '"광고 프로그램(SDK)이 " + e.adCount + "종 들어 있음"', 'Tr.f(@0, e.adCount)', '광고 프로그램(SDK)이 %d종 들어 있음')
e('Scanner', '"비슷한 시기에 앱이 " + near + "개 몰려서 설치됨"', 'Tr.f(@0, near)', '비슷한 시기에 앱이 %d개 몰려서 설치됨')
e('Scanner', '"같은 개발사(서명)가 만든 앱이 " + l.size() + "개 설치돼 있음"', 'Tr.f(@0, l.size())', '같은 개발사(서명)가 만든 앱이 %d개 설치돼 있음')
# ---- Storage
e('StorageActivity', '"저장공간  사용 " + fmt(used) + " / 전체 " + fmt(total) + "  (남은 " + fmt(free) + ")"', 'Tr.f(@0, fmt(used), fmt(total), fmt(free))', '저장공간  사용 %s / 전체 %s  (남은 %s)')
# ---- Dup contacts
e('DupContactsActivity', '"중복 묶음 " + (gi + 1) + "  (" + g.size() + "명)"', 'Tr.f(@0, gi + 1, g.size())', '중복 묶음 %d  (%d명)')
e('DupContactsActivity', '"중복 " + groups.size() + "묶음 (연락처 " + countItems() + "건)"', 'Tr.f(@0, groups.size(), countItems())', '중복 %d묶음 (연락처 %d건)')
e('DupContactsActivity', ': "선택한 " + n + "건 삭제"', ': Tr.f(@0, n)', '선택한 %d건 삭제')
e('DupContactsActivity', '.setTitle(checked.size() + "건을 삭제할까요?")', '.setTitle(Tr.f(@0, checked.size()))', '%d건을 삭제할까요?')
e('DupContactsActivity', '''+ "\\n삭제하면 되돌릴 수 없고, 그 연락처에만 있던 메모·다른 번호도 함께 사라집니다.\\n"
                        + "남길 연락처에 필요한 정보가 다 들어 있는지 확인하세요.")''',
  '+ Tr.t(@0))', '\n삭제하면 되돌릴 수 없고, 그 연락처에만 있던 메모·다른 번호도 함께 사라집니다.\n남길 연락처에 필요한 정보가 다 들어 있는지 확인하세요.')
e('DupContactsActivity', 'ok + "건 삭제" + (fail > 0 ? ", " + fail + "건 실패" : "") + ". 다시 찾아봅니다."',
  'Tr.f(fail > 0 ? @0 : @1, ok, fail)', '%1$d건 삭제, %2$d건 실패. 다시 찾아봅니다.', '%1$d건 삭제. 다시 찾아봅니다.')
# ---- Dup media
e('DupMediaActivity', '"같은 파일인지 비교하는 중... " + done', 'Tr.t(@0) + done', '같은 파일인지 비교하는 중... ')
e('DupMediaActivity', '(first.video ? Tr.t("영상 ") : Tr.t("사진 ")) + (gi + 1) + "묶음 · " + g.size() + "개 · 파일 하나당 "\n                    + StorageActivity.fmt(first.size)',
  'Tr.f(first.video ? @0 : @1, gi + 1, g.size(), StorageActivity.fmt(first.size))', '영상 %d묶음 · %d개 · 파일 하나당 %s', '사진 %d묶음 · %d개 · 파일 하나당 %s')
e('DupMediaActivity', '"중복 " + groups.size() + "묶음 · 모두 정리하면 약 " + StorageActivity.fmt(waste) + " 확보"', 'Tr.f(@0, groups.size(), StorageActivity.fmt(waste))', '중복 %d묶음 · 모두 정리하면 약 %s 확보')
e('DupMediaActivity', ': "선택한 " + n + "개 휴지통으로"', ': Tr.f(@0, n)', '선택한 %d개 휴지통으로')
e('DupMediaActivity', '.setTitle(checked.size() + "개를 휴지통으로 보낼까요?")', '.setTitle(Tr.f(@0, checked.size()))', '%d개를 휴지통으로 보낼까요?')
e('DupContactsActivity', 'names.append(Tr.t("외 ")).append(shown - 8).append(Tr.t("건\\n"))', 'names.append(Tr.f(@0, shown - 8))', '외 %d건\n')
e('MainActivity', 'names.append(Tr.t("외 ")).append(shownCount - 8).append(Tr.t("개\\n"))', 'names.append(Tr.f(@0, shownCount - 8))', '외 %d개\n')
