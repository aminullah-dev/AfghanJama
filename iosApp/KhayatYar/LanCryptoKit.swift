import CryptoKit
import Foundation
import Security

/// «کارگاهِ وصل» روی آیفون — **فقط CryptoKit و Keychainِ خودِ اپل.**
///
/// این فایل عمداً هیچ چیزی از کاتلین نمی‌شناسد تا روی مک هم جدا ساخته و
/// با بردارهای منتشرشده آزموده شود (`iosApp/CryptoInterop/run.sh`). پلِ
/// `LanCryptoBridge.swift` فقط همین تابع‌ها را به کاتلین می‌دهد.
///
/// قرارداد همان `LanPrims`ِ کاتلین است: کلیدِ AES ۳۲ بایت، nonce ۱۲ بایت،
/// برچسبِ ۱۶ بایتی چسبیده به انتهای متنِ رمز؛ کلیدِ عمومیِ P-256 به شکلِ
/// X9.63 (`04 ‖ X ‖ Y`، ۶۵ بایت). هر شکست `nil` است.
enum LanCryptoKit {

    static func gcmSeal(key: Data, nonce: Data, plain: Data, aad: Data) -> Data? {
        guard key.count == 32, nonce.count == 12,
              let n = try? AES.GCM.Nonce(data: nonce),
              let box = try? AES.GCM.seal(plain, using: SymmetricKey(data: key), nonce: n, authenticating: aad)
        else { return nil }
        return box.ciphertext + box.tag
    }

    static func gcmOpen(key: Data, nonce: Data, sealed: Data, aad: Data) -> Data? {
        guard key.count == 32, nonce.count == 12, sealed.count >= 16,
              let n = try? AES.GCM.Nonce(data: nonce),
              let box = try? AES.GCM.SealedBox(nonce: n, ciphertext: sealed.prefix(sealed.count - 16), tag: sealed.suffix(16))
        else { return nil }
        return try? AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: aad)
    }

    /// ۳۲ بایت کلیدِ خصوصیِ خام ‖ ۶۵ بایت کلیدِ عمومیِ X9.63.
    static func p256Generate() -> Data {
        let k = P256.KeyAgreement.PrivateKey()
        return k.rawRepresentation + k.publicKey.x963Representation
    }

    /// رازِ مشترکِ ۳۲ بایتی؛ `nil` اگر کلیدِ آن‌سو روی منحنی نیست (CryptoKit خودش می‌سنجد).
    static func p256Agree(privateRaw: Data, peerX963: Data) -> Data? {
        guard let mine = try? P256.KeyAgreement.PrivateKey(rawRepresentation: privateRaw),
              let peer = try? P256.KeyAgreement.PublicKey(x963Representation: peerX963),
              let shared = try? mine.sharedSecretFromKeyAgreement(with: peer)
        else { return nil }
        return shared.withUnsafeBytes { Data($0) }
    }

    // MARK: Keychain — کلیدِ دستگاه فقط اینجا، و فقط روی همین آیفون.

    private static let service = "com.afghanjama.khayatyar.hub"

    private static func query(_ name: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: name]
    }

    static func secretPut(name: String, value: Data) -> Bool {
        SecItemDelete(query(name) as CFDictionary)
        var q = query(name)
        q[kSecValueData as String] = value
        // پس از اولین باز شدنِ گوشی خوانا، و هرگز به آیفونِ دیگر (پشتیبانِ iCloud) نمی‌رود.
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(q as CFDictionary, nil) == errSecSuccess
    }

    static func secretGet(name: String) -> Data? {
        var q = query(name)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    static func secretDelete(name: String) {
        SecItemDelete(query(name) as CFDictionary)
    }
}
