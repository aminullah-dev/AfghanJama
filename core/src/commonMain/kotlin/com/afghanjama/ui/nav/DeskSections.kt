package com.afghanjama.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.Feature

/**
 * گروه‌های نوارِ کنارِ کامپیوتر — به همین ترتیب، از بالا به پایین.
 *
 * **چرا گروه.** تا ۱.۹.۱ نوار چهل‌وسه بندِ پشتِ‌سرِ‌هم بود، فقط متن، و
 * گروه‌ها فقط در توضیحِ کد بودند. کارفرما روی مک «اشتراکِ کارگاه» را در
 * آن پیدا نکرد. حالا هر بند زیرِ سرگروهِ خودش است و سرگروه جمع می‌شود.
 */
enum class DeskGroup(val title: String) {
    TODAY("امروز"),
    WORKSHOP("کارگاه"),
    STOCK("انبار و خرید"),
    SALES("فروش و پول"),
    PEOPLE("کارکنان"),
    BOOKS("گزارش و حساب"),
    SYSTEM("دستگاه‌ها و تنظیمات"),
}

/**
 * بخش‌های نوارِ کنارِ کامپیوتر (مک و ویندوز).
 *
 * **این فهرست تنها جایی است که بخش‌ها تعریف می‌شوند.** نوار به همین ترتیب
 * ساخته می‌شود و `screenSmoke` هم روی همین می‌گردد — پس بخشی نمی‌تواند
 * اضافه شود و از آزمون جا بماند.
 *
 * **چرا در `:core` و نه در `:desktop`.** خودِ نمایش در `:desktop` است؛ این
 * فقط داده است: نام، گروه، آیکن و اینکه چه کسی به آن راه دارد. اینجا
 * آزمون‌های واحدِ `:app` (`SectionGroupTest`) می‌توانند ببینندش — همان
 * کاری که با [RouteAccess] برای گوشی می‌شود.
 *
 * **ترتیب مهم است:** بندهای هر گروه پشتِ‌سرِ‌هم و گروه‌ها به ترتیبِ
 * [DeskGroup]. آزمون این را نگه می‌دارد، وگرنه یک سرگروه دو بار در نوار
 * می‌آمد. «اشتراکِ کارگاه» در گروهِ «امروز» و میانِ سه بندِ اول می‌ماند
 * (`deskscreens`)، همان جایی که کارفرما پیدایش می‌کند.
 */
enum class DeskSection(val title: String, val group: DeskGroup) {
    // امروز
    Home("خانه", DeskGroup.TODAY),
    ActionCenter("مرکزِ هشدار", DeskGroup.TODAY),
    WorkshopLink("کارگاهِ وصل", DeskGroup.TODAY),
    MyWork("کارِ من", DeskGroup.TODAY),

    // کارگاه — از سفارش تا تحویل
    Board("تختهٔ کار", DeskGroup.WORKSHOP),
    Inventory("سفارش‌ها", DeskGroup.WORKSHOP),
    OrderSearch("جست‌وجوی سفارش", DeskGroup.WORKSHOP),
    ProductionOrder("شروعِ تولید", DeskGroup.WORKSHOP),
    Cutting("برش", DeskGroup.WORKSHOP),
    Sewing("دوخت", DeskGroup.WORKSHOP),
    Review("نظارت", DeskGroup.WORKSHOP),
    DeliveryQueue("صفِ تحویل", DeskGroup.WORKSHOP),
    WorkshopLoad("بارِ کارگاه", DeskGroup.WORKSHOP),
    QuoteCalc("قیمت‌دهی", DeskGroup.WORKSHOP),

    // انبار و خرید
    Warehouse("انبارِ مواد", DeskGroup.STOCK),
    StockLedger("گردشِ انبار", DeskGroup.STOCK),
    FinishedWarehouse("انبارِ محصول", DeskGroup.STOCK),
    Procurement("خریدِ مواد", DeskGroup.STOCK),
    PurchasePlan("برنامهٔ خرید", DeskGroup.STOCK),
    PurchaseReturn("برگشتِ خرید", DeskGroup.STOCK),

