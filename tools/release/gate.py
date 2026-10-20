#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""دروازهٔ انتشار — پیش از ساختنِ هر بسته‌ای از یک تگ.

**چرا هست.** تا ۱.۹.۰ هیچ‌چیز نمی‌گفت نسخهٔ تازه واقعاً از قبلی بالاتر
است. `tools/checks/appversion.py` خودش این را گفته بود: «به تاریخچه نیاز
دارد و اینجا نیست». نتیجه‌اش هم دیده شد: سه ساختِ متفاوت، هر سه «۱.۹.۰».
ویندوز MSIِ هم‌عدد را بی‌صدا روی نصب‌شده نمی‌نشاند و اندروید APKِ
هم‌کد را اصلاً نصب نمی‌کند.

این اسکریپت تاریخچه را دارد (گیت) و چهار چیز را می‌سنجد:

  ۱. تگ دقیقاً `v<appVersion>` است و روی همین HEAD نشسته.
  ۲. `appVersion` از نسخهٔ بزرگ‌ترین تگِ قبلی بالاتر است.
  ۳. `appVersionCode` از کدِ همان تگ (خوانده از `gradle.properties`ِ
     خودِ آن تگ) بالاتر است.
  ۴. درخت تمیز است. `release.yml` بعد از دروازه «release» را قول
     می‌دهد، و `BuildInfo` با یک فایلِ ثبت‌نشده «test build» می‌گوید؛
     پس دروازه‌ای که روی درختِ ناتمیز سبز شود، قولی می‌دهد که ساخت
     نگه نمی‌دارد.

بی `--tag` (اجرای دستی یا آزمایشی) بندهای ۱ و ۴ را نمی‌خواهد ولی ۲ و ۳
را می‌سنجد، و اگر `v<appVersion>` روی کامیتِ دیگری از قبل باشد رد
می‌کند: یعنی این نسخه پیش‌تر از کدِ دیگری منتشر شده.

تگی که سه بخشِ عددی نیست (مثلِ `v1.9.1-rc1`) ریلیزِ قبلی شمرده نمی‌شود،
و به‌عنوانِ `--tag` هم رد می‌شود چون `v<appVersion>` نیست.

خروجی برای `release.yml` در `GITHUB_OUTPUT`: `version`، `code`،
`commit` (`git rev-parse --short=7`، همان فرمانِ `BuildInfo`: هفت حرف یا
بیشتر، اگر هفت حرف در مخزن یکتا نباشد)، `channel` (`release` یا
`test build`، همان قاعدهٔ `BuildInfo`) و `previous`.

آزمونش `tools/checks/gaterun.py` است: همین فایل را در مخزن‌های گیتِ
دورریختنی اجرا می‌کند.

اجرا:
    python3 tools/release/gate.py --tag v1.9.1
    python3 tools/release/gate.py            # بی تگ، پیش از تگ زدن
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

REPO = Path(__file__).resolve().parents[2]
SEMVER = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")


