#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""دروازهٔ انتشار، اجرا شده — نه خوانده.

**چرا هست.** `tools/release/gate.py` تنها چیزی است که می‌گوید نسخهٔ
تازه از ریلیزِ قبلی بالاتر است، و هیچ آزمونی نداشت. `releasegate` فقط
می‌پرسد دروازه در `release.yml` صدا زده می‌شود یا نه؛ اینکه درست
می‌سنجد را نمی‌پرسد. اگر کسی `<=` را در مقایسهٔ کد به `<` عوض می‌کرد،
هر بررسی سبز می‌ماند و APKی با همان `versionCode` از دروازه می‌گذشت —
همان APKی که گوشیِ کارگاه نصبش نمی‌کند.

اینجا خودِ `gate.py` (همین فایلِ مخزن، کپی‌شده) در مخزن‌های گیتِ
دورریختنی اجرا می‌شود، هر کدام با تاریخچه و تگ‌های خودش:

  الف) کدِ برابر با ریلیزِ قبلی رد می‌شود (و کدِ بالاتر می‌گذرد).
  ب) نسخهٔ پایین‌تر از ریلیزِ قبلی رد می‌شود.
  ج) اولین تگ، بی هیچ ریلیزِ قبلی، می‌گذرد و `release` می‌گوید.
  د) تگی که سه بخشِ عددی نیست ریلیزِ قبلی شمرده نمی‌شود، و به‌عنوانِ
     `--tag` رد می‌شود.
  ه) تگی که روی HEAD نیست رد می‌شود.
  و) درختِ ناتمیز با `--tag` رد می‌شود (بی `--tag` فقط «test build»).

هر رد با **متنِ ایرادِ خودش** سنجیده می‌شود، نه فقط با کدِ خروج: در (ه)
ایرادِ دیگری هم هست و آزمونی که فقط «رد شد» را بپرسد، با برداشتنِ
وارسیِ HEAD هم سبز می‌ماند.