    // فروش و پول
    NewSale("فروشِ نو", DeskGroup.SALES),
    DailyTrade("معاملاتِ روزمره", DeskGroup.SALES),
    Customers("خریداران", DeskGroup.SALES),
    FollowUp("پیگیریِ طلب", DeskGroup.SALES),
    Finance("مالی", DeskGroup.SALES),
    MoneyMove("جابه‌جاییِ پول", DeskGroup.SALES),
    Recurring("هزینه‌های ثابت", DeskGroup.SALES),

    // کارکنان
    Attendance("حضور و غیاب", DeskGroup.PEOPLE),
    Payroll("پرداختِ حقوق", DeskGroup.PEOPLE),
    Performance("کارکرد", DeskGroup.PEOPLE),

    // گزارش و حساب
    Ledger("دفترِ حساب", DeskGroup.BOOKS),
    Journal("دفتر روزنامه", DeskGroup.BOOKS),
    Reports("گزارش‌ها", DeskGroup.BOOKS),
    Documents("اسناد", DeskGroup.BOOKS),
    Audit("رسیدگی", DeskGroup.BOOKS),

    // دستگاه‌ها و تنظیمات
    Settings("تنظیمات", DeskGroup.SYSTEM),
    Backup("پشتیبان و بازیابی", DeskGroup.SYSTEM),
    Users("کاربران و دسترسی‌ها", DeskGroup.SYSTEM),
    MasterData("اطلاعات پایه", DeskGroup.SYSTEM),
    ShopProfile("پروفایل کارگاه", DeskGroup.SYSTEM),
    Licence("لایسنس", DeskGroup.SYSTEM),
    SelfTest("خودآزمایی و سلامتِ داده", DeskGroup.SYSTEM),
    SampleShop("کارگاهِ نمونه", DeskGroup.SYSTEM),
    Guide("راهنما", DeskGroup.SYSTEM),

    // نسخه، کامیت و اینکه این ساخت رسمی است یا آزمایشی.
    About("دربارهٔ خیاط‌یار", DeskGroup.SYSTEM),
}

/**
 * این نفر به این بند راه دارد؟ — همان تیک‌هایی که گوشی می‌خوانَد
 * ([RouteAccess]).
 *
 * `when` عمداً کامل است و `else` ندارد: بخشِ تازه‌ای که کسی فراموش کند
 * اینجا تصمیمش را بگیرد، کامپایل نمی‌شود — نه اینکه بی‌صدا به همه نشان
 * داده شود.
 *
 * «خانه» و «تنظیمات» برای همه‌اند، مثلِ گوشی (`RouteAccess.ALWAYS`)؛ خودِ
 * این دو صفحه هر کارت را با تیکِ خودش نشان می‌دهند. بقیه همان قاعدهٔ ۱.۹.۱
 * است و `SectionGroupTest` آن را بند به بند نگه می‌دارد.
 */
fun DeskSection.allowedFor(access: Access): Boolean {
    if (access.isManager) return true
    val need: Feature = when (this) {
        DeskSection.Home, DeskSection.Settings, DeskSection.OrderSearch, DeskSection.MyWork,
        DeskSection.WorkshopLink, DeskSection.QuoteCalc, DeskSection.Guide, DeskSection.Licence,
        DeskSection.About -> return true
        DeskSection.ActionCenter, DeskSection.Audit -> Feature.AUDIT
        DeskSection.Board, DeskSection.WorkshopLoad -> Feature.BOARD
        DeskSection.Inventory, DeskSection.ProductionOrder -> Feature.ORDERS
        DeskSection.Cutting -> Feature.CUTTING
        DeskSection.Sewing -> Feature.SEWING
        DeskSection.Review -> Feature.REVIEW
        DeskSection.DeliveryQueue -> Feature.DELIVERY
        DeskSection.Warehouse, DeskSection.StockLedger -> Feature.WAREHOUSE
        DeskSection.FinishedWarehouse, DeskSection.NewSale -> Feature.SALES
        DeskSection.Procurement, DeskSection.PurchasePlan, DeskSection.PurchaseReturn -> Feature.PROCUREMENT
        DeskSection.DailyTrade, DeskSection.Finance, DeskSection.MoneyMove, DeskSection.Documents,
        DeskSection.Reports, DeskSection.Journal, DeskSection.Recurring -> Feature.FINANCE
        DeskSection.Customers, DeskSection.FollowUp -> Feature.CUSTOMERS
        DeskSection.Ledger -> Feature.LEDGER
        DeskSection.Payroll, DeskSection.Performance -> Feature.PAYROLL
        DeskSection.Attendance -> Feature.ATTENDANCE
        DeskSection.MasterData -> Feature.MASTER
        DeskSection.Backup -> Feature.BACKUP
        // فقط مدیر
        DeskSection.ShopProfile, DeskSection.SelfTest, DeskSection.SampleShop,
        DeskSection.Users -> return false
    }
    return access.has(need)
}

