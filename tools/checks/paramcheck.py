"""Every parameter line in a signature must end with a comma.

A script that inserts a parameter can silently drop the comma on the
previous line — a syntax error that brace-balance cannot see.

Comment lines (KDoc, block, line) are legal between parameters and carry
no comma, so they are skipped.

**این بررسی تا امروز تقریباً پوچ بود.** الگوی قبلی این بود:

    fun\\s+\\w+\\(\\n((?:[ \\t]+[^\\n)]*\\n)+?)\\)

آخرش `\\)` بی هیچ اجازه‌ای برای فاصله است، یعنی پرانتزِ بسته باید در
**ستونِ صفر** باشد. توابعِ سطحِ فایل این‌طورند، ولی تابعِ عضوِ یک کلاس
تورفتگی دارد:

        fun member(
            a: String
            b: String,
        ) { }

و همین یک نویسهٔ فاصله باعث می‌شد الگو اصلاً نخورَد. یعنی از صدها
تابعِ عضوِ این پروژه — که بیشترِ کد همان‌هاست — هیچ‌کدام دیده نمی‌شد و
بررسی سال‌ها سبزِ بی‌معنا بود.

**و کُند هم بود.** `[ \\t]+` و `[^\\n)]*` هر دو فاصله می‌گیرند، پس برای
هر خط چند راهِ تقسیم هست و موتورِ regex نمایی عقب‌گرد می‌کند. تا وقتی
فایل‌ها کوتاه بودند به چشم نمی‌آمد؛ اولین فایلی که رشتهٔ بلندی از
خط‌های بی‌پرانتز داشت، بررسی را برای همیشه معلق کرد. نگهبانی که
CI را قفل کند به‌اندازهٔ نگهبانِ خاموش بد است.

حالا هر خط با یک کمیت‌سنجِ یکتا گرفته می‌شود (`[^\\n)]*\\n`) و پرانتزِ
بسته حق دارد تورفته باشد.
"""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import re, sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

#: `fun name(` و بعد خط‌هایی که پرانتز ندارند، تا پرانتزِ بستهٔ
#: (شاید تورفتهٔ) پایان.
#:
#: هر خط `[^\n)]*\n` است: یک کمیت‌سنج، پس هیچ ابهامی در تقسیمِ فاصله‌ها
#: نمی‌ماند و عقب‌گردِ نمایی ممکن نیست.
SIG = re.compile(r"fun\s+\w+\(\n((?:[^\n)]*\n)+?)[ \t]*\)")

#: توضیحِ ته‌خط — کاما پیش از آن می‌آید، پس باید کنار گذاشته شود
#: وگرنه «a: String, // یادداشت» هشدارِ نادرست می‌دهد.
TRAILING_COMMENT = re.compile(r"//.*$")

bad = []
scanned = 0
signatures = 0

for path in _src.kt_files():
    src = path.read_text(encoding="utf-8")
    scanned += 1
    for m in SIG.finditer(src):
        signatures += 1
        lines = [
            l for l in m.group(1).split("\n")
            if l.strip() and not l.strip().startswith(("//", "/*", "*"))
        ]
        # آخرین پارامتر حق دارد کاما نداشته باشد
        for l in lines[:-1]:
            t = TRAILING_COMMENT.sub("", l).rstrip()
            if not t:
                continue
            if not t.endswith(",") and not t.endswith("("):
                bad.append((path.name, t.strip()))

if not scanned:
    print("✗ هیچ فایلی اسکن نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

if signatures == 0:
    print("✗ هیچ امضای چندخطی‌ای پیدا نشد — الگو شکسته، بررسی پوچ می‌شود.")
    sys.exit(1)

if bad:
    print(f"✗ {len(bad)} missing comma(s) in a parameter list:")
    for f, l in bad:
        print(f"   {f}: {l}")
    sys.exit(1)

print(f"✓ کاما: {signatures} امضای چندخطی در {scanned} فایل — همه کاما دارند")