گیتِ این مخزن‌ها از تنظیمِ کاربر و سیستم جداست (`GIT_CONFIG_GLOBAL`،
`GIT_CONFIG_NOSYSTEM`) و `GIT_*`ِ بیرون پاک می‌شود، تا امضای اجباریِ
کامیت یا `GIT_DIR`ِ رانر نتیجه را عوض نکند. `GITHUB_OUTPUT` هم به فایلِ
موقت می‌رود: در `release.yml` این بررسی بعد از دروازه می‌دود و نباید در
خروجیِ آن پله بنویسد.
"""
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

import _src

GATE = _src.REPO / "tools/release/gate.py"
if not GATE.exists():
    print(f"✗ {GATE.relative_to(_src.REPO)} نیست — بررسی پوچ می‌شد.")
    sys.exit(1)

fails = []
ran = 0


def check(cond, msg):
    if not cond:
        fails.append(msg)


class Repo:
    """یک مخزنِ گیتِ دورریختنی با کپیِ همین `gate.py`."""

    def __init__(self, root, name):
        self.dir = root / name
        self.out = root / f"{name}.out"
        self.dir.mkdir()
        cfg = root / "gitconfig"
        cfg.touch()
        self.env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
        self.env.update({
            "GIT_CONFIG_GLOBAL": str(cfg),
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_AUTHOR_NAME": "gaterun",
            "GIT_AUTHOR_EMAIL": "gaterun@example.invalid",
            "GIT_COMMITTER_NAME": "gaterun",
            "GIT_COMMITTER_EMAIL": "gaterun@example.invalid",
            "PYTHONIOENCODING": "utf-8",
        })
        self.git("init", "-q")
        dest = self.dir / "tools/release/gate.py"
        dest.parent.mkdir(parents=True)
        shutil.copyfile(GATE, dest)

    def git(self, *args):
        r = subprocess.run(
            ["git", "-C", str(self.dir), *args],
            capture_output=True, text=True, encoding="utf-8", errors="replace", env=self.env,
        )
        if r.returncode != 0:
            raise RuntimeError(f"git {' '.join(args)}: {r.stderr.strip()}")
        return r.stdout.strip()

    def release(self, version, code, tag=None):
        """`gradle.properties` با این نسخه، یک کامیت، و اگر خواسته شد تگ."""
        (self.dir / "gradle.properties").write_text(
            f"# آزمونِ دروازه\nappVersion={version}\nappVersionCode={code}\n",
            encoding="utf-8",
        )
        self.git("add", "-A")
        self.git("commit", "-q", "--allow-empty", "-m", f"{version} ({code})")
        if tag:
            self.git("tag", tag)

    def gate(self, *args):
        """`(کدِ خروج، متن، خروجی‌های GITHUB_OUTPUT)`."""
        global ran
        ran += 1
        self.out.write_text("", encoding="utf-8")
        r = subprocess.run(
            [sys.executable, str(self.dir / "tools/release/gate.py"), *args],
            capture_output=True, text=True, encoding="utf-8", errors="replace",
            env={**self.env, "GITHUB_OUTPUT": str(self.out)},
        )
        outputs = dict(
            line.split("=", 1)
            for line in self.out.read_text(encoding="utf-8").splitlines() if "=" in line
        )
        return r.returncode, (r.stdout or "") + (r.stderr or ""), outputs


def rejected(name, result, needle):
    rc, text, outputs = result
    check(rc == 1, f"{name}: باید رد می‌شد (کدِ خروج {rc})\n{text}")
    check(needle in text, f"{name}: ایرادِ «{needle}» گفته نشد\n{text}")
    check(not outputs, f"{name}: ردشده ولی در GITHUB_OUTPUT نوشت: {outputs}")


def passed(name, result, channel=None):
    rc, text, outputs = result
    check(rc == 0, f"{name}: باید می‌گذشت (کدِ خروج {rc})\n{text}")
    if channel is not None:
        check(outputs.get("channel") == channel,
              f"{name}: کانال باید «{channel}» باشد، شد {outputs.get('channel')!r}")
    return outputs


def remove_tree(path):
    # گیت فایل‌های `objects/` را فقط‌خواندنی می‌سازد و `rmtree` روی ویندوز
    # سرشان می‌ایستد؛ اول نوشتنی‌شان کن.
    for root, dirs, files in os.walk(path):
        for name in dirs + files:
            try:
                os.chmod(os.path.join(root, name), stat.S_IWRITE | stat.S_IREAD | stat.S_IEXEC)
            except OSError:
                pass
    shutil.rmtree(path, ignore_errors=True)


root = Path(tempfile.mkdtemp(prefix="gaterun-"))
try:
    # ---- الف) کدِ برابر رد می‌شود، کدِ بالاتر می‌گذرد ----
    r = Repo(root, "equal-code")
    r.release("1.0.0", 5, tag="v1.0.0")
    r.release("1.0.1", 5, tag="v1.0.1")
    rejected("کدِ برابر", r.gate("--tag", "v1.0.1"), "appVersionCode=5")
    # همان مخزن، کد یکی بالاتر: ردِ بالا از کد بود، نه از چیزِ دیگر.
    r.release("1.0.2", 6, tag="v1.0.2")
    passed("کدِ بالاتر", r.gate("--tag", "v1.0.2"), channel="release")

    # ---- ب) نسخهٔ پایین‌تر رد می‌شود ----
    r = Repo(root, "lower-version")
    r.release("1.2.0", 3, tag="v1.2.0")
    r.release("1.1.9", 4, tag="v1.1.9")
    rejected("نسخهٔ پایین‌تر", r.gate("--tag", "v1.1.9"), "appVersion=1.1.9")
    # کد بالاتر است، پس تنها ایراد همان نسخه است. بی تگ هم همان.
    rejected("نسخهٔ پایین‌تر، بی تگ", r.gate(), "appVersion=1.1.9")

    # ---- ج) اولین تگ ----
    r = Repo(root, "first-tag")
    r.release("1.0.0", 1, tag="v1.0.0")
    out = passed("اولین تگ", r.gate("--tag", "v1.0.0"), channel="release")
    check(out.get("previous") == "", f"اولین تگ: ریلیزِ قبلی باید خالی باشد، شد {out.get('previous')!r}")
    check(out.get("version") == "1.0.0" and out.get("code") == "1",
          f"اولین تگ: نسخه و کد درست نوشته نشد: {out}")
    # همان فرمانِ `BuildInfo`؛ هفت حرف یا بیشتر.
    short = r.git("rev-parse", "--short=7", "HEAD")
    check(out.get("commit") == short, f"کامیت باید {short} باشد (همان BuildInfo)، شد {out.get('commit')!r}")
    check(re.fullmatch(r"[0-9a-f]{7,}", out.get("commit", "")) is not None,
          f"کامیت هفت حرفِ هگز یا بیشتر نیست: {out.get('commit')!r}")

    # ---- د) تگِ غیرِ سه‌بخشی ----
    r = Repo(root, "non-semver")
    r.release("1.0.0", 1, tag="v1.0.0")
    # اگر این‌ها ریلیز شمرده شوند، «بزرگ‌ترین» یا پوچ است یا ۹۹.
    for t in ("v99.0.0-rc1", "v2.0", "vnext", "v01.0.0"):
        r.git("tag", t)
    r.release("1.0.1", 2, tag="v1.0.1")
    out = passed("تگِ غیرِ سه‌بخشی در تاریخچه", r.gate("--tag", "v1.0.1"), channel="release")
    check(out.get("previous") == "v1.0.0",
          f"ریلیزِ قبلی باید v1.0.0 باشد، نه تگِ غیرِ سه‌بخشی؛ شد {out.get('previous')!r}")
    # به‌عنوانِ `--tag`: روی همان HEAD، درخت تمیز، ولی نامش v1.0.1 نیست.
    r.git("tag", "v1.0.1-rc1")
    rejected("--tag غیرِ سه‌بخشی", r.gate("--tag", "v1.0.1-rc1"), "تگ باید v1.0.1 باشد")

    # ---- ه) تگ روی HEAD نیست ----
    r = Repo(root, "tag-not-head")
    r.release("1.0.0", 1, tag="v1.0.0")
    r.release("1.0.1", 2, tag="v1.0.1")
    r.release("1.0.1", 2)  # کامیتِ بعد از تگ، همان نسخه
    rejected("تگ روی HEAD نیست", r.gate("--tag", "v1.0.1"), "روی HEAD نیست")

    # ---- و) درختِ ناتمیز ----
    r = Repo(root, "dirty")
    r.release("1.0.0", 1, tag="v1.0.0")
    r.release("1.0.1", 2, tag="v1.0.1")
    passed("درختِ تمیز", r.gate("--tag", "v1.0.1"), channel="release")
    (r.dir / "notes.txt").write_text("یادداشت\n", encoding="utf-8")
    rejected("فایلِ ثبت‌نشده با --tag", r.gate("--tag", "v1.0.1"), "درخت تمیز نیست")
    # بی `--tag` اجرای آزمایشی است: می‌گذرد ولی «release» نمی‌گوید.
    passed("فایلِ ثبت‌نشده بی تگ", r.gate(), channel="test build")
    (r.dir / "notes.txt").unlink()
    (r.dir / "gradle.properties").write_text(
        "appVersion=1.0.1\nappVersionCode=2\n# ویرایشِ ثبت‌نشده\n", encoding="utf-8"
    )
    rejected("فایلِ تغییرکرده با --tag", r.gate("--tag", "v1.0.1"), "درخت تمیز نیست")
finally:
    remove_tree(root)

if ran == 0:
    print("✗ دروازه یک بار هم اجرا نشد — بررسی پوچ بود")
    sys.exit(1)

if fails:
    print(f"✗ {len(fails)} ایراد در دروازهٔ انتشار ({ran} اجرا)")
    for f in fails:
        print("  -", f.replace("\n", "\n    "))
    sys.exit(1)

print(
    f"✓ دروازهٔ انتشار در {ran} اجرا روی مخزن‌های دورریختنی: کدِ برابر، نسخهٔ "
    f"پایین‌تر، تگِ غیرِ سه‌بخشی، تگِ بیرون از HEAD و درختِ ناتمیز رد می‌شوند"
)
