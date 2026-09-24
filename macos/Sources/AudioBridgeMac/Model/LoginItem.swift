import AppKit
import Foundation
import ServiceManagement

/// Launch at login. A menu-bar utility that has to be started by hand every
/// morning is one you stop using by Thursday.
enum LoginItem {

    static var isEnabled: Bool {
        SMAppService.mainApp.status == .enabled
    }

    /// Registration is refused for an app that is not where macOS expects one,
    /// so the failure is surfaced rather than swallowed — silently not being a
    /// login item is worse than being told why.
    static func setEnabled(_ enabled: Bool) throws {
        if enabled {
            try SMAppService.mainApp.register()
        } else {
            try SMAppService.mainApp.unregister()
        }
    }

    static var explanation: String {
        switch SMAppService.mainApp.status {
        case .enabled: return "Starts with macOS."
        case .requiresApproval: return "Approve it in System Settings › General › Login Items."
        case .notFound: return "Move AudioBridge to /Applications first."
        default: return "Start AudioBridge when you log in."
        }
    }
}

enum PrivacySettings {
    static func open(_ pane: Pane) {
        guard let url = URL(string: pane.urlString) else { return }
        NSWorkspace.shared.open(url)
    }

    enum Pane {
        case microphone
        case screenRecording
        case loginItems

        var urlString: String {
            switch self {
            case .microphone:
                return "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone"
            case .screenRecording:
                return "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture"
            case .loginItems:
                return "x-apple.systempreferences:com.apple.LoginItems-Settings.extension"
            }
        }
    }
}
