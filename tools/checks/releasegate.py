#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""انتشار فقط از یک تگ — امضای مک و `release.yml` هر دو.

**وضعی که از آن آمدیم.** تا ۱.۹.۰ سه بسته‌ای که خودشان را «۱.۹.۰»
می‌نامیدند از سه کامیتِ متفاوت بودند. DMGِ مک را
`sign-and-notarize.sh` از هر شاخه و هر درختی امضا و مهر می‌کرد، پس
ساختِ آزمایشی هم با مهرِ اپل «رسمی» دیده می‌شد.

۱.۹.۱ دو نگهبان گذاشت و این بررسی نمی‌گذارد بی‌صدا برداشته شوند:

  ۱. `tools/macos-signing/sign-and-notarize.sh` پیش از ساخت و پیش از
     دست زدن به جاکلیدی می‌سنجد که HEAD روی تگِ `v<appVersion>` است و
     درخت تمیز، و بعد از ساخت و پیش از اولین `codesign` با
     `verify-build.py` می‌سنجد که بسته همان کامیت را با کانالِ `release`
     می‌گوید.
  ۲. `.github/workflows/release.yml` روی تگِ `v*` راه می‌افتد، دروازه
     (`gate.py --tag`) را پیش از هر ساختی می‌زند، و هر سه بسته (اندروید،
     مک، ویندوز) را با `verify-build.py` می‌سنجد.

و دو چیز که وارسیِ درختِ تمیز را از بیرون بی‌اثر می‌کردند: متغیرِ
`GIT_DIR`/`GIT_WORK_TREE` (گیت یک کپیِ تمیزِ دیگر را می‌سنجید) و
`status.showUntrackedFiles=no` (فایلِ تازه دیده نمی‌شد). اسکریپتِ امضا
`GIT_*` را پاک می‌کند و فایلِ skip-worktree را هم رد می‌کند، و `git status`
در اسکریپت، در `BuildInfo` و در دروازه `--untracked-files=normal` می‌گیرد. شرطِ کانالِ `BuildInfo` هم (تگ و درختِ
تمیز) اینجا نگه داشته می‌شود.

