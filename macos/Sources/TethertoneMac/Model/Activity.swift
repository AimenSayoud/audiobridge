import Foundation

/// A log of what the server has actually seen.
///
/// Without this there is no way to tell the two failure modes apart: a phone
/// whose packets never arrive and a phone that arrives and is turned away for a
/// stale token look identical from outside — the menu says "waiting" in both
/// cases and the phone says "retrying" in both cases.
struct ActivityEntry: Identifiable, Equatable {
    enum Kind: Equatable {
        case arrived        // TCP connection accepted — proves packets reach us
        case connected
        case rejected
        case silent         // connected but never spoke
        case disconnected
        case info

        var symbol: String {
            switch self {
            case .arrived: return "arrow.down.circle"
            case .connected: return "checkmark.circle.fill"
            case .rejected: return "xmark.octagon.fill"
            case .silent: return "questionmark.circle"
            case .disconnected: return "arrow.up.forward.circle"
            case .info: return "info.circle"
            }
        }
    }

    let id = UUID()
    let date = Date()
    let kind: Kind
    let text: String

    var timestamp: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter.string(from: date)
    }
}
