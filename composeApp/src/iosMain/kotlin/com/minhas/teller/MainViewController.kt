package com.minhas.teller

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point for iOS UIKit / SwiftUI integration.
 * Wraps the Compose Multiplatform UI hierarchy in a native UIViewController.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    TellerApp()
}
