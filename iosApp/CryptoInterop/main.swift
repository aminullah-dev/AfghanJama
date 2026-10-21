import Foundation

// همان بردارهای منتشرشده‌ای که `LanCryptoTest`ِ جاوا می‌سنجد — پس آیفون و
// کامپیوتر برای یک ورودی دقیقاً یک بایت‌ها را می‌سازند.

func hex(_ s: String) -> Data {
    var d = Data()
    var i = s.startIndex
    while i < s.endIndex {
        let j = s.index(i, offsetBy: 2)
        d.append(UInt8(s[i..<j], radix: 16)!)
        i = j
    }
    return d
}

func toHex(_ d: Data) -> String { d.map { String(format: "%02x", $0) }.joined() }

var failed = 0
func check(_ name: String, _ ok: Bool) {
    print(ok ? "OK    \(name)" : "FAIL  \(name)")
    if !ok { failed += 1 }
}

// GCM — سندِ GCM، مورد ۱۳، ۱۴ و ۱۶
let zeroKey = Data(count: 32), zeroIv = Data(count: 12)
check("GCM case 13", toHex(LanCryptoKit.gcmSeal(key: zeroKey, nonce: zeroIv, plain: Data(), aad: Data())!) == "530f8afbc74536b9a963b4f1c4cb738b")
check("GCM case 14", toHex(LanCryptoKit.gcmSeal(key: zeroKey, nonce: zeroIv, plain: Data(count: 16), aad: Data())!) ==
      "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919")
let k = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
let iv = hex("cafebabefacedbaddecaf888")
let p = hex("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
let a = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
let sealed = LanCryptoKit.gcmSeal(key: k, nonce: iv, plain: p, aad: a)!
check("GCM case 16 seal", toHex(sealed) ==
      "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
      "76fc6ece0f4e1768cddf8853bb2d551b")
check("GCM case 16 open", LanCryptoKit.gcmOpen(key: k, nonce: iv, sealed: sealed, aad: a) == p)
var bad = sealed; bad[bad.count - 1] ^= 1
check("GCM refuses a flipped tag bit", LanCryptoKit.gcmOpen(key: k, nonce: iv, sealed: bad, aad: a) == nil)
check("GCM refuses other associated data", LanCryptoKit.gcmOpen(key: k, nonce: iv, sealed: sealed, aad: Data([1])) == nil)
check("GCM refuses a short key", LanCryptoKit.gcmSeal(key: Data(count: 16), nonce: iv, plain: p, aad: a) == nil)

// ECDH — RFC 5903 §8.1
let i = hex("C88F01F510D9AC3F70A292DAA2316DE544E9AAB8AFE84049C62A9C57862D1433")
let gr = hex("04D12DFB5289C8D4F81208B70270398C342296970A0BCCB74C736FC7554494BF6356FBF3CA366CC23E8157854C13C58D6AAC23F046ADA30F8353E74F33039872AB")
check("ECDH RFC 5903", toHex(LanCryptoKit.p256Agree(privateRaw: i, peerX963: gr) ?? Data()) ==
      "d6840f6b42f6edafd13116e0e12565202fef8e9ece7dce03812464d04b9442de")
let pair1 = LanCryptoKit.p256Generate(), pair2 = LanCryptoKit.p256Generate()
check("a key pair is 32 + 65 bytes, uncompressed", pair1.count == 97 && pair1[32] == 4)
let s12 = LanCryptoKit.p256Agree(privateRaw: pair1.prefix(32), peerX963: pair2.suffix(65))
let s21 = LanCryptoKit.p256Agree(privateRaw: pair2.prefix(32), peerX963: pair1.suffix(65))
check("both sides agree", s12 != nil && s12 == s21 && s12!.count == 32)
var off = Data(gr); off[64] ^= 1
check("a point off the curve is refused", LanCryptoKit.p256Agree(privateRaw: i, peerX963: off) == nil)

print(failed == 0 ? "crypto interop verified" : "\(failed) crypto interop checks FAILED")
exit(failed == 0 ? 0 : 1)
