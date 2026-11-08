package app.pulse.wear

import android.os.Handler
import android.os.Looper
import java.time.Instant
import java.time.ZonedDateTime

internal data class Wearable(
    val id: String,
    val name: String,
    val model: String,
    val firmware: String,
    val batteryPercent: Int,
    val signalDbm: Int,
)

internal data class HeartSample(val minuteOfDay: Int, val bpm: Int)

internal data class HealthRecord(
    val deviceId: String,
    val deviceName: String,
    val day: String,
    val syncedAt: Instant,
    val steps: Int,
    val heartSamples: List<HeartSample>,
) {
    val latestBpm: Int get() = heartSamples.last().bpm
}

internal sealed interface SyncReply {
    data class Data(val record: HealthRecord) : SyncReply
    data object Timeout : SyncReply
}

/** UI-facing boundary. Device readiness includes discovery and subscription setup. */
internal interface WearableClient {
    fun scan(onFound: (Wearable) -> Unit)
    fun connect(device: Wearable, onPreparing: () -> Unit, onReady: (Wearable) -> Unit)
    fun requestHealth(dropReply: Boolean, onReply: (SyncReply) -> Unit)
    fun disconnect()
}

internal class MockWearableClient : WearableClient {
    private val handler = Handler(Looper.getMainLooper())
    private val band = Wearable("demo-s1", "Pulse Band S1", "PB-S1", "1.4.2", 82, -52)
    private var connectedDevice: Wearable? = null
    private var pendingTimeout: Runnable? = null

    override fun scan(onFound: (Wearable) -> Unit) {
        handler.postDelayed({ onFound(band) }, 850L)
    }

    override fun connect(device: Wearable, onPreparing: () -> Unit, onReady: (Wearable) -> Unit) {
        handler.postDelayed(onPreparing, 470L)
        handler.postDelayed({
            connectedDevice = device
            onReady(device)
        }, 1150L)
    }

    override fun requestHealth(dropReply: Boolean, onReply: (SyncReply) -> Unit) {
        val device = checkNotNull(connectedDevice)
        check(pendingTimeout == null)

        // A request completes only when its reply arrives or the wait expires.
        val timeout = Runnable {
            pendingTimeout = null
            onReply(SyncReply.Timeout)
        }
        pendingTimeout = timeout
        handler.postDelayed(timeout, 2400L)

        if (!dropReply) {
            handler.postDelayed({
                handler.removeCallbacks(timeout)
                pendingTimeout = null
                onReply(SyncReply.Data(dailyRecord(device)))
            }, 1080L)
        }
    }

    override fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        pendingTimeout = null
        connectedDevice = null
    }

    private fun dailyRecord(device: Wearable): HealthRecord {
        val now = ZonedDateTime.now()
        val lastMinute = now.hour * 60 + now.minute
        val startMinute = (lastMinute - 7 * 60).coerceAtLeast(0)
        val readings = listOf(66, 70, 68, 78, 73, 76, 71, 74)
        val samples = readings.mapIndexed { index, bpm ->
            val minute = startMinute + (lastMinute - startMinute) * index / readings.lastIndex
            HeartSample(minute, bpm)
        }
        return HealthRecord(
            deviceId = device.id,
            deviceName = device.name,
            day = now.toLocalDate().toString(),
            syncedAt = now.toInstant(),
            steps = 6842,
            heartSamples = samples,
        )
    }
}
