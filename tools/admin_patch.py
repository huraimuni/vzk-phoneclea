import re,io
def edit(p,pairs,must=True):
    s=open(p,encoding='utf-8').read()
    for o,n in pairs:
        if o not in s:
            if must: raise SystemExit('missing in %s: %s'%(p,o[:60]))
            continue
        s=s.replace(o,n,1) if s.count(o)==1 else s.replace(o,n)
    open(p,'w',encoding='utf-8').write(s)
J='app/src/main/java/kr/todanjeon/phoneclean/'
edit('app/src/main/AndroidManifest.xml',[('        <activity\n            android:name=".FamilyActivity"','        <activity\n            android:name=".AdminActivity"\n            android:exported="false" />\n        <activity\n            android:name=".FamilyActivity"')])
edit(J+'FamilyActivity.java',[('String.format("%06d", r.nextInt(1000000))','String.format("%08d", r.nextInt(100000000))'),('c.length() != 6','c.length() != 8')])
edit(J+'MainActivity.java',[
 ('6자리 코드와 QR','8자리 코드와 QR'),
 ('        resumed = true;\n','        resumed = true;\n        Admin.refresh();\n'),
 ('        final String[] items = {','        final String[] base = {'),
 ('Tr.t("도움말")};\n        new AlertDialog.Builder(this)\n                .setTitle(Tr.t("더보기"))',
  'Tr.t("도움말")};\n        final boolean adm = Admin.is;\n        final String[] items = new String[base.length + (adm ? 1 : 0)];\n        for (int i = 0; i < base.length; i++) items[i + (adm ? 1 : 0)] = base[i];\n        if (adm) items[0] = "👑 관리자";\n        new AlertDialog.Builder(this)\n                .setTitle(Tr.t("더보기"))'),
 ('                    public void onClick(DialogInterface dialog, int which) {\n                        if (which == 0) startActivity(new Intent(MainActivity.this, Auth.uid() == null',
  '                    public void onClick(DialogInterface dialog, int which) {\n                        if (adm) {\n                            if (which == 0) {\n                                startActivity(new Intent(MainActivity.this, AdminActivity.class));\n                                return;\n                            }\n                            which = which - 1;\n                        }\n                        if (which == 0) startActivity(new Intent(MainActivity.this, Auth.uid() == null'),
 ('else if (which == items.length - 2) showFeedback();','else if (which == base.length - 2) showFeedback();'),
],must=True)
edit('tools/strings_data.py',[("'Invite code (6 digits)', '초대 코드 (6자리)', '招待コード（6桁）', 'Código de invitación (6 dígitos)', 'Código de convite (6 dígitos)', 'Kode undangan (6 digit)'","'Invite code (8 digits)', '초대 코드 (8자리)', '招待コード（8桁）', 'Código de invitación (8 dígitos)', 'Código de convite (8 dígitos)', 'Kode undangan (8 digit)'")],must=False)