/**
 * آیکنِ هر بند — همان آیکنی که کاشیِ همان کار روی خانهٔ گوشی دارد، هر جا
 * که هست، تا چشم روی دو سکو یک نشانه را بشناسد.
 *
 * `when` کامل است؛ بندِ بی‌آیکن کامپایل نمی‌شود.
 */
val DeskSection.icon: ImageVector
    get() = when (this) {
        DeskSection.Home -> Icons.Default.Home
        DeskSection.ActionCenter -> Icons.Default.NotificationsActive
        DeskSection.WorkshopLink -> Icons.Default.Wifi
        DeskSection.MyWork -> Icons.Default.AssignmentInd
        DeskSection.Board -> Icons.Default.Monitor
        DeskSection.Inventory -> Icons.Default.Checkroom
        DeskSection.OrderSearch -> Icons.Default.Search
        DeskSection.ProductionOrder -> Icons.Default.PostAdd
        DeskSection.Cutting -> Icons.Default.ContentCut
        DeskSection.Sewing -> Icons.Default.Gesture
        DeskSection.Review -> Icons.Default.FactCheck
        DeskSection.DeliveryQueue -> Icons.Default.LocalShipping
        DeskSection.WorkshopLoad -> Icons.Default.Speed
        DeskSection.QuoteCalc -> Icons.Default.Calculate
        DeskSection.Warehouse -> Icons.Default.Warehouse
        DeskSection.StockLedger -> Icons.Default.History
        DeskSection.FinishedWarehouse -> Icons.Default.Sell
        DeskSection.Procurement -> Icons.Default.ShoppingCart
        DeskSection.PurchasePlan -> Icons.Default.AddShoppingCart
        DeskSection.PurchaseReturn -> Icons.AutoMirrored.Filled.AssignmentReturn
        DeskSection.NewSale -> Icons.Default.PointOfSale
        DeskSection.DailyTrade -> Icons.Default.SwapHoriz
        DeskSection.Customers -> Icons.Default.Group
        DeskSection.FollowUp -> Icons.Default.PhoneInTalk
        DeskSection.Finance -> Icons.Default.Payments
        DeskSection.MoneyMove -> Icons.Default.CurrencyExchange
        DeskSection.Recurring -> Icons.Default.EventRepeat
        DeskSection.Attendance -> Icons.Default.Fingerprint
        DeskSection.Payroll -> Icons.Default.Badge
        DeskSection.Performance -> Icons.Default.WorkspacePremium
        DeskSection.Ledger -> Icons.Default.AccountBalance
        DeskSection.Journal -> Icons.AutoMirrored.Filled.MenuBook
        DeskSection.Reports -> Icons.Default.Assessment
        DeskSection.Documents -> Icons.Default.Description
        DeskSection.Audit -> Icons.Default.Policy
        DeskSection.Settings -> Icons.Default.Settings
        DeskSection.Backup -> Icons.Default.Backup
        DeskSection.Users -> Icons.Default.ManageAccounts
        DeskSection.MasterData -> Icons.Default.Tune
        DeskSection.ShopProfile -> Icons.Default.Storefront
        DeskSection.Licence -> Icons.Default.VerifiedUser
        DeskSection.SelfTest -> Icons.Default.HealthAndSafety
        DeskSection.SampleShop -> Icons.Default.Science
        DeskSection.Guide -> Icons.AutoMirrored.Filled.HelpOutline
        DeskSection.About -> Icons.Default.Info
    }

