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
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose خودش صفحه‌کلید را جابه‌جا می‌کند؛ بی این،
                // آیفون یک بار دیگر هم بالا می‌بردش و ورودیِ رمز دو
                // برابر بالا می‌پرید.
                .ignoresSafeArea(.keyboard)
        }
    }
}
