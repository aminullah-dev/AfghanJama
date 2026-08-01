# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
نمادهای کتابخانه‌ای (androidx/kotlinx/java) که استفاده شده‌اند ولی ایمپورت نه.

refcheck فقط نمادهای **خودِ پروژه** را می‌شناسد، پس `BorderStroke` را
نگرفت و خطا در CI بیرون آمد. اینجا از خودِ پروژه یاد می‌گیریم: هر نمادی
که جایی در پروژه ایمپورت شده، یک نامِ شناخته‌شده است؛ اگر فایلی از آن
استفاده کند و ایمپورتش نکند، همان‌جا خطای کامپایل می‌شود.

هیچ فهرستِ دستی‌ای لازم نیست و با رشدِ پروژه خودش کامل‌تر می‌شود.
"""
import glob
import re
import sys
from collections import defaultdict

SRC = _REPO + "/app/src/main/java/com/afghanjama"
files = sorted(glob.glob(f"{SRC}/**/*.kt", recursive=True))
if len(files) < 50:
    raise SystemExit(f"✗ فقط {len(files)} فایل — مسیر اشتباه است، بررسی پوچ بود")


def strip_code(src):
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


# ---- ۱. نامِ هر نمادِ کتابخانه‌ای که جایی ایمپورت شده ----
owner_of = defaultdict(set)      # نامِ ساده → مجموعهٔ مسیرهای کامل
for f in files:
    for m in re.finditer(r"^import\s+((?:androidx|kotlinx|java|org|com\.google)[\w.]*)",
                         open(f).read(), re.M):
        path = m.group(1)
        simple = path.split(".")[-1]
        if simple == "*":
            continue
        owner_of[simple].add(path)

    # نمادهایی که هیچ‌جا ایمپورت نشده‌اند ولی با **مسیرِ کامل** استفاده
    # می‌شوند هم شناخته می‌شوند. GridItemSpan همین‌طور بود: دو جای پروژه
    # با مسیرِ کامل صدایش می‌زدند، پس در هیچ خطِ import نبود و این بررسی
    # نمی‌شناختش — بعد جای سومی با نامِ کوتاه نوشته شد و کامپایل شکست.
    for m in re.finditer(
        r"(?<![\w.])((?:androidx|kotlinx|java|org|com\.google)(?:\.[a-z0-9_]+)+\.([A-Z]\w*))",
        open(f).read()
    ):
        owner_of[m.group(2)].add(m.group(1))

# نامی که دو مسیرِ مختلف دارد مبهم است — کنار گذاشته می‌شود تا هشدارِ
# نادرست ندهیم (مثلاً Card در material و material3)
AMBIGUOUS = {s for s, paths in owner_of.items() if len(paths) > 1}

# نامِ با حرفِ کوچک را نمی‌شود آزادانه سنجید: `update` در DAO متدِ Room
# است و `cancel` چیزِ دیگری — هر دو هشدارِ نادرست می‌دادند. پس فقط همان
# توابعِ Compose که واقعاً جا می‌مانند سنجیده می‌شوند. نبودشان ده‌ها خطای
# کامپایل می‌سازد، همان که یک بار در ProcurementScreen پیش آمد.
WATCHED_LOWER = {
    "remember", "rememberSaveable", "rememberCoroutineScope",
    "rememberNavController", "rememberScrollState", "rememberLazyListState",
    "mutableStateOf", "mutableIntStateOf", "mutableLongStateOf",
    "mutableStateListOf", "derivedStateOf", "produceState",
    "collectAsState", "collectAsStateWithLifecycle",
    # توابعِ Flow که در ViewModelها جا می‌مانند و همان کلاسِ خطا را
    # می‌سازند — یک بار map و combine در ProcurementViewModel جا ماندند.
    "combine", "stateIn", "asStateFlow", "flowOf", "distinctUntilChanged",
}

# افزونه‌های Modifier همیشه بعد از نقطه می‌آیند (`Modifier.heightIn(...)`)
# پس در سنجشِ عمومی که نامِ بی‌نقطه می‌خواهد دیده نمی‌شوند — heightIn
# همین‌طور جا ماند و ساخت شکست. اینها جدا سنجیده می‌شوند.
MODIFIER_EXT = {
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "sizeIn": "androidx.compose.foundation.layout.sizeIn",
    "imePadding": "androidx.compose.foundation.layout.imePadding",
    "navigationBarsPadding": "androidx.compose.foundation.layout.navigationBarsPadding",
    "statusBarsPadding": "androidx.compose.foundation.layout.statusBarsPadding",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "wrapContentHeight": "androidx.compose.foundation.layout.wrapContentHeight",
    "wrapContentWidth": "androidx.compose.foundation.layout.wrapContentWidth",
}

# آیکون‌ها الگوی خودشان را دارند: `Icons.Default.X` نامِ X را هرگز
# بدونِ نقطه نشان نمی‌دهد، پس از سنجشِ عمومی بیرون می‌مانْد — و همین
# باعث شد PersonSearch جا بماند و ساخت بشکند.
ICON_SETS = {
    "Default": "androidx.compose.material.icons.filled",
    "Filled": "androidx.compose.material.icons.filled",
    "Outlined": "androidx.compose.material.icons.outlined",
    "Rounded": "androidx.compose.material.icons.rounded",
    "Sharp": "androidx.compose.material.icons.sharp",
    "TwoTone": "androidx.compose.material.icons.twotone",
}

problems = []
for f in files:
    raw = open(f).read()
    code = strip_code(raw)
    imported = set()
    star = False
    for m in re.finditer(r"^import\s+([\w.*]+)(?:\s+as\s+(\w+))?", raw, re.M):
        path, alias = m.group(1), m.group(2)
        if path.endswith(".*"):
            star = True
        imported.add(alias or path.split(".")[-1])
    if star:
        continue                            # ایمپورتِ ستاره‌دار — نمی‌شود قضاوت کرد

    # نمادهایی که خودِ همین فایل تعریف کرده
    local = set(re.findall(
        r"\b(?:class|object|interface|enum class|data class|annotation class)\s+([A-Za-z_]\w*)",
        code))

    # نامِ با حرفِ بزرگ هرجا، و نامِ با حرفِ کوچک فقط وقتی مثلِ تابع صدا
    # زده می‌شود یا واگذارندهٔ `by` است. توابعِ کوچکِ Compose مثل remember
    # و mutableStateOf همین‌طورند و تا امروز از قلم می‌افتادند — همان
    # چیزی که ۱۶ خطای ProcurementScreen را ساخت.
    used = set(re.findall(r"(?<![.\w])([A-Z][A-Za-z0-9_]*)\b", code))
    lower_used = set(re.findall(r"(?<![.\w])([a-z]\w*)\s*[({]", code))
    used |= (lower_used & WATCHED_LOWER)
    for sym in used:
        if sym in AMBIGUOUS or sym not in owner_of:
            continue
        if sym in imported or sym in local:
            continue
        # «مسیرِ کامل جایی در فایل هست» دلیلِ بی‌نیازی نیست.
        #
        # `used` نامِ کوتاه را فقط وقتی می‌شمارد که پیش از آن نقطه نباشد،
        # پس استفادهٔ کاملاً باکیفیت اصلاً به اینجا نمی‌رسد. اگر رسیده،
        # یعنی جایی در همین فایل نامِ کوتاه هم نوشته شده و ایمپورت لازم
        # است — دقیقاً حالتِ GridItemSpan که دو جا با مسیرِ کامل بود و
        # جای سوم کوتاه، و این قاعده پنهانش کرد.
        path = next(iter(owner_of[sym]))
        problems.append((f.replace(SRC + "/", ""), sym, path))

# ---- افزونه‌های Modifier که بعد از نقطه صدا زده می‌شوند ----
for f in files:
    raw = open(f).read()
    code = strip_code(raw)
    for name, path in MODIFIER_EXT.items():
        if not re.search(r"\.\s*" + name + r"\s*\(", code):
            continue
        if f"import {path}" in raw or path in raw:
            continue
        problems.append((f.replace(SRC + "/", ""), name, path))

# ---- آیکون‌های استفاده‌شده ولی ایمپورت‌نشده ----
for f in files:
    raw = open(f).read()
    if re.search(r"^import androidx\.compose\.material\.icons\.\*", raw, re.M):
        continue
    code = strip_code(raw)
    for m in re.finditer(r"\bIcons\.AutoMirrored\.(\w+)\.(\w+)", code):
        pkg = ICON_SETS.get(m.group(1))
        if not pkg:
            continue
        want = pkg.replace("icons.", "icons.automirrored.")
        if f"import {want}.{m.group(2)}" not in raw:
            problems.append((f.replace(SRC + "/", ""), m.group(2), f"{want}.{m.group(2)}"))
    for m in re.finditer(r"\bIcons\.(?!AutoMirrored)(\w+)\.(\w+)", code):
        pkg = ICON_SETS.get(m.group(1))
        if not pkg:
            continue
        if f"import {pkg}.{m.group(2)}" not in raw:
            problems.append((f.replace(SRC + "/", ""), m.group(2), f"{pkg}.{m.group(2)}"))

if problems:
    print(f"✗ {len(problems)} نمادِ ایمپورت‌نشده")
    for f, sym, path in problems[:25]:
        print(f"  {f:52} '{sym}'  →  import {path}")
    sys.exit(1)
print(f"✓ {len(files)} فایل — هر نمادِ کتابخانه‌ایِ شناخته‌شده ایمپورت شده")
