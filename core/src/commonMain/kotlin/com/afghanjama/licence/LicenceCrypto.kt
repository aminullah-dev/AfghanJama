package com.afghanjama.licence

/*
 * سنجشِ امضای کلید — **تنها کارِ رمزنگاریِ لایسنس، و تنها چیزی که سکو
 * باید بدهد.**
 *
 * ECDSA روی منحنیِ P-256 با SHA-256، امضا به شکلِ DER. همان چیزی که:
 *
 * - جاوا و اندروید با `Signature.getInstance("SHA256withECDSA")` دارند
 *   (روی اندروید از API ۲۴ — همان `minSdk`ِ این اپ — بی هیچ کتابخانه‌ای)،
 * - اپل با `kSecKeyAlgorithmECDSASignatureMessageX962SHA256` دارد،
 * - و `cryptography`ِ پایتون در ابزارِ صدور تولید می‌کند.
 *
 * چکیدهٔ SHA-256 برای کدِ دستگاه از قبل در `util/PinCrypto` بود و همان
 * به کار می‌رود؛ دو راه برای یک کار نمی‌سازیم.
 *
 * [spkiDer] کلیدِ عمومی به شکلِ SubjectPublicKeyInfo (بدنهٔ فایلِ PEM).
 * هر خطایی — کلیدِ خراب، امضای خراب — `false` است، نه استثنا: از دیدِ
 * کاربر هر دو یعنی «این کلید پذیرفته نیست».
 */
internal expect fun verifyEcdsaP256Sha256(
    spkiDer: ByteArray,
    message: ByteArray,
    derSignature: ByteArray,
): Boolean
