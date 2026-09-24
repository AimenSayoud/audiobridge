package dev.tethertone.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.tethertone.shared.BridgeState
import dev.tethertone.shared.Pairing
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and the notification honest while audio plays. It
 * does not own the connection — [BridgeController] does — so stopping the
 * service is the only thing that stops playback, and nothing else can.
 */
class TethertoneService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Until startForeground() has run, posting notifications is pointless and
     * stopping is fatal: the platform kills the process if startForeground()
     * does not follow startForegroundService() within a few seconds.
     */
    private var inForeground = false
    private var everConnected = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        lifecycleScope.launch {
            combine(BridgeController.state, BridgeController.stats) { state, stats -> state to stats }
                .collect { (state, stats) ->
                    if (!inForeground) return@collect
                    if (state is BridgeState.Connected) everConnected = true
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state, stats.bufferMs))
                    when {
                        // Leave BridgeController alone on failure: the reason is
                        // the only thing the UI has to show, and tearing it down
                        // would replace it with a blank "Idle".
                        // With auto-reconnect on, Failed never arrives; the
                        // client keeps retrying and the notification says so.
                        state is BridgeState.Failed -> standDown()
                        state is BridgeState.Idle && everConnected -> stopEverything()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Log.i(TAG, "onStartCommand action=${intent?.action} startId=$startId")
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        // First, before parsing, before anything that could throw or block.
        startInForeground()

        val info = intent?.getStringExtra(EXTRA_PAIRING)?.let(Pairing::parse)
        if (info == null) {
            stopEverything()
            return START_NOT_STICKY
        }
        everConnected = false
        acquireWakeLock()
        // Settings are read here rather than shipped through the intent, so a
        // change made while connected and a fresh start cannot disagree.
        BridgeController.connect(info, Prefs(applicationContext).toConfig())

        // Deliberately not sticky: a restart with no intent carries no pairing,
        // and a foreground service that cannot start foregrounding is a crash.
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification(BridgeController.state.value, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
    }

    /**
     * AudioTrack alone does not reliably hold the CPU awake on every OEM build,
     * and a doze-induced underrun sounds like a dropout, not like power saving.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tethertone::playback").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun stopEverything() {
        Log.i(TAG, "stopping: disconnecting and leaving the foreground")
        BridgeController.disconnect()
        standDown(removeNotification = true)
    }

    /** Give up the foreground slot and the wake lock, but keep the reported state. */
    private fun standDown(removeNotification: Boolean = false) {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        inForeground = false
        stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: BridgeState, bufferMs: Int): Notification {
        val (title, text) = when (state) {
            is BridgeState.Connected ->
                "Playing from ${state.serverName}" to
                    "${state.rate / 1000} kHz · ${state.channels}ch · ${bufferMs} ms buffer · ${state.host}"
            is BridgeState.Connecting -> "Connecting" to state.detail
            is BridgeState.Reconnecting ->
                "Reconnecting" to "attempt ${state.attempt} in ${state.inSeconds}s · ${state.reason}"


            is BridgeState.Failed -> "Disconnected" to state.reason
            BridgeState.Idle -> "Idle" to "Not connected"
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TethertoneService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(state is BridgeState.Connected)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START = "dev.tethertone.app.START"
        const val ACTION_STOP = "dev.tethertone.app.STOP"
        const val EXTRA_PAIRING = "pairing"

        private const val TAG = "TethertoneSvc"
        private const val CHANNEL_ID = "tethertone.playback"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 8L * 60 * 60 * 1000

        fun start(context: Context, pairingUri: String) {
            val intent = Intent(context, TethertoneService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PAIRING, pairingUri)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TethertoneService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
