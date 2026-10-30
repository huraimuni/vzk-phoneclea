# -*- coding: utf-8 -*-
"""번역 문자열 생성기: python3 tools/gen_strings.py  → app/src/main/res/values*/strings.xml
언어 순서: en(기본), ko, ja, es, pt-BR(pt-rBR), id(in), hi
새 문구는 S 에 한 줄씩 추가한다. (%1$s 같은 서식 기호는 모든 언어에서 동일하게 유지)"""
import os, html
LANGS = ['en', 'ko', 'ja', 'es', 'pt', 'id', 'hi']
DIRS = {'en': 'values', 'ko': 'values-ko', 'ja': 'values-ja', 'es': 'values-es',
        'pt': 'values-pt-rBR', 'id': 'values-in', 'hi': 'values-hi'}
S = {}
def add(key, *t):
    assert len(t) == 7, key
    S[key] = dict(zip(LANGS, t))

exec(open(os.path.join(os.path.dirname(__file__), 'strings_data.py'), encoding='utf-8').read())

root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
for l in LANGS:
    d = os.path.join(root, DIRS[l]); os.makedirs(d, exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for k, v in S.items():
        s = v[l].replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('&', '&amp;').replace('<', '&lt;')
        out.append('    <string name="%s">%s</string>' % (k, s))
    out.append('</resources>')
    open(os.path.join(d, 'strings.xml'), 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print(len(S), 'strings x', len(LANGS), 'languages')
