<div dir="rtl">

# امضا و notarize کردنِ نسخهٔ مک

بی این کار، مکِ مشتری برنامه را باز نمی‌کند و می‌گوید «از یک توسعه‌دهندهٔ
ناشناس است». با این کار، بی هیچ هشداری باز می‌شود.

**گواهی و رمزها هرگز از این مک بیرون نمی‌روند** — نه در مخزن، نه در
GitHub Secrets. همان قاعده‌ای که برای کلیدِ امضای اندروید و ویندوز
گذاشتیم. CI فقط بستهٔ بی‌امضا می‌سازد.

## چه لازم است

| | |
|---|---|
| حسابِ Apple Developer Program | دارید — سالانه C$119 |
| گواهیِ **Developer ID Application** | از Xcode یا developer.apple.com می‌گیرید |
| رمزِ ویژهٔ برنامه (app-specific password) | از appleid.apple.com |
| Xcode یا Command Line Tools | برای `codesign` و `notarytool` |

**چرا Developer ID و نه گواهی‌های دیگر:** برنامه‌ای که بیرون از App Store
پخش می‌شود فقط با این نوع گواهی مورد اعتماد است. «Apple Development» و
«Apple Distribution» برای App Store و آزمایش‌اند و اینجا کار نمی‌کنند.

## گامِ یک‌بار: گواهی

۱. Xcode → Settings → Accounts → حسابتان → Manage Certificates
۲. دکمهٔ **+** → **Developer ID Application**
۳. بسنجید که آمده:

```bash
security find-identity -v -p codesigning | grep "Developer ID Application"
```

باید چیزی مثلِ این ببینید:

```
1) A1B2C3... "Developer ID Application: Your Name (27RXPRW77S)"
```

آن رشتهٔ داخلِ گیومه همان چیزی است که به اسکریپت می‌دهید.

## گامِ یک‌بار: رمزِ ویژهٔ برنامه

رمزِ اصلیِ اپل را هرگز در خطِ فرمان ننویسید. به‌جایش:

۱. به `appleid.apple.com` بروید → Sign-In and Security → App-Specific Passwords
۲. یکی بسازید (مثلاً به نامِ «notarize»)
۳. در جاکلیدیِ مک ذخیره‌اش کنید تا فقط یک بار وارد شود:

```bash
xcrun notarytool store-credentials KhayatYar \
  --apple-id "ایمیلِ اپلِ شما" \
  --team-id 27RXPRW77S \
  --password "رمزِ-ویژه-ای-که-ساختید"
```

از این به بعد اسکریپت فقط نامِ `KhayatYar` را صدا می‌زند و رمز جایی
نوشته نمی‌شود.

## هر بار که نسخهٔ تازه می‌سازید

```bash
./gradlew :desktop:packageDmg
./tools/macos-signing/sign-and-notarize.sh "Developer ID Application: Your Name (27RXPRW77S)"
```

## ⚠ روی مک آزموده نشده

من مک و Xcode در دسترس ندارم. این اسکریپت از روی مستنداتِ اپل نوشته
شده و منطقش وارسی شده، ولی تا وقتی یک بار روی مکِ شما ندوَد، «آزموده»
نیست. اگر خطا داد، متنش را بفرستید.

</div>