def git(*args):
    """خروجیِ گیت، یا `None` اگر فرمان شکست."""
    r = subprocess.run(
        ["git", "-C", str(REPO), *args],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    return r.stdout.strip() if r.returncode == 0 else None


def props(text):
    out = {}
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def ver(s):
    m = SEMVER.fullmatch(s or "")
    return tuple(int(x) for x in m.groups()) if m else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", help="تگی که انتشار از آن است، مثلِ v1.9.1")
    args = ap.parse_args()

    cur = props((REPO / "gradle.properties").read_text(encoding="utf-8"))
    version = cur.get("appVersion", "")
    code = cur.get("appVersionCode", "")
    problems = []

    if ver(version) is None:
        problems.append(f"appVersion='{version}' سه بخشِ عددی نیست")
    if not re.fullmatch(r"[1-9]\d*", code):
        problems.append(f"appVersionCode='{code}' عددِ صحیحِ مثبت نیست")
    if problems:
        return report(problems)

    head = git("rev-parse", "HEAD")
    if head is None:
        return report(["گیت HEAD را نشناخت — این پوشه مخزنِ گیت نیست؟"])
    own_tag = f"v{version}"

    # ---- ۱) تگ همان نسخه است و روی همین کامیت ----
    if args.tag is not None:
        if args.tag != own_tag:
            problems.append(
                f"تگِ {args.tag} با appVersion={version} جور نیست؛ "
                f"تگ باید {own_tag} باشد"
            )
        tag_sha = git("rev-parse", f"{args.tag}^{{commit}}")
        if tag_sha is None:
            problems.append(f"تگِ {args.tag} در این مخزن نیست")
        elif tag_sha != head:
            problems.append(f"تگِ {args.tag} روی HEAD نیست ({tag_sha[:7]} ≠ {head[:7]})")

    # این نسخه پیش‌تر از کامیتِ دیگری تگ خورده؟
    existing = git("rev-parse", f"{own_tag}^{{commit}}")
    if existing is not None and existing != head:
        problems.append(
            f"{own_tag} از قبل روی کامیتِ دیگری است ({existing[:7]}). "
            f"این نسخه منتشر شده؛ appVersion و appVersionCode را بالا ببرید"
        )

    # ---- ۲ و ۳) بالاتر از ریلیزِ قبلی ----
    tags = [t for t in (git("tag", "--list", "v*") or "").split() if ver(t[1:])]
    others = [t for t in tags if t not in (own_tag, args.tag)]
    previous = max(others, key=lambda t: ver(t[1:])) if others else None
    if previous is not None:
        if ver(version) <= ver(previous[1:]):
            problems.append(
                f"appVersion={version} از ریلیزِ قبلی ({previous}) بالاتر نیست"
            )
        old = props(git("show", f"{previous}:gradle.properties") or "")
        old_code = old.get("appVersionCode", "")
        if re.fullmatch(r"\d+", old_code):
            if int(code) <= int(old_code):
                problems.append(
                    f"appVersionCode={code} از کدِ {previous} ({old_code}) بالاتر "
                    f"نیست؛ گوشی آپدیت را نصب نمی‌کند"
                )
        else:
            print(f"  (کدِ {previous} خوانده نشد؛ فقط نسخه سنجیده شد)")

    # همان قاعدهٔ `BuildInfo` در `core/build.gradle.kts`.
    tagged = git("describe", "--exact-match", "--tags", "--match", own_tag, "HEAD") is not None
    status = git("--no-optional-locks", "status", "--porcelain", "--untracked-files=normal")
    clean = status == ""
    channel = "release" if tagged and clean else "test build"

    # ---- ۴) انتشار از تگ فقط با درختِ تمیز ----
    #
    # تا اینجا دروازه روی درختِ ناتمیز هم سبز می‌شد و فقط می‌نوشت
    # `channel=test build`. ولی `release.yml` روی تگ «release» قول می‌دهد،
    # پس آن خروجی یا ساخت را با پیامِ گنگی در `verify-build.py` می‌انداخت،
    # یا بدتر: بسته‌ای «آزمایشی» از روی تگِ نسخه.
    if args.tag is not None and not clean:
        if status is None:
            problems.append("git status شکست؛ تمیز بودنِ درخت معلوم نیست")
        else:
            dirty = status.splitlines()
            problems.append(
                f"درخت تمیز نیست ({len(dirty)} فایلِ تغییرکرده یا ثبت‌نشده، مثلِ "
                f"{dirty[0].strip()})؛ انتشار از تگ فقط با درختِ تمیز"
            )

    # همان فرمانِ `BuildInfo`، نه `head[:7]`. گیت اگر هفت حرف یکتا نباشد
    # بیشتر می‌دهد؛ برش به هفت حرف آن وقت با کامیتِ داخلِ بسته جور نمی‌شد و
    # `verify-build.py` ساختِ درست را رد می‌کرد.
    short = git("rev-parse", "--short=7", "HEAD") or ""
    if not re.fullmatch(r"[0-9a-f]{7,}", short):
        problems.append(f"کامیتِ کوتاهِ '{short}' دستِ‌کم هفت حرفِ هگز نیست")

    print(f"  نسخه      {version} ({code})")
    print(f"  کامیت     {short}")
    print(f"  کانال     {channel}")
    print(f"  ریلیزِ قبل {previous or '(هیچ)'}")

    out = os.environ.get("GITHUB_OUTPUT")
    if out and not problems:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"version={version}\ncode={code}\ncommit={short}\n")
            f.write(f"channel={channel}\nprevious={previous or ''}\n")

    return report(problems, f"✓ {own_tag} از {previous or 'هیچ ریلیزی'} بالاتر است")


def report(problems, ok=None):
    if problems:
        print(f"✗ {len(problems)} ایراد — انتشار نمی‌شود:")
        for p in problems:
            print(f"  {p}")
        return 1
    print(ok)
    return 0


if __name__ == "__main__":
    sys.exit(main())
