import SwiftUI

/// The QR in its own window, large enough to scan comfortably.
///
/// A menu-bar popover closes the moment focus moves, which is precisely what
/// happens when you pick up your phone — and the 180pt version in the menu is
/// borderline for a phone camera at arm's length anyway.
struct PairingWindow: View {
    @ObservedObject var model: ServerModel

    var body: some View {
        VStack(spacing: 16) {
            Text("Scan with Tethertone on your phone")
                .font(.headline)

            if let image = model.qrImage {
                Image(nsImage: image)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 320, height: 320)
                    .padding(16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.secondary.opacity(0.15))
                    .frame(width: 320, height: 320)
                    .overlay(Text("Start the server to generate a code")
                        .foregroundStyle(.secondary))
            }

            if let pairing = model.pairing {
                VStack(spacing: 4) {
                    Text(pairing.hosts.joined(separator: "  ·  ") + "  :  \(pairing.port)")
                        .font(.system(.callout, design: .monospaced))
                    if model.usbAttached {
                        Label("USB path live — the phone tries it first",
                              systemImage: "cable.connector")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Button("Copy pairing link") { model.copyPairingURI() }
        }
        .padding(24)
        .frame(width: 400)
    }
}
