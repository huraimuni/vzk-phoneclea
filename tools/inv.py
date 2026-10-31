import re,sys,glob,json
def tokenize(src):
    # returns list of (start,end,value) for string literals (handles escapes), skipping comments and char literals
    i=0;n=len(src);out=[]
    while i<n:
        c=src[i]
        if src.startswith('//',i):
            j=src.find('\n',i); i=n if j<0 else j; continue
        if src.startswith('/*',i):
            j=src.find('*/',i); i=n if j<0 else j+2; continue
        if c=="'":
            j=i+1
            while src[j]!="'":
                j+= 2 if src[j]=='\\' else 1
            i=j+1; continue
        if c=='"':
            j=i+1;buf=[]
            while src[j]!='"':
                if src[j]=='\\':
                    buf.append(src[j:j+2]); j+=2
                else: buf.append(src[j]); j+=1
            out.append((i,j+1,''.join(buf))); i=j+1; continue
        i+=1
    return out
def chains(src):
    toks=tokenize(src); res=[]; cur=None
    for t in toks:
        if cur and re.fullmatch(r'\s*\+\s*',src[cur[1]:t[0]]):
            cur=(cur[0],t[1],cur[2]+[t[2]])
        else:
            if cur: res.append(cur)
            cur=(t[0],t[1],[t[2]])
    if cur: res.append(cur)
    return res
H=re.compile('[가-힣]')
if __name__=='__main__':
    tot=0;static=0;dyn=0;chars=0
    for f in sorted(glob.glob('app/src/main/java/kr/todanjeon/phoneclean/*.java')):
        src=open(f,encoding='utf-8').read()
        for s,e,parts in chains(src):
            v=''.join(parts)
            if not H.search(v): continue
            tot+=1;chars+=len(v)
            before=src[:s].rstrip()[-1:]; after=src[e:].lstrip()[:1]
            d=(before=='+') or (after=='+')
            if d: dyn+=1
            else: static+=1
    print(tot,static,dyn,chars)
