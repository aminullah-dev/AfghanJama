#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ساخت یا به‌روزرسانیِ صفحهٔ وردپرس از راهِ REST API — بی مرورگر.

**چرا این راه، نه مرورگر.** یک سشنِ محلیِ Claude Code به اینترنتِ
واقعیِ کاربر وصل است، ولی «مرورگر» ندارد مگر ابزارِ تازه‌ای برایش نصب
شود — و آن ابزار هم باید به نشستِ واقعیِ وردپرسِ کاربر در کروم برسد که
کارِ ساده‌ای نیست. REST API یک HTTP معمولی است؛ `curl` یا `requests`
کافی است، بی نیاز به مرورگر یا نشست.

**رمزِ برنامه (Application Password) چیست.** وردپرس از نسخهٔ ۵٫۶ یک
رمزِ جداگانه برای برنامه‌ها می‌سازد — نه رمزِ اصلیِ حساب. قابلِ
لغوشدن، محدود به همین کار، و اگر لو برود رمزِ اصلی دست‌نخورده می‌ماند.
ساختنش: پیشخوانِ وردپرس ← Users ← Profile ← پایینِ صفحه، «Application
Passwords».

**این اسکریپت را کجا اجرا کنید.** نه اینجا — این محیط به هیچ سایتی
جز چند مقصدِ مشخص راه ندارد. روی کمپیوترِ خودتان، در همین پوشه.

استفاده:
    python3 tools/wordpress/publish.py \\
        --slug tailor-erp \\
        --title "خیاط‌یار" \\
        --file docs/site/tailor-erp-fa.html

اگر صفحه‌ای با همین نامک (slug) از قبل هست، **به‌روزرسانی** می‌شود؛
وگرنه تازه ساخته می‌شود. هیچ صفحه‌ای را پاک نمی‌کند.

اعتبارنامه از کجا می‌آید — به ترتیبِ اولویت:
    1. متغیرهای محیطی: WP_URL، WP_USER، WP_APP_PASSWORD
    2. فایلِ wp-credentials.json کنارِ همین اسکریپت (نمونه‌اش را
       ببینید: wp-credentials.example.json). این فایل در .gitignore
       است — هرگز کامیت نشود.
"""
import argparse
import json
import pathlib
import sys
import urllib.error
import urllib.request
from base64 import b64encode

HERE = pathlib.Path(__file__).resolve().parent


def load_credentials() -> dict:
    import os

    url = os.environ.get("WP_URL")
    user = os.environ.get("WP_USER")
    app_password = os.environ.get("WP_APP_PASSWORD")
    if url and user and app_password:
        return {"url": url, "user": user, "app_password": app_password}

    cred_file = HERE / "wp-credentials.json"
    if cred_file.exists():
        data = json.loads(cred_file.read_text(encoding="utf-8"))
        missing = [k for k in ("url", "user", "app_password") if not data.get(k)]
        if missing:
            sys.exit(f"✗ {cred_file.name}: کلیدهای {missing} خالی‌اند.")
        return data

    sys.exit(
        "✗ اعتبارنامه پیدا نشد.\n"
        "  یا متغیرهای WP_URL / WP_USER / WP_APP_PASSWORD را بگذارید،\n"
        f"  یا {cred_file} را بسازید — نمونه‌اش کنارِ همین فایل است:\n"
        f"  {HERE / 'wp-credentials.example.json'}"
    )


def _call(creds: dict, method: str, path: str, body: dict | None = None):
    """یک تماسِ REST — و اگر شکست، **متنِ خطای خودِ وردپرس** را نشان می‌دهد.

    بی این، شکست‌ها فقط «HTTP 401» می‌گویند و کاربر نمی‌فهمد رمز
    غلط بوده یا کاربر یا مسیر. وردپرس در بدنهٔ خطا پیامِ خواندنی
    می‌گذارد (`rest_cannot_create` و مانندش)؛ آن پیام مهم‌تر از
    کدِ HTTP است.

    **هر دو نوعِ خطا از یک نقطه رد می‌شوند** — هم `HTTPError` (سرور
    جواب داد، ولی با شکست) و هم `URLError` (اصلاً به سرور نرسیدیم).
    اولین نسخهٔ این اسکریپت این دو تابع را جدا نوشته بود و فقط یکی
    از آن دو خطا را می‌گرفت؛ نتیجه‌اش traceback خامِ پایتون در حالتی
    بود که فقط نشانی غلط بود — دقیقاً حالتی که کاربرِ تازه‌کار
    بیشترین احتمال را دارد بهش بخورد.
    """
    base = creds["url"].rstrip("/")
    token = b64encode(f"{creds['user']}:{creds['app_password']}".encode()).decode()
    req = urllib.request.Request(
        f"{base}/wp-json/wp/v2/{path}",
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        method=method,
        headers={
            "Authorization": f"Basic {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(detail).get("message", detail)
        except json.JSONDecodeError:
            pass
        sys.exit(f"✗ وردپرس {e.code} برگرداند: {detail}")
    except urllib.error.URLError as e:
        sys.exit(
            f"✗ به {base} نرسیدیم: {e.reason}\n"
            "  نشانیِ WP_URL را بررسی کنید — باید ریشهٔ سایت باشد،\n"
            "  نه نشانیِ خودِ صفحه."
        )


def wp_request(creds: dict, method: str, path: str, body: dict | None = None) -> dict:
    return _call(creds, method, path, body)


def find_page_by_slug(creds: dict, slug: str) -> dict | None:
    results = _call(creds, "GET", f"pages?slug={slug}&status=publish,draft,private")
    return results[0] if results else None


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--slug", required=True, help="نامکِ صفحه — بخشِ آخرِ نشانی")
    ap.add_argument("--title", required=True, help="عنوانِ صفحه")
    ap.add_argument("--file", required=True, type=pathlib.Path,
                     help="فایلِ HTMLِ محتوا (مثلِ docs/site/tailor-erp-fa.html)")
    ap.add_argument("--status", default="draft", choices=["draft", "publish", "private"],
                     help="پیش‌فرض draft — تا خودتان با چشم ببینید و بعد publish کنید")
    args = ap.parse_args()

    if not args.file.exists():
        sys.exit(f"✗ فایل نیست: {args.file}")
    content = args.file.read_text(encoding="utf-8")

    creds = load_credentials()
    existing = find_page_by_slug(creds, args.slug)

    body = {"title": args.title, "slug": args.slug, "content": content, "status": args.status}

    if existing:
        result = wp_request(creds, "POST", f"pages/{existing['id']}", body)
        print(f"✓ صفحه به‌روز شد: {result.get('link', '(بی‌لینک)')}")
    else:
        result = wp_request(creds, "POST", "pages", body)
        print(f"✓ صفحهٔ تازه ساخته شد: {result.get('link', '(بی‌لینک)')}")

    print(f"  وضعیت: {result.get('status')}")
    print(f"  شناسه: {result.get('id')}")


if __name__ == "__main__":
    main()
