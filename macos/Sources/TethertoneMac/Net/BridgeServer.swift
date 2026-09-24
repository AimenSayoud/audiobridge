import Foundation
import Network

enum ServerError: LocalizedError {
    case invalidPort(UInt16)

    var errorDescription: String? {
        switch self {
        case .invalidPort(let port): return "Port \(port) is not usable; pick 1-65535."
        }
    }
}

/// TCP server for the Tethertone protocol. Capture pushes blocks in through
/// `broadcast`; each session owns its own bounded queue from there on.
final class BridgeServer {

    let queue = DispatchQueue(label: "dev.tethertone.server")
    private var listener: NWListener?
    private var sessions: [ObjectIdentifier: ClientSession] = [:]

    private(set) var port: UInt16 = 45678
    var format: StreamFormat
    let token: String
    let serverName: String

    /// Always delivered on the main queue — it drives the UI.
    var onClientsChanged: (([ClientSnapshot]) -> Void)?
    var onListenerFailed: ((Error) -> Void)?
    /// Delivered on the main queue.
    var onActivity: ((ActivityEntry.Kind, String) -> Void)?

    /// Bytes actually written to sockets, not bytes captured — with nobody
    /// connected this stays at zero, which is the honest answer.
    private(set) var totalBytesSent: Int64 = 0

    init(token: String, serverName: String, format: StreamFormat) {
        self.token = token
        self.serverName = serverName
        self.format = format
    }

    var clientCount: Int { sessions.values.filter(\.authorised).count }

    func start(port: UInt16) throws {
        stop()
        self.port = port

        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        if let tcp = parameters.defaultProtocolStack.internetProtocol as? NWProtocolTCP.Options {
            // Audio packets are small and frequent; waiting for Nagle to fill a
            // segment would add tens of milliseconds for no bandwidth gain.
            tcp.noDelay = true
        }

        guard let endpointPort = NWEndpoint.Port(rawValue: port) else {
            throw ServerError.invalidPort(port)
        }
        let listener = try NWListener(using: parameters, on: endpointPort)
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        // The bind result arrives asynchronously, so "listening" is only true
        // once the listener says .ready — announcing it at start() time reports
        // success for a port that is about to fail with EADDRINUSE.
        listener.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                NSLog("[net] listening on 0.0.0.0:%d", Int(self.port))
            case .failed(let error):
                DispatchQueue.main.async { self.onListenerFailed?(error) }
            default:
                break
            }
        }
        listener.start(queue: queue)
        self.listener = listener
    }

    func stop() {
        queue.sync {
            for session in sessions.values { session.close() }
            sessions.removeAll()
        }
        listener?.cancel()
        listener = nil
        publish()
    }

    private func accept(_ connection: NWConnection) {
        let session = ClientSession(connection: connection, queue: queue, token: token,
                                    format: format, serverName: serverName)
        // Retained here, not in onReady: onReady only fires once the handshake
        // has succeeded, so waiting for it meant the session was deallocated
        // the moment this function returned and the hello was never answered.
        sessions[ObjectIdentifier(connection)] = session

        session.onActivity = { [weak self] kind, text in
            DispatchQueue.main.async { self?.onActivity?(kind, text) }
        }
        session.onBytesSent = { [weak self] count in
            self?.totalBytesSent += Int64(count)
        }
        session.onReady = { [weak self] _ in self?.publish() }
        session.onUpdate = { [weak self] _ in self?.publish() }
        session.onClosed = { [weak self] _ in
            guard let self else { return }
            self.sessions.removeValue(forKey: ObjectIdentifier(connection))
            self.publish()
        }
        session.start()
    }

    func disconnect(_ id: ObjectIdentifier) {
        queue.async { [weak self] in self?.sessions[id]?.close() }
    }

    /// Hot path: called once per captured block, from the audio thread.
    func broadcast(_ pcm: Data) {
        queue.async { [weak self] in
            guard let self else { return }
            for session in self.sessions.values { session.enqueue(pcm) }
        }
    }

    /// Only authorised sessions are anybody's business: a half-open connection
    /// that has not said hello yet is not a device the user has paired.
    private func publish() {
        let snapshots = sessions.values
            .filter(\.authorised)
            .map(\.snapshot)
            .sorted { $0.connectedAt < $1.connectedAt }
        DispatchQueue.main.async { [weak self] in self?.onClientsChanged?(snapshots) }
    }
}
