package app.pulse.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException

internal class HealthHistory(context: Context) {
    private val preferences = context.getSharedPreferences("pulse_health", Context.MODE_PRIVATE)

    fun load(): List<HealthRecord> {
        val stored = preferences.getString(RECORDS_KEY, "[]") ?: "[]"
        val array = try {
            JSONArray(stored)
        } catch (_: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::decode)
        }.sortedByDescending(HealthRecord::syncedAt)
    }

    /** Upsert one daily snapshot per device, then report success only after a disk write. */
    @Throws(IOException::class)
    fun save(record: HealthRecord, previous: List<HealthRecord>): List<HealthRecord> {
        val updated = (previous.filterNot { it.deviceId == record.deviceId && it.day == record.day } + record)
            .sortedByDescending(HealthRecord::syncedAt)
        val payload = JSONArray()
        updated.forEach { payload.put(encode(it)) }
        if (!preferences.edit().putString(RECORDS_KEY, payload.toString()).commit()) {
            throw IOException("Could not write the local health history")
        }
        return updated
    }

    private fun encode(record: HealthRecord): JSONObject {
        val samples = JSONArray()
        record.heartSamples.forEach { sample ->
            samples.put(JSONObject().put("minute", sample.minuteOfDay).put("bpm", sample.bpm))
        }
        return JSONObject()
            .put("deviceId", record.deviceId)
            .put("deviceName", record.deviceName)
            .put("day", record.day)
            .put("syncedAt", record.syncedAt.toString())
            .put("steps", record.steps)
            .put("heart", samples)
    }

    private fun decode(entry: JSONObject): HealthRecord? {
        return try {
            val samples = entry.getJSONArray("heart")
            val heart = (0 until samples.length()).map { index ->
                val sample = samples.getJSONObject(index)
                HeartSample(sample.getInt("minute"), sample.getInt("bpm"))
            }
            if (heart.isEmpty()) return null
            HealthRecord(
                deviceId = entry.getString("deviceId"),
                deviceName = entry.getString("deviceName"),
                day = entry.getString("day"),
                syncedAt = Instant.parse(entry.getString("syncedAt")),
                steps = entry.getInt("steps"),
                heartSamples = heart,
            )
        } catch (_: JSONException) {
            null
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private companion object {
        const val RECORDS_KEY = "daily_health_v1"
    }
}
