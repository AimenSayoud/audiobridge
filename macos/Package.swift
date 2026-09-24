// swift-tools-version:5.9
import PackageDescription

// Swift 5 language mode on purpose: the audio callback and the Network.framework
// queues are already serialised by hand, and Swift 6 strict concurrency would
// demand a large annotation pass for no behaviour change.
let package = Package(
    name: "AudioBridgeMac",
    platforms: [.macOS(.v14)],
    targets: [
        .executableTarget(
            name: "AudioBridgeMac",
            path: "Sources/AudioBridgeMac"
        )
    ]
)
