import SwiftUI
import UIKit
import composeApp

struct ComposeView: UIViewControllerRepresentable {
    typealias UIViewControllerType = UIViewController
    typealias Coordinator = Void

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
