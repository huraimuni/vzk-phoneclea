import sys,json,glob
sys.path.insert(0,'tools')
from inv import chains,H
from gen_tr import dec
keys=set(json.load(open('/tmp/s2/keys.json')))
n=0
for f in sorted(glob.glob('app/src/main/java/kr/todanjeon/phoneclean/*.java')):
    if f.endswith(('Tr.java','Lang.java','CrashLog.java')): continue
    src=open(f,encoding='utf-8').read()
    ch=chains(src); out=src; cnt=0
    for s,e,parts in reversed(ch):
        v=''.join(parts)
        if not H.search(v): continue
        before=src[:s].rstrip()[-1:]; after=src[e:].lstrip()[:1]
        if before=='+' or after=='+': continue
        if src[:s].rstrip().endswith('Tr.t(') or src[:s].endswith('Tr.t('): continue
        if dec(v) not in keys: continue
        out=out[:s]+'Tr.t('+out[s:e]+')'+out[e:]; cnt+=1
    if cnt:
        open(f,'w',encoding='utf-8').write(out); n+=cnt
print('wrapped',n)
