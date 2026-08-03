#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""دو تابع با یک نام و یک امضا در یک فایل.

کاتلین این را خطا می‌دهد («conflicting overloads»)، ولی ده دقیقه بعد و
در CI. اینجا در یک ثانیه گفته می‌شود.

**چرا لازم شد:** هنگامِ افزودنِ ویرایش و حذف به «اطلاعات پایه»،
`deleteDesign` را به DAO اضافه کردم در حالی که از قبل بود. فایل ۱۳۰ خط
است و آن یکی بیست خط پایین‌تر نشسته بود؛ چشم نمی‌بیندش.

این دقیقاً همان دسته کاری است که زیاد پیش می‌آید: تابعی به فایلی اضافه
می‌شود که کسی همهٔ ۱۳۰ خطش را نخوانده.
"""
import re
import sys
from collections import defaultdict

import _src

#: امضا = نام + نوعِ پارامترها. نامِ پارامتر مهم نیست، چون کاتلین هم
#: فقط نوع‌ها را برای تشخیصِ سربارگذاری می‌بیند.
DECL = re.compile(r"^\s*(?:private |internal |public |protected )?(?:abstract |open |override )*"
                  r"(?:suspend )?fun\s+(?:<[^>]*>\s*)?(\w+)\s*\(([^)]*)\)", re.M)


def types_of(params: str) -> str:
    out = []
    depth = 0
    cur = ""
    for ch in params:
        if ch in "<([":
            depth += 1
        elif ch in ">)]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    # از هر پارامتر فقط نوعش می‌ماند؛ مقدارِ پیش‌فرض هم بریده می‌شود
    return ",".join(
        p.split(":", 1)[1].split("=")[0].strip() if ":" in p else p.strip()
        for p in out
    )


def enclosing_blocks(src: str):
    """برای هر جای متن، شناسهٔ بلوکی که در آن است.

    نسخهٔ اول این را نداشت و `rnd` را در `SelfTest` تکراری می‌دید — در
    حالی که آن‌ها توابعِ محلیِ **بررسی‌های مختلف**اند، و `migrate` هم در
    `object : Migration`های جداست. هیچ‌کدام تضاد ندارند.

    دامنه با شمردنِ آکولاد پیدا می‌شود: دو تابع فقط وقتی تضاد دارند که
    آکولادِ دربرگیرنده‌شان یکی باشد.
    """
    depth_stack = []
    out = []           # (offset, block_id) به ترتیب
    i = 0
    in_str = in_char = in_line_c = in_block_c = False
    while i < len(src):
        c = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ""
        if in_line_c:
            if c == "\n":
                in_line_c = False
        elif in_block_c:
            if c == "*" and nxt == "/":
                in_block_c = False
                i += 1
        elif in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif in_char:
            if c == "\\":
                i += 1
            elif c == "'":
                in_char = False
        elif c == "/" and nxt == "/":
            in_line_c = True
            i += 1
        elif c == "/" and nxt == "*":
            in_block_c = True
            i += 1
        elif c == '"':
            in_str = True
        elif c == "'":
            in_char = True
        elif c == "{":
            depth_stack.append(i)
        elif c == "}":
            if depth_stack:
                depth_stack.pop()
        out.append((i, depth_stack[-1] if depth_stack else -1))
        i += 1
    return out


files = _src.kt_files()
if not files:
    print("✗ هیچ فایلی پیدا نشد — بررسی پوچ بود، مسیرها را ببینید")
    sys.exit(1)

bad = []
for p in files:
    src = p.read_text(encoding="utf-8")
    blocks = enclosing_blocks(src)
    seen = defaultdict(list)
    for m in DECL.finditer(src):
        block = blocks[m.start()][1] if m.start() < len(blocks) else -1
        line = src[:m.start()].count("\n") + 1
        seen[(block, m.group(1), types_of(m.group(2)))].append(line)
    for (_, name, sig), lines in seen.items():
        if len(lines) > 1:
            bad.append((p.name, name, sig, lines))

if bad:
    print(f"✗ {len(bad)} تابعِ تکراری با همان امضا در یک دامنه")
    for f, name, sig, lines in bad:
        print(f"  {f}  «{name}({sig})»  خطوط {', '.join(map(str, lines))}")
    print("\n  کاتلین این را «conflicting overloads» می‌گیرد — ولی در CI.")
    sys.exit(1)

print(f"✓ {len(files)} فایل — هیچ تابعی دو بار در یک دامنه اعلام نشده")
