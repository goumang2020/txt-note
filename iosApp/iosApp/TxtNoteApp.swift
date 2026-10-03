import SwiftUI
import TxtNote

@main
struct TxtNoteApp: App {
    var body: some Scene {
        WindowGroup { ComposeView().ignoresSafeArea(.all) }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
