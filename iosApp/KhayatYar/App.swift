import SwiftUI
import UIKit
import KhayatYarKit

/// پلی که صفحهٔ Composeِ `:core` را به SwiftUI می‌دهد.
///
/// عمداً هیچ منطقی اینجا نیست: هر خطی که در Swift نوشته شود فقط روی
/// آیفون اجرا می‌شود و هیچ آزمونی از سکوهای دیگر پوششش نمی‌دهد.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosAppKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

@main
struct KhayatYarApp: App {
    init() {
        // «کارگاهِ وصل»: AES-GCM، P-256 و Keychain فقط از Swift دیده می‌شوند؛
        // کاتلین همین پل را صدا می‌زند. هیچ منطقی اینجا نیست.
        IosLanBridge.shared.crypto = LanCryptoBridge()
        HubSecrets.shared.store = IosSecretStore()
        PhoneName.shared.current = UIDevice.current.name
        PhoneName.shared.platform = "ios"
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                // QRِ «افزودنِ گوشی» (`kylink2:`) که دوربینِ خودِ آیفون خواند؛
                // صفحهٔ «کارگاهِ وصل» پیش از هر اتصالی از کاربر می‌پرسد.
                .onOpenURL { url in IosAppKt.openHubLink(raw: url.absoluteString) }
                // Compose خودش صفحه‌کلید را جابه‌جا می‌کند؛ بی این،
                // آیفون یک بار دیگر هم بالا می‌بردش و ورودیِ رمز دو
                // برابر بالا می‌پرید.
                .ignoresSafeArea(.keyboard)
        }
    }
}
