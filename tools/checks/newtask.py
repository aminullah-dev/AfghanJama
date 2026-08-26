#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`startActivity` فقط از یک جا — وگرنه اپ از Contextِ برنامه می‌ترکد.

**خرابی‌ای که این بررسی را ساخت** (روی گوشیِ کارگاه، نسخهٔ ۱.۱.۱):

    android.util.AndroidRuntimeException: Calling startActivity() from
    outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK
    flag.
        at com.afghanjama.util.ShareUtil.shareFile(ShareUtil.kt:29)
        at com.afghanjama.platform.AndroidDocs.financials(...)

`AndroidDocs` و `AndroidSystemActions` با `applicationContext` ساخته
می‌شوند، نه با Activity. اندروید از چنین Contextی `startActivity` را
فقط با پرچمِ `FLAG_ACTIVITY_NEW_TASK` قبول می‌کند.

**نکتهٔ تلخش:** `AndroidSystemActions` این را می‌دانست و پرچم را
می‌گذاشت — حتی در توضیحش نوشته بود چرا. ولی `ShareUtil.shareFile`
مسیرِ جداگانه‌ای بود و همان درس به آن نرسیده بود. یک قاعده که در دو
جا جدا پیاده شود، دیر یا زود در یکی‌شان فراموش می‌شود؛ و این یکی
تا لحظهٔ زدنِ دکمهٔ «اشتراکِ گزارشِ مالی» روی گوشیِ واقعی معلوم نشد.
کامپایل بی‌عیب بود و هیچ آزمونی هم آن را نمی‌گرفت.

**قاعده:** در کلِ `:app` فقط [ALLOWED] حق دارد `startActivity` را صدا
بزند. آنجا یک تابع تصمیم می‌گیرد پرچم لازم است یا نه، و استثنای
«هیچ برنامه‌ای برای این کار نیست» را هم می‌گیرد.
"""
import re
import sys

import _src

#: تنها فایلی که اجازه دارد
ALLOWED = "com/afghanjama/util/ShareUtil.kt"

#: تابعی که باید در همان فایل، پرچم را مشروط بگذارد
FLAG = "FLAG_ACTIVITY_NEW_TASK"


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    # رشته‌ها برداشته می‌شوند تا نامِ تابع داخلِ یک پیام شمرده نشود
    src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


gate = _src.APP / ALLOWED
if not gate.exists():
    print(f"✗ {ALLOWED} نیست — دروازه‌ای که این بررسی نگه می‌دارد جابه‌جا شده.")
    sys.exit(1)

fail = []

# ── ۱ دروازه واقعاً پرچم را می‌گذارد؟ ─────────────────────────────
gate_src = strip(gate.read_text(encoding="utf-8"))
if FLAG not in gate_src:
    fail.append(
        f"ShareUtil دیگر {FLAG} را نمی‌گذارد — دروازه هست ولی کارش را "
        f"نمی‌کند، و این بدتر از نبودنش است."
    )
# مشروط، نه همیشگی: پرچمِ همیشگی پنجره را در وظیفهٔ جدا باز می‌کند و
# دکمهٔ برگشت به صفحهٔ قبل برنمی‌گردد.
if "activityOrNull" not in gate_src:
    fail.append(
        "ShareUtil دیگر Activity را تشخیص نمی‌دهد — یعنی پرچم یا همیشه "
        "گذاشته می‌شود یا هرگز؛ هر دو غلط است."
    )

# ── ۲ هیچ‌کس دیگری صدا نزند ───────────────────────────────────────
for path in sorted(_src.APP.rglob("*.kt")):
    rel = path.relative_to(_src.APP).as_posix()
    if rel == ALLOWED:
        continue
    src = strip(path.read_text(encoding="utf-8"))
    for m in re.finditer(r"\bstartActivity(?:ForResult)?\s*\(", src):
        line = src[:m.start()].count("\n") + 1
        fail.append(
            f"{path.relative_to(_src.REPO)}:{line} — startActivity مستقیم. "
            f"اگر این Context از Activity نیامده باشد اپ می‌بندد. از "
            f"ShareUtil.launch یا launchChooser رد شود."
        )

if fail:
    print(f"✗ {len(fail)} مشکل در باز کردنِ صفحهٔ بیرونی")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print("✓ باز کردنِ صفحهٔ بیرونی: فقط از ShareUtil، و پرچم مشروط گذاشته می‌شود")
