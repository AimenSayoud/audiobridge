import AppKit

/// Bridges the two shapes this app can take.
///
/// It is a normal windowed app by default — Dock icon, main window, the lot —
/// but it can be reduced to a menu-bar-only utility at runtime. That is done
/// with the activation policy rather than `LSUIElement` in Info.plist, so the
/// choice can be changed without reinstalling.
final class AppDelegate: NSObject, NSApplicationDelegate {

    private static let menuBarOnlyKey = "menuBarOnly"

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard UserDefaults.standard.bool(forKey: Self.menuBarOnlyKey) else { return }
        NSApp.setActivationPolicy(.accessory)
        // The Window scene opens one regardless; in menu-bar-only mode the user
        // has said they do not want it on launch.
        DispatchQueue.main.async {
            NSApp.windows.filter { $0.isVisible && $0.canBecomeMain }.forEach { $0.close() }
        }
    }

    /// Reopening a normal app shows its window, which AppKit does for us. In
    /// menu-bar-only mode there is no window and no Dock icon, so double
    /// clicking the app in Finder would otherwise do nothing at all — which is
    /// indistinguishable from it being broken.
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows: Bool) -> Bool {
        guard UserDefaults.standard.bool(forKey: Self.menuBarOnlyKey), !hasVisibleWindows else {
            return true
        }
        let alert = NSAlert()
        alert.messageText = "AudioBridge is running in your menu bar"
        alert.informativeText = "You have it set to menu-bar-only, so there is no Dock icon and no "
            + "window. Click the waveform icon near the clock.\n\n"
            + "Turn that off in General if you would rather have the full window back."
        alert.alertStyle = .informational
        if let icon = NSImage(systemSymbolName: "waveform.circle.fill", accessibilityDescription: nil) {
            alert.icon = icon
        }
        alert.addButton(withTitle: "OK")
        alert.addButton(withTitle: "Show the window")

        NSApp.activate(ignoringOtherApps: true)
        if alert.runModal() == .alertSecondButtonReturn {
            UserDefaults.standard.set(false, forKey: Self.menuBarOnlyKey)
            NSApp.setActivationPolicy(.regular)
            NSApp.activate(ignoringOtherApps: true)
        }
        return true
    }

    /// Closing the window is not quitting: the server keeps streaming, and the
    /// menu bar icon is still there.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
}
