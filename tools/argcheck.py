import re,glob,sys
D=sys.argv[1].rstrip('/')+'/'
files={f:open(f).read() for f in glob.glob(D+'*.kt')}
def split_top(s):
    parts=[];depth=0;cur='';instr=False;i=0
    while i<len(s):
        c=s[i]
        if c=='"' and (i==0 or s[i-1]!='\\'): instr=not instr
        if not instr:
            if c in '([{': depth+=1
            elif c in ')]}': depth-=1
            elif c==',' and depth==0: parts.append(cur);cur='';i+=1;continue
        cur+=c;i+=1
    if cur.strip(): parts.append(cur)
    return [p.strip() for p in parts]
def match_paren(s,i):
    depth=0;instr=False
    while i<len(s):
        c=s[i]
        if c=='"' and s[i-1]!='\\': instr=not instr
        if not instr:
            if c=='(': depth+=1
            elif c==')':
                depth-=1
                if depth==0: return i
        i+=1
    return -1
defs={}
for f,src in files.items():
    for m in re.finditer(r'\bfun\s+(?:Modifier\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(',src):
        name=m.group(1); end=match_paren(src,m.end()-1)
        plist=[]
        for p in split_top(src[m.end():end]):
            mm=re.match(r'(?:val |var )?([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.+?)(?:\s*=\s*(.+))?$',p,re.S)
            if mm: plist.append((mm.group(1),mm.group(3) is not None,mm.group(2).strip()))
        defs.setdefault(name,[]).append((f,plist))
mine=[n for n,v in defs.items() if any(f.endswith('Ui.kt') for f,_ in v)]+['SettingRow','TodayTab','InsightsTab','ControlsTab','Onboarding','ConsentGate','DisclosureBody','Shell','Splash']
problems=0;checked=0
for f,src in files.items():
    for name in set(mine):
        if name not in defs: continue
        for m in re.finditer(r'(?<![A-Za-z0-9_.])'+name+r'\s*\(',src):
            if re.search(r'fun\s+(Modifier\.)?$',src[max(0,m.start()-12):m.start()]): continue
            end=match_paren(src,m.end()-1)
            if end<0: continue
            args=split_top(src[m.end():end])
            trailing=src[end+1:end+40].lstrip().startswith('{')
            cands=[d for d in defs[name] if d[0]==f] or defs[name]
            plist=cands[0][1]; names=[p[0] for p in plist]
            pos=0;named=set();bad=[]
            for a in args:
                mm=re.match(r'([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)',a)
                if mm and mm.group(1) in names: named.add(mm.group(1))
                elif mm: bad.append('unknown named arg '+mm.group(1))
                else:
                    if named: bad.append('positional after named')
                    pos+=1
            supplied=set(names[:pos])|named
            if trailing:
                lam=[p[0] for p in plist if '->' in p[2] or p[0]=='content']
                if lam: supplied.add(lam[-1])
            missing=[p[0] for p in plist if not p[1] and p[0] not in supplied]
            if pos>len(plist): bad.append('too many positional args')
            if missing: bad.append('missing '+','.join(missing))
            checked+=1
            if bad:
                problems+=1
                print(f"{f.split('/')[-1]}:{src[:m.start()].count(chr(10))+1} {name}({', '.join(a[:25] for a in args)}) -> {bad}")
print("calls checked:",checked,"problems:",problems)
