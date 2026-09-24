package dev.tethertone.app

import android.os.Build
import dev.tethertone.shared.BridgeClient
import dev.tethertone.shared.BridgeConfig
import dev.tethertone.shared.BridgeState
import dev.tethertone.shared.BridgeStats
import dev.tethertone.shared.PairingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * One connection per process, owned outside the Activity and outside the
 * Service. The Service exists to keep the process alive and put up a
 * notification; the UI only observes. Neither owns the socket, so a rotation or
 * a notification tap cannot interrupt playback.
 */
object BridgeController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(BridgeStats())
    val stats: StateFlow<BridgeStats> = _stats.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var client: BridgeClient? = null
    private var mirror: Job? = null

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun connect(info: PairingInfo, config: BridgeConfig) {
        disconnect()
        val fresh = BridgeClient(scope, deviceName, config)
        client = fresh
        mirror = scope.launch {
            launch { fresh.state.collect { _state.value = it } }
            launch { fresh.stats.collect { _stats.value = it } }
            launch { fresh.level.collect { _level.value = it } }
        }
        fresh.connect(info)
    }

    /** Volume applies at once; buffer and latency settings take effect on the next connect. */
    fun updateConfig(config: BridgeConfig) {
        client?.updateConfig(config)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        mirror?.cancel()
        mirror = null
        _state.value = BridgeState.Idle
        _stats.value = BridgeStats()
        _level.value = 0f
    }
}
