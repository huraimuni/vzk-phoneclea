import sys,json,os,re,glob
sys.path.insert(0,'tools/tr'); sys.path.insert(0,'tools')
LANGS=['en','ja','es','pt-BR','id','hi']
def dec(s):
    out=[];i=0
    while i<len(s):
        c=s[i]
        if c=='\\' and i+1<len(s):
            n=s[i+1]
            if n=='n': out.append('\n');i+=2
            elif n=='t': out.append('\t');i+=2
            elif n in '"\'\\': out.append(n);i+=2
            elif n=='u': out.append(chr(int(s[i+2:i+6],16)));i+=6
            else: out.append(c);i+=1
        else: out.append(c);i+=1
    return ''.join(out)
def load_idx(items):
    res={}  # korean -> [6]
    idxs={}
    for f in sorted([x for x in glob.glob('tools/tr/p[0-9].py') if not x.endswith(('p7.py','p8.py','p9.py'))]):
        m=__import__(os.path.basename(f)[:-3])
        for k,v in m.T.items():
            ko=dec(items[k]['text'])
            assert not items[k]['dyn'],(k,'dyn')
            res[ko]=[dec(x) for x in v]; idxs[k]=ko
    return res,idxs
def load_extra():
    res={}
    for n in ('p7','p8','p9'):
        try:
            m=__import__(n)
            for ko,v in m.T.items(): res[ko]=list(v)
        except ImportError: pass
    return res
if __name__=='__main__':
    items=json.load(open('/tmp/s2/items.json'))
    res,idxs=load_idx(items)
    res.update(load_extra())
    FIX=[('6-digit','8-digit'),('6桁','8桁'),('6 dígitos','8 dígitos'),('6 digit','8 digit'),('6 अंकों','8 अंकों'),('6자리','8자리')]
    def fx(t):
        for a,b in FIX: t=t.replace(a,b)
        return t
    res={(fx(k) if k.startswith('이 앱은 무엇을') else k):([fx(x) for x in v] if k.startswith('이 앱은 무엇을') else v) for k,v in res.items()}
    os.makedirs('app/src/main/assets/tr',exist_ok=True)
    for li,l in enumerate(LANGS):
        d={ko:v[li] for ko,v in res.items()}
        json.dump(d,open(f'app/src/main/assets/tr/{l}.json','w',encoding='utf-8'),ensure_ascii=False,indent=0)
    print('entries',len(res))
    json.dump(sorted(res.keys()),open('/tmp/s2/keys.json','w'),ensure_ascii=False)
    miss=[i for i,x in enumerate(items) if not x['dyn'] and i not in idxs]
    print('static untranslated',len(miss),miss)
