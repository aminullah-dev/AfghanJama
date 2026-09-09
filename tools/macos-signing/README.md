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

هفت کار می‌کند: پیش‌نیازها را می‌سنجد، بسته را می‌سازد، فایل‌های بومی
(از جمله آن‌ها که داخلِ jar پنهان‌اند) و خودِ بسته را امضا می‌کند،
برنامه را به اپل می‌فرستد و مهرش می‌زند، از روی همان نسخه DMG می‌سازد و
امضا می‌کند، DMG را هم می‌فرستد و مهر می‌زند، و در آخر هر دو را با
`spctl` می‌سنجد.

**دو بار به اپل می‌رود، و این عمدی است** — دلیلش در بندِ «خودِ برنامه هم
مهر می‌خورد» پایین آمده. پس زمانِ انتظار دو برابر است.

خروجی:
`desktop/build/compose/binaries/main/dmg/KhayatYar-<نسخه>.dmg`

</div>

---

<div dir="rtl">

## شش دامی که بسته شده‌اند

سه‌تای اول از مستنداتِ اپل آمدند. سه‌تای آخر را **اجرای واقعی یاد
داد** و هر سه از یک جنس‌اند: چیزی که شبیهِ موفقیت است.

**`--deep` استفاده نمی‌شود.** اپل از ۲۰۱۹ منسوخش اعلام کرده و روی
بستهٔ جاوا نتیجه‌اش امضایی است که `codesign --verify` قبول می‌کند و
notarize ردش می‌کند. هر فایلِ بومی جدا امضا می‌شود و بسته در آخر.

**DMG بعد از امضا ساخته می‌شود، نه با `packageDmg`.** آن دستور
`.app` را از نو می‌سازد و امضا را دور می‌ریزد — بی هیچ خطایی. یک
DMGِ سالمِ بی‌امضا، که بدترین حالت است چون شبیهِ موفقیت است.

**`stapler` پریده نمی‌شود.** بی آن، مکِ کاربر باید برای تأییدِ
notarize به سرورِ اپل وصل شود؛ روی کمپیوترِ بی‌اینترنت — یعنی دقیقاً
کارگاهی که این برنامه برایش نوشته شده — همان هشدار برمی‌گردد.

**فایل‌های بومیِ داخلِ jar هم امضا می‌شوند.** اولین ارسالِ واقعی
`Invalid` برگشت با سه خطا:

```
…/skiko-awt-runtime-macos-arm64-….jar/libskiko-macos-x64.dylib
…/sqlite-bundled-jvm-….jar/natives/osx_x64/libsqliteJni.dylib
…/sqlite-bundled-jvm-….jar/natives/osx_arm64/libsqliteJni.dylib
"The binary is not signed with a valid Developer ID certificate."
```

سرویسِ اپل jarها را باز می‌کند و هر Mach-Oی داخلشان را هم می‌سنجد.
حلقه‌ای که روی فایل‌های دیسک راه می‌رود این‌ها را نمی‌بیند، و
`codesign --verify --deep --strict` هم سبز می‌مانَد چون برای او jar
فقط یک فایلِ داده است. حالا هر jar باز می‌شود، محتوایش با `file`
سنجیده می‌شود و فایلِ بومی‌اش سرِ جای خودش در بایگانی امضا شده
برمی‌گردد.

**خودِ برنامه هم مهر می‌خورد، نه فقط DMG.** اولین اجرای موفق این را
نشان داد:

```
$ xcrun stapler validate "/Volumes/KhayatYar 1.7.0/KhayatYar.app"
KhayatYar.app does not have a ticket stapled to it.
```

DMG مهر داشت و Gatekeeper قبولش می‌کرد. ولی کاربرِ مک برنامه را از
پنجرهٔ DMG روی `/Applications` می‌کشد، و آن نسخهٔ کپی‌شده هیچ مهری
ندارد — پس مک باید برای تأییدش به سرورِ اپل وصل شود. یعنی همان چیزی که
بندِ `stapler` قرار بود جلویش را بگیرد، این بار سرِ اجرای برنامه‌ای که
نصب شده و کار می‌کرده. حالا اول خودِ `.app` مهر می‌خورد و DMG بعد از آن
و از روی همان نسخه ساخته می‌شود.

**وضعیتِ پاسخِ اپل خوانده می‌شود، نه کدِ خروجِ `notarytool`.**
`notarytool submit --wait` وقتی اپل بسته را **رد** می‌کند هم با کدِ
صفر برمی‌گردد. در همان اجرای اول، وضعیت `Invalid` بود ولی اسکریپت
رد نشد و رفت سراغِ `stapler` — و آنچه کاربر دید `Error 65`ِ
`stapler` بود، نه سه dylibِ امضانشده که علتِ واقعی بودند. حالا
`status: Accepted` صریح وارسی می‌شود و اگر نبود، گزارشِ خودِ اپل
همان‌جا چاپ می‌شود.

</div>

---

<div dir="rtl">

## هشدارِ صادقانه

**تا آخر اجرا شده و اپل پذیرفته.**

روی macOS 26.6، Xcode 26.6، معماری arm64، با گواهیِ واقعیِ Developer
ID. خروجی‌اش این است:

```
status: Accepted
KhayatYar-1.7.0.dmg: accepted
source=Notarized Developer ID
origin=Developer ID Application: AMINULLAH HASHEMI (27RXPRW77S)
```

با قرنطینه هم سنجیده شد — یعنی فایل جوری نشانه‌گذاری شد که انگار از
اینترنت دانلود شده — و همان پاسخ آمد.

**زمانِ مرحلهٔ اپل تضمینی نیست.** در همان اجراها یک بار ۱۷ دقیقه طول
کشید و بارِ بعدی ۱ ساعت و ۴۸ دقیقه. صف است، نه خرابی؛ `--wait` تا
آخرش می‌مانَد. و چون حالا دو بار به اپل می‌رود (برنامه و DMG جدا)، این
زمان دو برابر می‌شود.

سه چیز در همان اجراها پیدا و درست شد: نبودنِ نمایهٔ جاکلیدی تا بندِ
آخر معلوم نمی‌شد، سه فایلِ بومیِ داخلِ jar اصلاً امضا نمی‌شدند، و مهرِ
تأیید به خودِ برنامه نمی‌چسبید. هر سه در بخشِ بالا توضیح داده‌اند.

اگر اپل رد کرد، دلیلش را با این می‌بینید:

```bash
xcrun notarytool history --keychain-profile KhayatYar
xcrun notarytool log <submission-id> --keychain-profile KhayatYar
```

متنش را بیاورید تا اصلاح شود.

</div>