ترتیب مهم است، نه فقط بودن: نگهبانی که بعد از امضا بیاید فقط دیر خبر
می‌دهد.
"""
import re
import sys

import _src

SIGN = _src.REPO / "tools/macos-signing/sign-and-notarize.sh"
RELEASE = _src.REPO / ".github/workflows/release.yml"
GATE = _src.REPO / "tools/release/gate.py"
VERIFY = _src.REPO / "tools/release/verify-build.py"
CORE = _src.REPO / "core/build.gradle.kts"

for p in (SIGN, RELEASE, GATE, VERIFY, CORE):
    if not p.exists():
        print(f"✗ {p.relative_to(_src.REPO)} نیست — بررسی پوچ می‌شد.")
        sys.exit(1)

problems = []


def code_only(text):
    """خط‌های غیرِ توضیح — توضیح‌ها حق دارند از همین فرمان‌ها حرف بزنند."""
    return "\n".join(l for l in text.splitlines() if not l.lstrip().startswith("#"))


# ---- ۱) اسکریپتِ امضای مک ----
sign = code_only(SIGN.read_text(encoding="utf-8"))


def first(pattern):
    m = re.search(pattern, sign)
    return m.start() if m else None


tag_check = first(r'rev-parse -q --verify "refs/tags/\$TAG"')
head_check = first(r'"\$HEAD_SHA" != "\$TAG_SHA"')
clean_check = first(r'status --porcelain')
keychain = first(r"security find-identity")
build = first(r":desktop:createDistributable")
verify = first(r"tools/release/verify-build\.py[^\n]*\\?\n?[^\n]*--channel release")
codesign = first(r"\bcodesign --force")

for name, pos in (
    ("وارسیِ بودنِ تگِ v<appVersion>", tag_check),
    ("وارسیِ HEAD = تگ", head_check),
    ("وارسیِ درختِ تمیز (git status --porcelain)", clean_check),
    ("وارسیِ بسته با verify-build.py --channel release", verify),
):
    if pos is None:
        problems.append(f"sign-and-notarize.sh — {name} نیست")

# راهِ دورزدن از بیرون: `GIT_DIR`/`GIT_WORK_TREE` گیت را به یک کپیِ تمیزِ
# دیگر می‌فرستد، و `status.showUntrackedFiles=no` (در تنظیم یا در
# `GIT_CONFIG_PARAMETERS`) فایلِ تازه را از `git status` پنهان می‌کند.
env_reset = first(r"compgen -e \| grep '\^GIT_'")
first_git = first(r"\bgit -C ")
hidden_check = first(r"ls-files -v")
if env_reset is None:
    problems.append(
        "sign-and-notarize.sh — متغیرهای GIT_* پاک نمی‌شوند؛ با GIT_DIR وارسی "
        "یک کپیِ تمیزِ دیگر را می‌سنجد"
    )
elif first_git is not None and env_reset > first_git:
    problems.append("sign-and-notarize.sh — پاک‌کردنِ GIT_* باید پیش از اولین فرمانِ گیت بیاید")
if not re.search(r"status --porcelain --untracked-files=(?:normal|all)\b", sign):
    problems.append(
        "sign-and-notarize.sh — git status بی --untracked-files؛ با "
        "status.showUntrackedFiles=no فایلِ تازه دیده نمی‌شود"
    )
if hidden_check is None:
    problems.append(
        "sign-and-notarize.sh — فایل‌های skip-worktree و assume-unchanged "
        "(git ls-files -v) سنجیده نمی‌شوند"
    )

if None not in (tag_check, head_check, clean_check, hidden_check, keychain, build):
    guard_end = max(tag_check, head_check, clean_check, hidden_check)
    if guard_end > keychain:
        problems.append(
            "sign-and-notarize.sh — وارسیِ تگ و درخت باید پیش از جاکلیدی "
            "(security find-identity) بیاید"
        )
    if guard_end > build:
        problems.append(
            "sign-and-notarize.sh — وارسیِ تگ و درخت باید پیش از ساختِ بسته بیاید"
        )
if None not in (verify, build, codesign):
    if not (build < verify < codesign):
        problems.append(
            "sign-and-notarize.sh — verify-build.py باید بعد از ساخت و پیش از "
            "اولین codesign بیاید"
        )

# راهِ دورزدن: متغیری که وارسی را خاموش کند.
if re.search(r"\b(SKIP|FORCE|ALLOW)_[A-Z_]*\b", sign):
    problems.append("sign-and-notarize.sh — راهِ دورزدنِ وارسیِ تگ نباید باشد")

# ---- ۲) release.yml ----
rel = code_only(RELEASE.read_text(encoding="utf-8"))
if not re.search(r"^\s*tags:\s*\[\s*[\"']v\*[\"']\s*\]", rel, re.M):
    problems.append("release.yml — روی تگِ v* راه نمی‌افتد")
if "tools/release/gate.py --tag" not in rel:
    problems.append("release.yml — دروازه را با --tag نمی‌زند")

jobs = {}
for m in re.finditer(r"^  (\w+):\n(.*?)(?=^  \w+:\n|\Z)", rel, re.M | re.S):
    jobs[m.group(1)] = m.group(2)
for job in ("android", "macos", "windows"):
    body = jobs.get(job)
    if body is None:
        problems.append(f"release.yml — کارِ «{job}» نیست")
        continue
    if "needs: gate" not in body:
        problems.append(f"release.yml — «{job}» پیش از دروازه می‌دود (needs: gate نیست)")
    if "tools/release/verify-build.py" not in body:
        problems.append(f"release.yml — «{job}» بسته‌اش را با verify-build.py نمی‌سنجد")
    if "fetch-depth: 0" not in body:
        problems.append(
            f"release.yml — «{job}» بی fetch-depth: 0؛ ساخت تگ را نمی‌بیند و "
            f"کانال «test build» می‌شود"
        )
if "xcodebuild" in rel and "archive" in rel:
    problems.append("release.yml — آرشیوِ آیفون اینجا جایی ندارد؛ CI امضا ندارد")

# ---- ۳) قاعدهٔ کانال در `BuildInfo` و دروازه ----
#
# «release» فقط روی تگ و با درختِ تمیز. اگر `!isDirty` از شرط بیفتد، هیچ
# آزمونی روی شاخه نمی‌شکند و APKی که در Android Studio با فایلِ ثبت‌نشده
# ساخته شود «ساختِ رسمی» می‌گوید.
core = re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", CORE.read_text(encoding="utf-8"), flags=re.S))
channel_line = next((l for l in core.splitlines() if re.search(r"\bval channel\s*=", l)), "")
for need in ("tagged.get()", "!isDirty", '"release"'):
    if need not in channel_line:
        problems.append(f"core/build.gradle.kts — شرطِ کانالِ BuildInfo «{need}» را ندارد")
if '"--exact-match"' not in core:
    problems.append("core/build.gradle.kts — تگ با git describe --exact-match سنجیده نمی‌شود")
status_call = re.search(r'git\(([^)]*"status"[^)]*)\)', core)
if not status_call or '"--untracked-files=normal"' not in status_call.group(1):
    problems.append(
        "core/build.gradle.kts — git status در BuildInfo بی --untracked-files=normal؛ "
        "با status.showUntrackedFiles=no فایلِ تازه دیده نمی‌شود"
    )
if '"--untracked-files=normal"' not in GATE.read_text(encoding="utf-8"):
    problems.append("tools/release/gate.py — git status بی --untracked-files=normal")

if problems:
    print(f"✗ {len(problems)} ایراد در نگهبانِ انتشار")
    for p in problems:
        print(f"  {p}")
    sys.exit(1)

print(
    "✓ انتشار فقط از تگ: امضای مک پیش از جاکلیدی و ساخت تگ و درخت را "
    "می‌سنجد، و release.yml هر سه بسته را با همان کامیت می‌سنجد"
)
