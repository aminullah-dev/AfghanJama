"""وقتی به یک تابع پارامترِ تازه با مقدارِ پیش‌فرض اضافه می‌کنیم، خطر این
است که یک فراخوانیِ قدیمی آرگومانِ جایگاهی داشته باشد که حالا به جایگاهِ
پارامترِ تازه بنشیند. آرگومانِ نام‌دار این خطر را ندارد."""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import re, glob, sys
import sys as _s2, pathlib as _p2
_s2.path.insert(0, str(_p2.Path(__file__).resolve().parent))
import _src

SIG = {"drawHeader":7,"drawFooter":6,"rule":3,"section":5,"kv":7,
       "tableHeader":6,"tableRow":7,"totalBox":6,"signatures":6,"note":5,
       "boxedNote":5,"drawCells":6}
RECV = "PdfKit."
NAMED = re.compile(r'^\s*[A-Za-z_]\w*\s*=(?!=)')
bad=[]
for path in [str(x) for x in _src.kt_files()]:
    src=open(path).read()
    for fn,pos in SIG.items():
        for m in re.finditer(re.escape(RECV)+fn+r'\s*\(', src):
            i=m.end(); depth=1; instr=False; start=i; args=[]
            while i<len(src):
                ch=src[i]
                if instr:
                    if ch=='\\': i+=2; continue
                    if ch=='"': instr=False
                elif ch=='"': instr=True
                elif ch in '([{': depth+=1
                elif ch in ')]}':
                    depth-=1
                    if depth==0: args.append(src[start:i]); break
                elif ch==',' and depth==1:
                    args.append(src[start:i]); start=i+1
                i+=1
            positional=[a for a in args if not NAMED.match(a)]
            if len(positional)>=pos:
                # پر کردنِ عمدیِ همان جایگاه با خودِ paper مشکلی نیست؛
                # خطر آنجاست که چیزِ دیگری در آن جایگاه بنشیند.
                filler=positional[pos-1].strip()
                if filler=='paper': continue
                line=src[:m.start()].count('\n')+1
                bad.append(f"{path}:{line} {RECV}{fn} — slot {pos} (paper) filled with `{filler}`")
print('\n'.join(bad) if bad else "✓ no positional argument lands in a newly added parameter slot")
sys.exit(1 if bad else 0)
