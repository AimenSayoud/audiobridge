package dev.tethertone.desktop

import dev.tethertone.shared.BridgeClient
import dev.tethertone.shared.BridgeConfig
import dev.tethertone.shared.BridgeState
import dev.tethertone.shared.Pairing
import dev.tethertone.shared.PairingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Headless sink: the same BridgeClient the Android app runs, playing through
 * javax.sound. If this works and the phone does not, the problem is Android.
 *
 *   ./gradlew :desktopSink:run --args="tethertone://p?..."
 *   ./gradlew :desktopSink:run --args="--seconds 10"
 */
fun main(args: Array<String>) = runBlocking {
    var uri: String? = null
    var seconds = 15L
    var host = "127.0.0.1"
    var port = 45678

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--seconds" -> seconds = args[++i].toLong()
            "--host" -> host = args[++i]
            "--port" -> port = args[++i].toInt()
            else -> uri = args[i]
        }
        i++
    }

    val info: PairingInfo = uri?.let(Pairing::parse) ?: run {
        val tokenFile = File(System.getProperty("user.home"), ".tethertone/token")
        val token = if (tokenFile.exists()) tokenFile.readText().trim() else ""
        PairingInfo(listOf(host), port, token, name = "local")
    }

    println("[sink] hosts=${info.hosts} port=${info.port} token=${if (info.token.isEmpty()) "<none>" else "set"}")

    val scope = CoroutineScope(SupervisorJob())
    val client = BridgeClient(scope, "desktopSink", BridgeConfig(autoReconnect = false))
    client.connect(info)

    var settled = false
    repeat(seconds.toInt()) {
        delay(1000)
        when (val state = client.state.value) {
            is BridgeState.Connected -> {
                settled = true
                val s = client.stats.value
                println(
                    "[sink] ${state.serverName}@${state.host} ${state.rate}Hz/${state.channels}ch  " +
                        "buf=${s.bufferMs}ms under=${s.underruns} over=${s.overruns} " +
                        "lost=${s.lostFrames} speed=${s.speed} ${s.kbps}kbit/s t=${s.secondsConnected}s"
                )
            }
            is BridgeState.Failed -> {
                println("[sink] FAILED: ${state.reason}")
                client.disconnect()
                return@runBlocking
            }
            is BridgeState.Connecting -> println("[sink] connecting to ${state.detail}...")
            is BridgeState.Reconnecting ->
                println("[sink] reconnecting (attempt ${state.attempt}) in ${state.inSeconds}s: ${state.reason}")
            BridgeState.Idle -> println("[sink] idle")
        }
    }

    val final = client.stats.value
    client.disconnect()
    println(if (settled && final.secondsConnected > 0) "[sink] PASS" else "[sink] FAIL")
}
