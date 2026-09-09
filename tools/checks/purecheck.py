#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ماژولِ :core باید بی‌اندروید بماند.

این مرز چیزی است که نسخهٔ ویندوز رویش بنا می‌شود: هر خطی که در `:core`
است روی گوشی و روی پی‌سی یکسان اجرا می‌شود. کافی است کسی یک
`import android.content.Context` اضافه کند تا آن ضمانت از بین برود.

کامپایلر هم همین را می‌گیرد (اندروید اصلاً در classpathِ این ماژول
نیست)، ولی کامپایلر فقط در CI اجرا می‌شود و ده دقیقه طول می‌کشد. این
بررسی در یک ثانیه همان را می‌گوید.
"""
import pathlib
import re
import sys

import _src

REPO = pathlib.Path(__file__).resolve().parents[2]
# مسیر از `_src` می‌آید، نه سفت اینجا — همان قانونی که همین فایل پایین
# بر بقیهٔ بررسی‌ها اعمال می‌کند. `:core` حالا سه پوشهٔ منبع دارد.
CORE = _src.CORE

BANNED = ("import android.", "import androidx.")

# استثنا: حاشیه‌نویسی‌های Room.
#
# `androidx.room` با بقیهٔ androidx فرق دارد: `room-common` یک jarِ
# خالصِ جاواست، نه کتابخانهٔ اندروید. جدولِ داده‌ها با همین‌ها توصیف
# می‌شود و روی ویندوز هم همان توصیف کار می‌کند. موتورِ Room
# (`room-runtime`) اینجا نیست و نباید بیاید.
#
# و `androidx.lifecycle.ViewModel` / `viewModelScope`.
#
# از نسخهٔ ۲.۸ این کتابخانه چندسکویی است و برای JVMِ رومیزی هم منتشر
# می‌شود؛ Gradle برای هر مصرف‌کننده نسخهٔ درستش را برمی‌دارد. پس مثلِ
# `room-common` اینجا مجاز است — ولی فقط همین دو نام، نه هر چیزی که
# زیرِ `androidx.lifecycle` باشد.
#
# و `androidx.compose.*` — صفحه‌های مشترکِ فازِ ۴.۵.
#
# همان استدلال: کلاس‌های Compose روی گوشی و پی‌سی نامِ یکسان دارند، پس
# یک صفحه یک بار نوشته می‌شود و هر دو جا اجرا. اینجا `compileOnly`اند تا
# گرافِ وابستگیِ اپِ اندروید دست‌نخورده بماند.
ALLOWED = (
    "import androidx.room.",
    "import androidx.lifecycle.ViewModel",
    "import androidx.lifecycle.viewModelScope",
    "import androidx.compose.",
)

# ...ولی نه هر چیزی زیرِ androidx.compose.
#
# اینها اسمشان Compose است ولی زیرشان اندروید است: `LocalContext` یک
# `android.content.Context` می‌دهد، `stringResource` به `res/` گوشی نگاه
# می‌کند، و `Preview` ابزارِ Android Studio است. اگر یکی از اینها وارد
# یک صفحهٔ مشترک شود، آن صفحه دیگر روی ویندوز کامپایل نمی‌شود — و چون
# `:core` را رانرِ لینوکس با jarِ دسکتاپ می‌سازد، کامپایلر هم می‌گیردش؛
# ولی این بررسی زودتر و با پیامِ روشن‌تر می‌گیرد.
COMPOSE_ANDROID = (
    "import androidx.compose.ui.platform.LocalContext",
    "import androidx.compose.ui.platform.LocalConfiguration",
    "import androidx.compose.ui.platform.LocalView",
    "import androidx.compose.ui.res.",
    "import androidx.compose.ui.tooling.",
    "import androidx.compose.ui.viewinterop.",
)

# ...و اینها زیرِ `androidx.lifecycle` هستند ولی اندروید می‌خواهند.
#
# `AndroidViewModel` یک `Application` می‌گیرد — یعنی مستقیم به اندروید
# گره می‌خورد. اگر کسی به‌جای `ViewModel` این را بنویسد، مرز بی‌سروصدا
# شکسته و بررسی باید همان‌جا بگیردش.
LIFECYCLE_ANDROID = (
    "import androidx.lifecycle.AndroidViewModel",
    "import androidx.lifecycle.LiveData",
    "import androidx.lifecycle.MutableLiveData",
    "import androidx.lifecycle.LifecycleOwner",
    "import androidx.lifecycle.LifecycleObserver",
    "import androidx.lifecycle.ProcessLifecycleOwner",
    "import androidx.lifecycle.asLiveData",
    "import androidx.lifecycle.observe",
)

# ...ولی نه هر چیزی زیرِ androidx.room.
#
# `room-common` فقط حاشیه‌نویسی است؛ این‌ها در `room-runtime` هستند که
# موتور است و اندروید می‌خواهد. اگر یکی از این‌ها وارد :core شود، مرز
# بی‌سروصدا شکسته و همان روز فرقی با نبودنِ مرز ندارد.
RUNTIME_ONLY = (
    "import androidx.room.Room\n",
    "import androidx.room.Room.",
    "import androidx.room.RoomDatabase",
    "import androidx.room.RoomWarnings",
    "import androidx.room.migration",
    "import androidx.room.testing",
    "import androidx.room.util",
)

bad = []
files = sorted(CORE.rglob("*.kt"))
for p in files:
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        runtime = line.startswith(RUNTIME_ONLY) or line.rstrip() in (
            "import androidx.room.Room",
        )
        runtime = runtime or line.startswith(LIFECYCLE_ANDROID)
        runtime = runtime or line.startswith(COMPOSE_ANDROID)
        if runtime or (line.startswith(BANNED) and not line.startswith(ALLOWED)):
            bad.append((_src.rel_to_core(p), i, line.strip()))

# ---- نشتیِ موتورِ دیتابیس ----
#
# ایمپورت تنها راهِ گره خوردن به اندروید نیست. `Repo` تا دیروز
# `db.openHelper` و `db.runInTransaction` را صدا می‌زد — هیچ ایمپورتی
# لازم نداشت، ولی همان‌ها بودند که نگذاشتند از اندروید جدا شود.
#
# هر کاری که واقعاً به موتور نیاز دارد باید در `Db` یک نامِ خودش داشته
# باشد، نه اینکه موتور را از لای منطق بیرون بکشد.
ENGINE = ("openHelper", "runInTransaction", "clearAllTables", "beginTransaction")
leaks = []
for p_ in files:
    for i, line in enumerate(p_.read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith(("*", "//")):
            continue
        for name in ENGINE:
            if f".{name}" in line:
                leaks.append((_src.rel_to_core(p_), i, name, line.strip()))

if leaks:
    print(f"✗ {len(leaks)} نشتیِ موتورِ دیتابیس در :core")
    for f, i, name, line in leaks:
        print(f"  {f}:{i}  «{name}»  {line[:60]}")
    print("\n  اینها فقط روی Room اندروید هستند. اگر منطق به چنین کاری")
    print("  نیاز دارد، در `Db` یک عملیاتِ نام‌دار برایش تعریف کنید.")
    sys.exit(1)

if not files:
    print("✗ هیچ فایلی در :core نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

if bad:
    print(f"✗ {len(bad)} ایمپورتِ اندرویدی در :core")
    for f, i, line in bad:
        print(f"  {f}:{i}  {line}")
    print("\n  :core باید روی ویندوز هم کامپایل شود. این خط آنجا وجود ندارد.")
    sys.exit(1)

print(f"✓ {len(files)} فایلِ :core — هیچ‌کدام به اندروید وابسته نیست")

# ---- ارجاعِ کاملاً مقید از :core به بیرون ----
#
# `import` تنها راهِ نام بردن از یک کلاس نیست. `LanClient` سالها
# `com.afghanjama.ui.vm.BoardRow` را **کاملاً مقید** صدا می‌زد — بی هیچ
# خطِ import. وقتی آن فایل به `:core` رفت، اسکنِ ایمپورت چیزی ندید و
# ساخت در CI شکست.
#
# هر نامِ کاملاً مقیدِ `com.afghanjama.*` که در `:core` نوشته شود باید
# خودش هم در `:core` باشد.
FQ = re.compile(r"\bcom\.afghanjama\.((?:\w+\.)+)([A-Z]\w*)")
core_pkgs = set()
for p_ in files:
    for line in p_.read_text(encoding="utf-8").splitlines():
        if line.startswith("package "):
            core_pkgs.add(line.split()[1].strip())
            break

fq_bad = []
for p_ in files:
    for i, line in enumerate(p_.read_text(encoding="utf-8").splitlines(), 1):
        s = line.strip()
        if s.startswith(("import ", "package ", "*", "//")):
            continue
        for mm in FQ.finditer(line):
            pkg = "com.afghanjama." + mm.group(1).rstrip(".")
            if pkg not in core_pkgs:
                fq_bad.append((_src.rel_to_core(p_), i, f"{pkg}.{mm.group(2)}"))

if fq_bad:
    print(f"✗ {len(fq_bad)} ارجاعِ کاملاً مقید از :core به بستهٔ بیرونی")
    for f, i, name in fq_bad:
        print(f"  {f}:{i}  {name}")
    print("\n  این‌ها خطِ import ندارند، پس از چشمِ اسکنرها می‌افتند.")
    print("  یا آن نماد باید به :core بیاید، یا ارجاع برداشته شود.")
    sys.exit(1)

# ---- `internal`ِ :core که بیرون استفاده شده ----
#
# `internal` در کاتلین مرزِ **ماژول** دارد نه بسته. تا وقتی یک تابع در
# `:app` بود، `internal` بی‌ضرر بود؛ همان تابع که به `:core` می‌رود،
# `internal`اش یعنی «`:app` دیگر نمی‌بیندت».
#
# این یک بار افتاد: `qtyFa` با `internal` به `:core` رفت و ساختِ اپ
# شکست. کامپایلر می‌گیردش، ولی ده دقیقه بعد در CI.
CORE_INTERNAL = re.compile(
    r"^internal\s+(?:fun|val|var|class|object|interface)\s+(?:[\w.<>]+\.)?(\w+)", re.M
)
internals = {}
for p_ in files:
    for mm in CORE_INTERNAL.finditer(p_.read_text(encoding="utf-8")):
        internals[mm.group(1)] = _src.rel_to_core(p_)

outside = []
if internals:
    others = []
    for r in _src.ROOTS:
        if r in _src.CORE_ROOTS:
            continue
        others.extend(r.rglob("*.kt"))
    for p_ in others:
        body = p_.read_text(encoding="utf-8")
        for name, owner in internals.items():
            if re.search(rf"\b{re.escape(name)}\s*\(", body):
                outside.append((name, owner, p_.name))

if outside:
    print(f"✗ {len(outside)} نمادِ internalِ :core از بیرون استفاده شده")
    for name, owner, user in outside:
        print(f"  «{name}» در {owner}  ←  {user}")
    print("\n  `internal` مرزِ ماژول دارد. اگر بیرون لازم است، عمومی‌اش کنید.")
    sys.exit(1)

# ---- نگهبانِ خودِ بررسی‌ها ----
#
# هیچ بررسی‌ای نباید مسیرِ ماژول را در خودش سفت کند. با دو ماژول شدنِ
# پروژه، `extcheck` دقیقاً همین را داشت و بی‌سروصدا از ۹ تابع به ۱ تابع
# افتاد — سبز ماند در حالی که تقریباً هیچ نمی‌دید. بدترین حالتِ ممکن
# برای یک بررسی.
checks = pathlib.Path(__file__).resolve().parent
hard = []
for p in sorted(checks.glob("*.py")):
    if p.name in ("_src.py", "purecheck.py"):
        continue
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        # فقط بخشِ کدِ خط. مسیری که در **توضیح** آمده بی‌خطر است و
        # گرفتنش قرمزِ دروغ می‌دهد: `iconcolor.py` در توضیحش نوشته بود
        # کدام مسیر عوض شده و چرا، و همین بررسی همان توضیح را «مسیرِ
        # سفت» شمرد. سومین باری که یک نگهبانِ این پروژه متن را به‌جای کد
        # خواند — `uicheck` و `deskdeps` هم همین را داشتند.
        code = line.split("#", 1)[0]
        if "app/src/main/java" in code or "core/src/" in code:
            hard.append((p.name, i, line.strip()))

if hard:
    print(f"\n✗ {len(hard)} بررسی مسیرِ ماژول را در خودش سفت کرده")
    for f, i, line in hard:
        print(f"  {f}:{i}  {line[:78]}")
    print("\n  مسیرها باید از _src بیایند، وگرنه ماژولِ تازه از دیدشان می‌افتد.")
    sys.exit(1)

print(f"✓ هیچ بررسی‌ای مسیرِ ماژول را سفت نکرده")
