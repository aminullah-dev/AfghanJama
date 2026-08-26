#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""در یک ردیف، همسایهٔ چیزی که وزن دارد نباید ردیفِ بی‌وزنِ دیگری باشد.

**وضعی که از آن آمدیم — کارتِ «پروفایل کارگاه» در تنظیمات:**

    Row {
        SinglePhotoPicker(...)          ← خودش یک ردیفِ بی‌وزن است
        Column(Modifier.weight(1f)) { نامِ کارگاه؛ توضیح }
    }

`Row` اولْ بچه‌های **بی‌وزن** را اندازه می‌گیرد و هرچه ماند به وزن‌دارها
می‌دهد. `SinglePhotoPicker` با لوگو حدود ۲۶۰dp می‌گیرد (عکسِ ۶۴dp +
دکمهٔ «تعویض» + دکمهٔ «برداشتن») و عرضِ داخلِ کارت روی گوشیِ ۳۶۰dp حدود
۳۰۰dp است. یعنی برای آن ستونِ وزن‌دار ~۲۵dp می‌ماند: نامِ کارگاه حرف‌حرف
در سطرهای جدا، و توضیح یک نوارِ باریکِ عمودی. از دیدِ کاربر: «دیزاین
بهم ریخته».

**چرا کامپایل و آزمون هیچ نگفتند:** از نظرِ نوع‌ها همه‌چیز درست است.
`weight` هم کارِ خودش را می‌کند — فقط چیزی برای تقسیم نمانده. این خرابی
فقط روی صفحهٔ گوشی دیده می‌شود، و آن هم **تنها وقتی لوگو ثبت شده باشد**؛
تا وقتی `PhotoStore` خراب بود هیچ لوگویی ذخیره نمی‌شد و آن دو دکمه هرگز
با هم روی صفحه نبودند. تعمیرِ آپلود این را بیرون انداخت.

**قاعده‌ای که اینجا نگه داشته می‌شود:**

  اگر یک `Row` هم بچه‌ای دارد که `weight` می‌گیرد و هم بچه‌ای که
  **خودش ریشه‌اش یک `Row`ِ بی‌وزن است**، آن بچهٔ دوم باید یا وزن بگیرد
  یا به سطرِ خودش برود.

