import Foundation
import KhayatYarKit

/// پلی که کاتلین برای AES-GCM، P-256 و Keychain صدا می‌زند (`IosLanBridge.crypto`).
/// هر تابع یک خط به `LanCryptoKit` است؛ منطقی اینجا نیست.
final class LanCryptoBridge: NSObject, IosLanCrypto {
    func gcmSeal(key: Data, nonce: Data, plain: Data, aad: Data) -> Data? {
        LanCryptoKit.gcmSeal(key: key, nonce: nonce, plain: plain, aad: aad)
    }

    func gcmOpen(key: Data, nonce: Data, sealed: Data, aad: Data) -> Data? {
        LanCryptoKit.gcmOpen(key: key, nonce: nonce, sealed: sealed, aad: aad)
    }

    func p256Generate() -> Data? {
        LanCryptoKit.p256Generate()
    }

    func p256Agree(privateRaw: Data, peerX963: Data) -> Data? {
        LanCryptoKit.p256Agree(privateRaw: privateRaw, peerX963: peerX963)
    }

    func secretPut(name: String, value: Data) -> Bool {
        LanCryptoKit.secretPut(name: name, value: value)
    }

    func secretGet(name: String) -> Data? {
        LanCryptoKit.secretGet(name: name)
    }

    func secretDelete(name: String) {
        LanCryptoKit.secretDelete(name: name)
    }
}
