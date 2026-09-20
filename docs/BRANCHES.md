<div dir="rtl">

# شاخه‌ها — کدام زنده است و کدام نه

**آخرین وارسی: ۲۰ سپتامبر ۲۰۲۶، بعد از انتشارِ ۱.۸.۰.**
آن روز هر ۲۲ شاخه یکی‌یکی با `git diff --stat` سنجیده شد.
**نتیجه: هیچ کارِ نرفته‌ای در هیچ شاخه‌ای نمانده. `main` همه‌چیز را دارد.**

## ⛔ سه شاخه را **ادغام نکنید**

این سه از `main`های کهنه شاخه خورده‌اند. گیت‌هاب برایشان «کامیتِ
ادغام‌نشده» نشان می‌دهد و آدم فکر می‌کند کاری آنجا مانده — نمانده.
ادغامشان فقط پاک می‌کند:

| شاخه | شناسه | ادغامش چه می‌کند |
|---|---|---|
| `claude/windows-parity` | `5b9b699` | ۵۶٬۲۴۵ خط در ۴۰۳ فایل پاک می‌شود |
| `claude/macos-autodetect` | `a01a072` | ۱۷٬۴۲۹ خط در ۳۰۸ فایل پاک می‌شود |
| `claude/notify-customer` | `e24b477` | ۱۷٬۱۵۶ خط در ۳۰۸ فایل پاک می‌شود |

    git diff --stat origin/main origin/claude/windows-parity
    → 403 files changed, 2879 insertions(+), 56245 deletions(-)

**عددِ کامیت را باور نکنید؛ `git diff --stat` را باور کنید.** محتوای
آن کامیت‌ها در `main` هست، فقط شناسه‌هایشان فرق دارد چون تاریخچه یک
بار فشرده شد.

### ولی دو تایشان یک تکهٔ واقعی داشتند

`macos-autodetect` و `notify-customer` هر دو کامیتِ ۱۹ سپتامبر
داشتند، یعنی تازه بودند. هر دو وارسی شدند:

- **`notify-customer` (`e24b477`)** — اصلاحِ چیدمانِ سه دکمه در
  صفحهٔ تحویل. **از قبل در `main` بود**؛ همان `maxLines = 1` و همان
  ردیفِ دوم، با توضیحی که خودِ اشکال را نام می‌برد.

- **`macos-autodetect` (`a01a072`)** — پیدا کردنِ خودکارِ گواهی در
  اسکریپتِ امضا. **در `main` نبود.** خودِ شاخه ادغام نشد (۱۷هزار خط
  پاک می‌کرد)؛ فقط همان تکه دستی به اسکریپتِ فعلی منتقل شد و در
  `bec0063` نشست. READMEاش عمداً منتقل **نشد**: READMEی `main`
  تازه‌تر است و از قبل مستند کرده که
  `security find-identity | head -1` گواهیِ غلط برمی‌دارد — همان
  اشکالی که در READMEی آن شاخه هست.

پس حالا هر سه شاخه واقعاً مرده‌اند. کاری که با آن‌ها باید کرد:
**پاکشان کنید.** چیزی از دست نمی‌رود.

    git push origin --delete claude/windows-parity
    git push origin --delete claude/macos-autodetect
    git push origin --delete claude/notify-customer

اگر پشیمان شدید، تا مدتی از همان شناسه برمی‌گردند:

    git push origin 5b9b699:refs/heads/claude/windows-parity

---

## شاخه‌های تمام‌شده

این ۱۹ شاخه کارشان به `main` رسیده و گیت هم تأییدشان می‌کند
(`git branch -r --merged origin/main`). ماندنشان ضرری ندارد، پاک
کردنشان هم چیزی کم نمی‌کند:

| شاخه | شناسه |
|---|---|
| `claude/bottom-nav-five` | `b4aeeee` |
| `claude/bug-check-fix-toovr0` | `f36ab53` |
| `claude/bump-1.8.0` | `a8d4c35` |
| `claude/coral-palette` | `a6defe5` |
| `claude/customer-installments` | `698cdef` |
| `claude/delivery-release-docs` | `8cf163b` |
| `claude/download-links-and-branch-warning` | `4b8f3ee` |
| `claude/ios-app` | `a1e27f8` |
| `claude/macos-build` | `94c7570` |
| `claude/macos-dmg-signing-loogiq` | `949d54c` |
| `claude/motion-system` | `a24bdee` |
| `claude/preview` | `84b891c` |
| `claude/pricing-calculator` | `035e4b2` |
| `claude/recurring-expenses` | `9e7348e` |
| `claude/settings-capital-shared` | `c2747af` |
| `claude/windows-cutting-attendance` | `7bc13c4` |
| `claude/wordpress-publish` | `131af10` |
| `claude/workcost-add-button` | `9eaf50a` |
| `claude/workshop-load` | `b0409e2` |

## شاخهٔ زنده

`main` — همیشه جلوترین. هر چیزی که در اپ می‌بینید از اینجاست.

---

## چرا این فایل هست

کارفرما پرسید «انگار عقب می‌رویم، نکند شاخه‌ها کاری کرده‌اند؟» — و
پرسیدنش بجا بود. شش شاخه در مخزن نشسته بود که از بیرون هیچ‌کدام
نمی‌گفتند زنده‌اند یا مرده، و یکی‌شان ادغامش یک ماه کار را پاک می‌کرد.

اگر شاخهٔ تازه‌ای ساختید و کارش تمام شد، یا اینجا ثبتش کنید یا
پاکش کنید. شاخه‌ای که نه ثبت است نه پاک، روزی همین سؤال را دوباره
می‌سازد.

</div>
