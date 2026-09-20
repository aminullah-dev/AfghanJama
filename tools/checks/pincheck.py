#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""رمزِ کاربر هرگز خام یا با هشِ ساده ذخیره نشود.

**وضعی که از آن آمدیم — دو سیستمِ رمز، هر دو باز:**

  • `AuthViewModel` رمزِ ورود را **متنِ خام** ذخیره می‌کرد. هر کسی که
    به فایلِ تنظیمات می‌رسید رمزِ کارگاه را می‌خواند. (خودِ آزمونِ
    شبیه‌ساز هم همان را خام می‌نویسد — همان‌جا معلوم بود.)
  • `AppLock` از `SHA-256`ِ بی‌نمک استفاده می‌کرد. رمزِ ۴ رقمی ۱۰٬۰۰۰
    حالت دارد؛ جدولِ همه‌شان روی هر لپ‌تاپی کسری از ثانیه ساخته می‌شود.
    عملاً با متنِ خام فرقی نداشت.

هر دو حالا از `PinHash` می‌گذرند (PBKDF2 با نمک و ۱۰۰٬۰۰۰ تکرار).

این بررسی چهار چیز را نگه می‌دارد:

  ۱ هر جایی که رمز **نوشته** می‌شود از `PinHash.hash` بگذرد
  ۲ هر جایی که رمز **سنجیده** می‌شود از `PinHash.verify` بگذرد، نه از
    `==` روی مقدارِ ذخیره‌شده
  ۳ هیچ‌کدام از این دو فایل `MessageDigest` را مستقیم صدا نزند
  ۴ **هر سکو واقعاً PBKDF2 داشته باشد**

بندِ ۴ با چندسکویی شدنِ `:core` لازم شد. تا دیروز `PBEKeySpec` و
`SecureRandom` در خودِ `PinHash.kt` بودند و همین بررسی نامشان را
می‌دید. حالا `PinHash` منطق را دارد و اولیهٔ رمزنگاری را از سکو
می‌خواهد (`expect fun pbkdf2`), پس اگر یک سکو `TODO()` یا چیزی
سرِهم‌بندی برگرداند، این فایل همچنان سالم به‌نظر می‌رسد و قفلِ ورود روی
آن سکو پوچ است — بی هیچ خطای کامپایلی.

پس هر `actual` جدا سنجیده می‌شود: JVM باید `PBEKeySpec` داشته باشد و
iOS باید `CCKeyDerivationPBKDF`؛ و هیچ‌کدام حق ندارند `TODO(` داشته
باشند.

قاعدهٔ ۲ ظریف است و به همین دلیل صریح نوشته شده: برگشتن به `==` هیچ
خطای کامپایلی نمی‌دهد و اپ **درست کار می‌کند** — فقط رمز دوباره خام
مقایسه می‌شود.
"""
import re
import sys

import _src

AUTH = _src.CORE / "com/afghanjama/ui/vm/AuthViewModel.kt"

#: سهمِ هر سکو از رمزنگاری، و نامی که باید در آن باشد.
CRYPTO = {
    "com/afghanjama/util/PinCrypto.jvm.kt": ("PBEKeySpec", "SecureRandom"),
    "com/afghanjama/util/PinCrypto.ios.kt": ("CCKeyDerivationPBKDF", "SecRandomCopyBytes"),
}
LOCK = _src.APP / "com/afghanjama/util/AppLock.kt"
HASH = _src.CORE / "com/afghanjama/util/PinHash.kt"


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


fail = []

if not HASH.exists():
    print(f"✗ {HASH.name} نیست — رمز جای دیگری هش می‌شود؟")
    sys.exit(1)

hash_src = strip(HASH.read_text(encoding="utf-8"))
for need in ("pbkdf2(", "secureRandomBytes(", "fun verify", "fun needsUpgrade"):
    if need not in hash_src:
        fail.append(f"PinHash: «{need}» نیست — نمک یا وارسی برداشته شده.")

# ── سهمِ هر سکو ──
for rel, needs in CRYPTO.items():
    try:
        path = _src.CORE / rel
    except FileNotFoundError:
        fail.append(f"{rel.rsplit('/', 1)[-1]} نیست — آن سکو PBKDF2 ندارد.")
        continue
    src = strip(path.read_text(encoding="utf-8"))
    for need in needs:
        if need not in src:
            fail.append(
                f"{path.name}: «{need}» نیست — رمز روی آن سکو با چیزِ "
                f"دیگری ساخته می‌شود."
            )
    if "TODO(" in src:
        fail.append(f"{path.name}: `TODO(` دارد — قفلِ ورود روی آن سکو پوچ است.")
# نمک باید تصادفی باشد، نه ثابت
if re.search(r'salt\s*=\s*"', hash_src):
    fail.append("PinHash: نمکِ ثابت — نمکی که ثابت باشد نمک نیست.")

auth = strip(AUTH.read_text(encoding="utf-8"))
lock = strip(LOCK.read_text(encoding="utf-8"))

# ── ۱ نوشتن ─────────────────────────────────────────────────────
for m in re.finditer(r"putString\(\s*FILE\s*,\s*KEY_PIN\s*,\s*([^)]+)\)", auth):
    if "PinHash.hash(" not in m.group(1):
        fail.append(
            f"AuthViewModel: رمز بدونِ PinHash.hash نوشته می‌شود — "
            f"«{m.group(1).strip()[:40]}»"
        )
for m in re.finditer(r"putString\(\s*KEY_HASH\s*,\s*([^)]+)\)", lock):
    if "PinHash.hash(" not in m.group(1):
        fail.append(
            f"AppLock: رمز بدونِ PinHash.hash نوشته می‌شود — "
            f"«{m.group(1).strip()[:40]}»"
        )

# ── ۲ سنجیدن ────────────────────────────────────────────────────
for name, src in (("AuthViewModel", auth), ("AppLock", lock)):
    if "PinHash.verify(" not in src:
        fail.append(f"{name}: رمز از PinHash.verify نمی‌گذرد.")
    # مقایسهٔ مستقیمِ رمزِ ذخیره‌شده
    for pat in (r"!=\s*saved\b", r"==\s*saved\b", r"stored\s*==", r"==\s*stored\b"):
        if re.search(pat, src):
            fail.append(
                f"{name}: مقایسهٔ مستقیم با مقدارِ ذخیره‌شده («{pat}») — "
                f"باید از PinHash.verify بگذرد."
            )

# ── ۳ هشِ دستی ──────────────────────────────────────────────────
for name, src in (("AuthViewModel", auth), ("AppLock", lock)):
    if "MessageDigest" in src:
        fail.append(f"{name}: MessageDigest مستقیم — هشِ ساده برای رمز کافی نیست.")

# ── ارتقا نباید فراموش شود ──────────────────────────────────────
for name, src in (("AuthViewModel", auth), ("AppLock", lock)):
    if "needsUpgrade" not in src:
        fail.append(
            f"{name}: ارتقای بی‌صدا نیست — گوشی‌هایی که رمزِ قدیمی دارند "
            f"برای همیشه با قالبِ ضعیف می‌مانند."
        )

if fail:
    print(f"✗ {len(fail)} مشکل در نگه‌داریِ رمز")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print("✓ رمز: هر دو مسیر از PinHash می‌گذرند و قالبِ قدیمی ارتقا می‌گیرد")
