import sys,re,glob,json
sys.path.insert(0,'tools')
from inv import chains,H
items=[]
for f in sorted(glob.glob('app/src/main/java/kr/todanjeon/phoneclean/*.java')):
    src=open(f,encoding='utf-8').read()
    for s,e,parts in chains(src):
        v=''.join(parts)
        if not H.search(v): continue
        before=src[:s].rstrip()[-1:]; after=src[e:].lstrip()[:1]
        dyn=(before=='+') or (after=='+')
        line=src.count('\n',0,s)+1
        # decode java escapes minimal
        items.append(dict(file=f.split('/')[-1],line=line,start=s,end=e,dyn=dyn,text=v))
json.dump(items,open('/tmp/s2/items.json','w'),ensure_ascii=False)
for i,it in enumerate(items):
    if not it['dyn']: print(f"{i}|{it['file'][:-5]}:{it['line']}|{it['text']}")
