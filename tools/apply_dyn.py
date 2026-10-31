import sys,re,json
sys.path.insert(0,'tools/tr')
import dyn
def jl(s): return '"'+s.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n')+'"'
keys=[];miss=[]
for f,old,new,ks in dyn.E:
    p=f'app/src/main/java/kr/todanjeon/phoneclean/{f}.java'
    try: src=open(p,encoding='utf-8').read()
    except FileNotFoundError: continue
    if src.count(old)!=1:
        # allow already-applied
        miss.append((f,old[:60],src.count(old))); continue
    out=new
    for i,k in enumerate(ks): out=out.replace('@%d'%i,jl(k))
    open(p,'w',encoding='utf-8').write(src.replace(old,out))
for f,old,new,ks in dyn.E: keys+=list(ks)
json.dump(sorted(set(keys)),open('/tmp/s2/dynkeys.json','w'),ensure_ascii=False)
print('keys',len(set(keys)),'miss',miss)