/**
 * ترجمهٔ مسیرِ گوشی به بندِ نوارِ کنار.
 *
 * هشدارهای «مرکزِ هشدار» در `:core` ساخته می‌شوند و هر کدام یک `Routes.…`
 * همراه دارند — رشته‌ای که روی گوشی به `NavController` می‌رود. کامپیوتر
 * `NavController` ندارد، پس همان رشته اینجا به بند تبدیل می‌شود.
 *
 * `null` یعنی این مقصد روی کامپیوتر نیست؛ کلیک کاری نمی‌کند، که بهتر از
 * بردن به صفحهٔ اشتباه است.
 *
 * تا ۱.۹.۱ «تنظیمات» اینجا `null` بود و هشدارِ پشتیبان («هنوز هیچ بکاپی
 * گرفته نشده»)، که به تنظیمات می‌برد، روی کامپیوتر هیچ نمی‌کرد.
 */
fun deskSectionForRoute(route: String): DeskSection? = when (route) {
    Routes.HOME -> DeskSection.Home
    Routes.PRODUCTION_ORDER -> DeskSection.ProductionOrder
    Routes.PROCUREMENT -> DeskSection.Procurement
    Routes.LEDGER -> DeskSection.Ledger
    Routes.DELIVERY_QUEUE -> DeskSection.DeliveryQueue
    Routes.PAYROLL -> DeskSection.Payroll
    Routes.PURCHASE_PLAN -> DeskSection.PurchasePlan
    Routes.INVENTORY -> DeskSection.Inventory
    Routes.WAREHOUSE -> DeskSection.Warehouse
    Routes.STOCK_LEDGER -> DeskSection.StockLedger
    Routes.FINISHED_SALES -> DeskSection.FinishedWarehouse
    Routes.CUSTOMERS -> DeskSection.Customers
    Routes.DEBT_FOLLOW_UP -> DeskSection.FollowUp
    Routes.PERFORMANCE -> DeskSection.Performance
    Routes.NEW_SALE -> DeskSection.NewSale
    Routes.PURCHASE_RETURN -> DeskSection.PurchaseReturn
    Routes.DAILY_TRADE -> DeskSection.DailyTrade
    Routes.FINANCE -> DeskSection.Finance
    Routes.REPORTS -> DeskSection.Reports
    Routes.JOURNAL -> DeskSection.Journal
    Routes.DOCUMENTS -> DeskSection.Documents
    Routes.AUDIT -> DeskSection.Audit
    Routes.SAMPLE_WORKSHOP -> DeskSection.SampleShop
    Routes.USERS -> DeskSection.Users
    Routes.SEWING -> DeskSection.Sewing
    Routes.REVIEW -> DeskSection.Review
    Routes.MY_WORK -> DeskSection.MyWork
    Routes.WORKSHOP_LOAD -> DeskSection.WorkshopLoad
    Routes.CUTTING -> DeskSection.Cutting
    Routes.ATTENDANCE -> DeskSection.Attendance
    Routes.RECURRING -> DeskSection.Recurring
    Routes.ACTION_CENTER -> DeskSection.ActionCenter
    // هشدارِ «درخواست‌های منتظر» از مرکزِ هشدار به همین‌جا می‌آید.
    Routes.WORKSHOP_LINK -> DeskSection.WorkshopLink
    Routes.SETTINGS -> DeskSection.Settings
    Routes.MASTER -> DeskSection.MasterData
    Routes.LICENCE -> DeskSection.Licence
    Routes.SELF_TEST -> DeskSection.SelfTest
    Routes.BOARD -> DeskSection.Board
    Routes.SEARCH -> DeskSection.OrderSearch
    Routes.GUIDE -> DeskSection.Guide
    Routes.QUOTE_CALC -> DeskSection.QuoteCalc
    Routes.PAY, Routes.RECEIVE -> DeskSection.MoneyMove
    else -> null
}