فهرستِ «ریشه‌اش ردیف است» دستی نگه‌داشته نمی‌شود — از خودِ کد درمی‌آید:
هر `@Composable`ی که `modifier: Modifier = Modifier` می‌گیرد و همان را
مستقیم روی یک `Row(` می‌نشاند. پس کامپوننتِ تازه‌ای که فردا با همین
شکل ساخته شود، بی آنکه کسی چیزی به این فایل اضافه کند، پوشش می‌گیرد.
"""
import re
import sys

import _src

# ── کمکی‌ها ──────────────────────────────────────────────────────


def strip(src: str) -> str:
    """پاک‌کردنِ توضیح‌ها و رشته‌ها تا پرانتزهای داخلشان نشمرده شوند."""
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    # رشته‌های سه‌تایی و ساده — محتوا برداشته می‌شود، خطوط می‌مانند
    src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    return src


def block_after(src: str, start: int, opener: str, closer: str):
    """از `start` جلو می‌رود تا اولین `opener` و جفتش را پیدا کند.

    برمی‌گرداند: (اندیسِ داخلِ بلوک، اندیسِ بستن) یا None.
    """
    i = src.find(opener, start)
    if i < 0:
        return None
    depth = 0
    for j in range(i, len(src)):
        if src[j] == opener:
            depth += 1
        elif src[j] == closer:
            depth -= 1
            if depth == 0:
                return i + 1, j
    return None


def lambda_after(src: str, at: int):
    """بدنهٔ `{ … }`ی که بلافاصله بعدِ یک فراخوان می‌آید.

    هر دو نوشتار را می‌گیرد: `Row(...) { … }` و `Row { … }`. نوعِ دوم
    یک بار از چشمِ همین بررسی افتاد — ردیفی که هیچ آرگومانی ندارد
    پرانتز هم ندارد، و الگویی که `Row\\s*\\(` می‌خواست اصلاً نمی‌دیدش.
    """
    k = at
    while k < len(src) and src[k] in " \t\r\n":
        k += 1
    if k < len(src) and src[k] == "(":
        args = block_after(src, k, "(", ")")
        if not args:
            return None
        k = args[1] + 1
        while k < len(src) and src[k] in " \t\r\n":
            k += 1
    if k >= len(src) or src[k] != "{":
        return None
    return block_after(src, k, "{", "}")


def at_top_level(body: str):
    """جاهایی از `body` که در هیچ `{}`ی تودرتو نیستند — یعنی بچه‌های مستقیم.

    `Column(Modifier.weight(1f)) { … }` را در نظر بگیرید: خودِ
    `Modifier.weight(1f)` بیرونِ هر آکولادِ تودرتوست، پس مالِ همین
    ردیف است. ولی `weight`ی که داخلِ آن `{ … }` باشد مالِ خودِ ستون
    است، نه این ردیف.
    """
    mask = []
    depth = 0
    for ch in body:
        if ch == "{":
            depth += 1
            mask.append(False)
            continue
        if ch == "}":
            depth -= 1
            mask.append(False)
            continue
        mask.append(depth == 0)
    return mask


# ── ۱ کامپوننت‌هایی که ریشه‌شان یک ردیفِ بی‌وزن است ────────────────

ROW_ROOTED = {}          # نام -> فایل

for path in _src.kt_files():
    src = strip(path.read_text(encoding="utf-8"))
    for m in re.finditer(r"@Composable[\s\S]{0,200}?\bfun\s+([A-Z]\w*)\s*\(", src):
        name = m.group(1)
        args = block_after(src, m.end() - 1, "(", ")")
        if not args:
            continue
        params = src[args[0]:args[1]]
        if "modifier: Modifier = Modifier" not in params:
            continue
        body = block_after(src, args[1], "{", "}")
        if not body:
            continue
        inner = src[body[0]:body[1]]
        # همان modifierِ فراخواننده مستقیم روی یک Row نشسته باشد
        if re.search(r"\bRow\(\s*modifier\s*[,)]", inner):
            ROW_ROOTED[name] = path

if not ROW_ROOTED:
    print("✗ هیچ کامپوننتِ ردیف‌ریشه‌ای پیدا نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

# ── ۲ ردیف‌هایی که هم وزن دارند هم چنین همسایه‌ای ─────────────────

fail = []

for path in _src.kt_files():
    raw = path.read_text(encoding="utf-8")
    src = strip(raw)
    for m in re.finditer(r"\bRow\b", src):
        body_span = lambda_after(src, m.end())
        if not body_span:
            continue
        body = src[body_span[0]:body_span[1]]
        mask = at_top_level(body)

        def top_level_hits(pattern):
            return [
                h for h in re.finditer(pattern, body)
                if mask[h.start()]
            ]

        if not top_level_hits(r"\.weight\s*\("):
            continue

        for name in ROW_ROOTED:
            for hit in top_level_hits(r"\b" + name + r"\s*\("):
                # اگر خودش وزن گرفته باشد، مشکلی نیست
                call = block_after(body, hit.end() - 1, "(", ")")
                passed = body[call[0]:call[1]] if call else ""
                if ".weight(" in passed:
                    continue
                line = raw[:0].count("\n") + src[:m.start()].count("\n") + 1
                fail.append(
                    f"{path.relative_to(_src.REPO)}:{line} — ردیفی که بچهٔ "
                    f"وزن‌دار دارد «{name}» را هم بی‌وزن کنارش گذاشته. "
                    f"«{name}» خودش یک ردیفِ بی‌وزن است ("
                    f"{ROW_ROOTED[name].name}) و عرضِ کاملش را برمی‌دارد؛ "
                    f"برای بچهٔ وزن‌دار چیزی نمی‌ماند. یا وزن بدهید یا به "
                    f"سطرِ جدا ببرید."
                )

if fail:
    print(f"✗ {len(fail)} ردیفِ خفه‌شده")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ چیدمان: {len(ROW_ROOTED)} کامپوننتِ ردیف‌ریشه‌ای "
    f"({'، '.join(sorted(ROW_ROOTED))}) هیچ‌کدام کنارِ بچهٔ وزن‌دار نیستند"
)
