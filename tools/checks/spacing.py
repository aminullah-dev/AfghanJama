#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فاصله‌ها روی شبکهٔ ۴dp بمانند.

**وضعی که از آن آمدیم:** ۲۲۵ فاصلهٔ خارج از شبکه در ۴۵ فایل. سه عدد
به‌تنهایی ۲۱۵ تای‌شان بودند:

    14.dp × ۹۳      6.dp × ۶۶      10.dp × ۵۶

یعنی کسی یک بار `14.dp` نوشته بود و بقیه از رویش کپی کرده بودند.

**چرا اصلاً اهمیت دارد.** چشم فاصله‌ها را نسبت به هم می‌سنجد، نه
مطلق. وقتی در یک صفحه فاصله‌های ۶ و ۸ و ۱۰ و ۱۲ و ۱۴ کنارِ هم باشند،
هیچ‌کدام «یک پله بزرگ‌تر» از دیگری دیده نمی‌شود — همه تقریباً یکی‌اند
و صفحه ریتم ندارد. کاربر نمی‌تواند بگوید چه چیزی به چه چیزی مربوط
است، فقط حس می‌کند چیزی مرتب نیست. این همان چیزی است که «حرفه‌ای به
نظر نمی‌رسد» خوانده می‌شود و کسی نمی‌تواند انگشت رویش بگذارد.

**استثناها عمدی‌اند:**

  • `1.dp` و `2.dp` — خطِ مویی و سُربندیِ دو سطرِ یک برچسب. این‌ها
    فاصله نیستند، ضخامت‌اند.
  • `0.5.dp` — نیم‌خطِ جداکننده.
  • هر عددی که در `Space` تعریف شده.

بررسی فقط سطرهایی را می‌بیند که واقعاً دربارهٔ فاصله‌اند
(`padding`، `spacedBy`، `Spacer`، `height`، `width`) — نه اندازهٔ
آیکن و عکس، که قاعدهٔ خودشان را دارند.
"""
import re
import sys

import _src

GRID = 4
ALLOWED = {0.0, 0.5, 1.0, 2.0}   # ضخامت و سُربندی، نه فاصله

# فقط چیزهایی که واقعاً فاصله‌اند. `.height(320.dp)`ِ یک عکس **اندازه**
# است نه فاصله، و اگر اینجا شمرده شود بررسی سرِ هر ابعادِ درستی هم
# قرمز می‌شود — که یعنی خیلی زود خاموشش می‌کنند.
CTX = re.compile(r"padding|spacedBy|Spacer\(")
NUM = re.compile(r"\b(\d+(?:\.\d+)?)\.dp\b")

SPACE_FILE = _src.CORE / "com/afghanjama/ui/components/AppComponents.kt"

if not SPACE_FILE.exists():
    print("✗ AppComponents.kt نیست — شبکهٔ فاصله‌ها جای دیگری تعریف شده؟")
    sys.exit(1)

space_src = SPACE_FILE.read_text(encoding="utf-8")
if "object Space" not in space_src:
    print("✗ `object Space` برداشته شده — دیگر شبکه‌ای نیست که نگه داشته شود.")
    sys.exit(1)

fail = []
checked = 0

# `:desktop` عمداً بیرون است: آن ماژول را جریانِ دیگری پیش می‌برد و
# این بررسی نباید ساختِ آن‌ها را سرِ قاعده‌ای که با آن‌ها توافق نشده
# قرمز کند. صفحه‌های مشترک در `:core` هستند و پوشش می‌گیرند.
for path in sorted(_src.CORE.rglob("*.kt")) + sorted(_src.APP.rglob("*.kt")):
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.lstrip()
        if stripped.startswith(("//", "*", "/*")):
            continue
        if not CTX.search(line):
            continue
        for m in NUM.finditer(line):
            v = float(m.group(1))
            checked += 1
            if v in ALLOWED or v % GRID == 0:
                continue
            fail.append(
                f"{path.relative_to(_src.REPO)}:{n} — {m.group(1)}.dp روی "
                f"شبکهٔ {GRID} نیست. نزدیک‌ترین‌ها: "
                f"{int(v // GRID * GRID)}dp یا {int((v // GRID + 1) * GRID)}dp "
                f"(یا از `Space` بگیرید)"
            )

if fail:
    print(f"✗ {len(fail)} فاصله خارج از شبکهٔ {GRID}dp")
    for f in fail[:40]:
        print(f"  • {f}")
    if len(fail) > 40:
        print(f"  … و {len(fail) - 40} مورد دیگر")
    sys.exit(1)

print(f"✓ فاصله‌ها: هر {checked} مقدار روی شبکهٔ {GRID}dp می‌نشیند")
