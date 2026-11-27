package com.example.terralock.nav.modules

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TelemetryFrame(
    val timestampMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Float,
    val heading: Float,
    val meanCn0DbHz: Double,
    val clockBiasJumpNs: Long,
    val spoofScore: Int,
    val activeFlags: List<String>,
    val pressureHpa: Double = 1013.25
)

class TelemetryBlackBox(private val capacity: Int = 900) {
    private val buffer = ArrayDeque<TelemetryFrame>()
    private var lastSpoofedState = false

    @Synchronized
    fun recordFrame(frame: TelemetryFrame, isSpoofedNow: Boolean): Pair<String, String>? {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(frame)

        var dumpResult: Pair<String, String>? = null

        if (isSpoofedNow && !lastSpoofedState) {
            dumpResult = exportBlackBoxDump()
        }

        lastSpoofedState = isSpoofedNow
        return dumpResult
    }

    @Synchronized
    fun exportBlackBoxDump(): Pair<String, String> {
        val jsonStr = Json.encodeToString(buffer.toList())

        val csvStr = buildString {
            append("timestampMs,lat,lon,speedMps,heading,meanCn0DbHz,clockBiasJumpNs,spoofScore,activeFlags,pressureHpa\n")
            for (f in buffer) {
                append("${f.timestampMs},${f.lat},${f.lon},${f.speedMps},${f.heading},${f.meanCn0DbHz},${f.clockBiasJumpNs},${f.spoofScore},\"${f.activeFlags.joinToString(";")}\",${f.pressureHpa}\n")
            }
        }

        return Pair(jsonStr, csvStr)
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        lastSpoofedState = false
    }
}