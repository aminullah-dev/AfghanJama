<div dir="rtl">

# امضای نسخهٔ مک

DMGی که CI می‌سازد **بی‌امضا** است. این عمدی است: گواهیِ Developer ID
کلیدِ خصوصی دارد و اگر بیرون برود، هر کسی می‌تواند بدافزاری بسازد که
به نامِ شما امضا شده و روی هر مکی بی هیچ هشداری باز می‌شود. پس کلید
از کمپیوترِ شما بیرون نمی‌رود — نه در مخزن، نه در GitHub Secrets.
همان قاعده‌ای که برای `*.jks`ِ اندروید و `*.pfx`ِ ویندوز داریم.

نتیجه‌اش این است که امضا **روی مکِ خودتان** انجام می‌شود، با
`sign-and-notarize.sh` کنارِ همین فایل.

</div>

---

<div dir="rtl">

## بی‌امضا یعنی چه

مکِ کاربر DMGِ بی‌امضا را باز می‌کند ولی برنامه را اجرا نمی‌کند:
«KhayatYar را نمی‌توان باز کرد چون از یک توسعه‌دهندهٔ شناخته‌شده
نیست.» راهِ دور زدنش هست (راست‌کلیک ← Open، یا System Settings ←
Privacy & Security ← Open Anyway) ولی برای کارگاهی که قرار است
برنامه را نصب کند و کار کند، هر مانعی یک تماسِ پشتیبانی است.

با امضا و notarize، این پیام کلاً نمی‌آید.

</div>

---

<div dir="rtl">

## پیش‌نیازها — یک بار

**۱. حسابِ Apple Developer.** سالی ۹۹ دلار. بی آن گواهیِ
Developer ID صادر نمی‌شود و هیچ راهِ دیگری برای notarize نیست.

**۲. گواهیِ «Developer ID Application».**
در Xcode: `Settings → Accounts → [حسابتان] → Manage Certificates → +
→ Developer ID Application`.

دقت کنید کدام گواهی: `Apple Development` و `Apple Distribution` برای
App Store هستند و برنامه‌ای که مستقیم توزیع می‌شود با آن‌ها امضا
نمی‌شود. آنچه لازم است **Developer ID Application** است.

نامِ دقیقش را بعد از ساخت با این می‌بینید:

```bash
security find-identity -v -p codesigning
```

**۳. رمزِ ویژه (app-specific password).**
از [appleid.apple.com](https://appleid.apple.com) → Sign-In and
Security → App-Specific Passwords. رمزِ اصلیِ اپل کار نمی‌کند.

بعد یک بار در جاکلیدی ذخیره‌اش کنید تا در اسکریپت نیاید:

```bash
xcrun notarytool store-credentials KhayatYar \
  --apple-id "ایمیلِ اپلتان" \
  --team-id "TEAMID" \
  --password "رمزِ-ویژه"
```

`TEAMID` را در [developer.apple.com/account](https://developer.apple.com/account)
زیرِ Membership می‌بینید.

</div>

---

<div dir="rtl">

## اجرا

```bash
./tools/macos-signing/sign-and-notarize.sh \
  "Developer ID Application: NAME (TEAMID)"
```

اگر نامِ گواهی را ندهید، اسکریپت فهرستِ گواهی‌های موجود را چاپ می‌کند.

نامِ نمایهٔ جاکلیدی پیش‌فرض `KhayatYar` است. اگر جورِ دیگری ذخیره
کرده‌اید:

```bash
NOTARY_PROFILE=نامِ-دیگر ./tools/macos-signing/sign-and-notarize.sh "..."
```

شش کار می‌کند: بسته را می‌سازد، فایل‌های بومی و خودِ بسته را امضا
می‌کند، DMG می‌سازد و امضا می‌کند، به اپل می‌فرستد، و تأیید را به
فایل می‌چسباند. مرحلهٔ اپل چند دقیقه طول می‌کشد.

خروجی:
`desktop/build/compose/binaries/main/dmg/KhayatYar-<نسخه>.dmg`

</div>

---

<div dir="rtl">

## سه دامی که از قبل بسته شده‌اند

**`--deep` استفاده نمی‌شود.** اپل از ۲۰۱۹ منسوخش اعلام کرده و روی
بستهٔ جاوا نتیجه‌اش امضایی است که `codesign --verify` قبول می‌کند و
notarize ردش می‌کند. هر فایلِ بومی جدا امضا می‌شود و بسته در آخر.

**DMG بعد از امضا ساخته می‌شود، نه با `packageDmg`.** آن دستور
`.app` را از نو می‌سازد و امضا را دور می‌ریزد — بی هیچ خطایی. یک
DMGِ سالمِ بی‌امضا، که بدترین حالت است چون شبیهِ موفقیت است.

**`stapler` پریده نمی‌شود.** بی آن، مکِ کاربر باید برای تأییدِ
notarize به سرورِ اپل وصل شود؛ روی کمپیوترِ بی‌اینترنت — یعنی دقیقاً
کارگاهی که این برنامه برایش نوشته شده — همان هشدار برمی‌گردد.

</div>

---

<div dir="rtl">

## هشدارِ صادقانه

**تا مرحلهٔ اپل روی مک آزموده شده؛ خودِ مرحلهٔ اپل نه.**

آنچه واقعاً اجرا شد (macOS 26.6، Xcode 26.6، معماری arm64): ساختِ بسته،
پیدا کردن و امضای هر ۴۶ فایلِ Mach-Oِ داخلِ بسته، امضای خودِ بسته،
`codesign --verify --deep --strict` که سبز شد، ساختِ DMGِ ۱۵۰ مگابایتی
و امضای آن. همه گرفتند.

آنچه آزموده **نشد** بندهای ۵ و ۶ است — `notarytool` و `stapler` — چون
هر دو گواهیِ Developer ID می‌خواهند و آن گواهی روی این مک نبود. پس
اولین اجرای شما با گواهیِ واقعی هنوز ممکن است در آن دو بند چیزی
بگوید؛ معمولاً دربارهٔ فایلی در JVMِ همراه که امضایش جا افتاده.

یک چیز هم در همان اجرا درست شد: نبودنِ نمایهٔ جاکلیدی تا بندِ ۵ معلوم
نمی‌شد، یعنی بعد از تمامِ کارِ بالا. حالا هر دو پیش‌نیاز در ثانیهٔ اول
سنجیده می‌شوند.

اگر اپل رد کرد، دلیلش را با این می‌بینید:

```bash
xcrun notarytool history --keychain-profile KhayatYar
xcrun notarytool log <submission-id> --keychain-profile KhayatYar
```

متنش را بیاورید تا اصلاح شود.

</div>
