package com.afghanjama.licence

import platform.UIKit.UIDevice

/**
 * سهمِ آیفون — `identifierForVendor`.
 *
 * برای همهٔ اپ‌های یک سازنده روی یک گوشی یکی است و تا وقتی دستِ‌کم
 * یکی از آن‌ها نصب باشد عوض نمی‌شود. پاک کردن و نصبِ دوباره‌اش را عوض
 * می‌کند — آن‌وقت لایسنس به مهلتِ «دستگاهِ تازه» می‌رود، نه به قفل.
 */
actual fun platformMachineId(): String? =
    UIDevice.currentDevice.identifierForVendor?.UUIDString
