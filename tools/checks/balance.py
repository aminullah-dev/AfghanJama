#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""توازنِ پرانتز و آکولاد — ارزان‌ترین نگهبانِ نحو.

**چرا هست.** بارِ اول که بلوکی را با اسکریپت از فایلی بریدم، یک `)`
اضافه جا ماند. هر ۵۲ بررسی سبز ماندند — هیچ‌کدام نحو را نمی‌سنجیدند —
و CI دو دقیقه بعد گفت:

    Theme.kt:41:1 Expecting a top level declaration

آن دو دقیقه ارزانی است ولی رایگان نیست، و اشکال از جنسی است که در یک
ثانیه اینجا پیدا می‌شود.

**این جای کامپایلر را نمی‌گیرد** و چنین ادعایی هم ندارد: فقط
نامتوازنی می‌گیرد. کدی که پرانتزش موازن است هزار جور دیگر می‌تواند
خراب باشد. ولی برشِ ماشینیِ متن دقیقاً همین یک اشتباه را می‌کند.
"""
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

def code_only(text):
    """رشته‌ها و توضیح‌ها بیرون — پرانتزِ داخلشان نحو نیست.

    **پویشگر، نه زنجیرهٔ regex.** نسخهٔ اول توضیح‌ها را پیش از رشته‌ها
    برمی‌داشت، پس `//` در `URL("http://$host…")` یک توضیح شمرده می‌شد و
    بقیهٔ خط — با پرانتزِ بسته‌اش — پاک. دو فایلِ سالم قرمز شدند.

    ترتیب را برعکس کردن هم درمان نیست: نقلِ قولی داخلِ یک توضیح آن‌وقت
    آغازِ رشته حساب می‌شد. این دو حالت با regex در هم می‌روند؛ با یک
    پویشِ حرف‌به‌حرف نه.
    """
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if two == "//":
            i = text.find("\n", i)
            if i < 0:
                break
        elif two == "/*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif text[i:i + 3] == '"""':
            j = text.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif c == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif c == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


bad = []
for path in _src.kt_files():
    t = code_only(path.read_text(encoding="utf-8", errors="replace"))
    for open_c, close_c, name in (("(", ")", "پرانتز"), ("{", "}", "آکولاد"),
                                  ("[", "]", "کروشه")):
        diff = t.count(open_c) - t.count(close_c)
        if diff:
            more = "باز" if diff > 0 else "بسته"
            bad.append(f"{path.name}: {abs(diff)} {name}ِ {more}ِ اضافه")

if bad:
    print(f"✗ {len(bad)} نامتوازنی:")
    for b in bad:
        print(f"  • {b}")
    sys.exit(1)

print(f"✓ توازن: {len(_src.kt_files())} فایلِ کاتلین — پرانتز، آکولاد و کروشه همه جفت‌اند")
