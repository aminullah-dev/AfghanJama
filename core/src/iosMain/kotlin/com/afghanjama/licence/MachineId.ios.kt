package com.afghanjama.licence

import platform.Foundation.NSThread
import platform.UIKit.UIDevice
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

/**
 * سهمِ آیفون — `identifierForVendor`.
 *
 * برای همهٔ اپ‌های یک سازنده روی یک گوشی یکی است و تا وقتی دستِ‌کم
 * یکی از آن‌ها نصب باشد عوض نمی‌شود. پاک کردن و نصبِ دوباره‌اش را عوض
 * می‌کند — آن‌وقت لایسنس به مهلتِ «دستگاهِ تازه» می‌رود، نه به قفل.
 *
 * `UIDevice` مالِ نخِ اصلی است، و [Licensing] این را روی نخِ پس‌زمینه
 * می‌پرسد (روی ویندوز و مک خواندنِ شناسه یک فرمانِ سیستم است و نباید
 * پنجره را نگه دارد). پس اگر روی نخِ اصلی نیستیم، همان یک خط روی نخِ
 * اصلی خوانده می‌شود.
 */
actual fun platformMachineId(): String? {
    if (NSThread.isMainThread) return read()
    var id: String? = null
    dispatch_sync(dispatch_get_main_queue()) { id = read() }
    return id
}

private fun read(): String? = UIDevice.currentDevice.identifierForVendor?.UUIDString
