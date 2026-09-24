import SwiftUI

struct TethertoneApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var model: ServerModel

    init() {
        let model = ServerModel()
        _model = StateObject(wrappedValue: model)
        // Deferred: SwiftUI is still assembling the scene at init time, and
        // capture touches AVFoundation and the main run loop.
        DispatchQueue.main.async { model.startIfPreviouslyRunning() }
    }

    var body: some Scene {
        // A single Window rather than a WindowGroup: several copies of a
        // control panel for one server would be nonsense.
        Window("Tethertone", id: "main") {
            MainWindow(model: model)
        }
        .commands {
            CommandGroup(replacing: .newItem) { }
            CommandGroup(after: .appInfo) {
                Button(model.running ? "Stop Server" : "Start Server") {
                    model.running ? model.stop() : model.startAfterAuthorisation()
                }
                .keyboardShortcut("r")
            }
        }

        MenuBarExtra {
            MenuContent(model: model)
        } label: {
            // Filled while audio is actually going somewhere, outline otherwise:
            // the menu bar should answer "is it working" without being opened.
            Image(systemName: model.running && !model.clients.isEmpty
                  ? "waveform.circle.fill" : "waveform.circle")
        }
        .menuBarExtraStyle(.window)

        // A separate window so the QR survives the popover closing, which is
        // exactly what happens when you reach for your phone.
        Window("Pair your phone", id: "pairing") {
            PairingWindow(model: model)
        }
        .windowResizability(.contentSize)
    }
}
