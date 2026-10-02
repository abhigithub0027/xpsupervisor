package com.miniaturesoftwares.xpjobssuperviser.cybercap

import android.util.Base64
import org.json.JSONObject

/**
 * One heartbeat frame from a cyber-cap device.
 *
 * The device emits these ~1/second unconditionally - even when nothing has
 * changed - because a suppressed frame would be indistinguishable from a dead
 * link. [seq] advancing is the liveness signal.
 */
data class CyberCapHeartbeat(
    val seq: Long,
    val uptimeMs: Long,
    val deviceId: String,
    val recording: Boolean,
    val sessionId: String,
    val taskId: String,
    /** "none" | "button" | "phone" - who started the current recording. */
    val owner: String,
    val segmentIndex: Long,
    val sdMounted: Boolean,
    val sessionSec: Int,
    val totalSec: Int,
    /** SoC temperature in C, or null when unavailable. */
    val socTempC: Int?,
    /** Free space on the recording volume in MiB, or null when unavailable. */
    val freeMb: Long?,
    /** Compact upload summary, or null on firmware that predates it. */
    val upload: UploadSummary?,
    /** Wall-clock time this frame was received, for staleness checks. */
    val receivedAtMs: Long,
) {
    val ownedByPhone: Boolean get() = owner == "phone"

    companion object {
        const val TYPE = "heartbeat"

        fun parse(json: JSONObject, receivedAtMs: Long): CyberCapHeartbeat? {
            if (json.optString("type") != TYPE) return null
            return CyberCapHeartbeat(
                seq = json.optLong("seq"),
                uptimeMs = json.optLong("uptime_ms"),
                deviceId = json.optString("device_id"),
                recording = json.optBoolean("recording"),
                sessionId = json.optString("session_id"),
                taskId = json.optString("task_id"),
                owner = json.optString("owner", "none"),
                segmentIndex = json.optLong("segment_index"),
                sdMounted = json.optBoolean("sd_mounted"),
                sessionSec = json.optInt("session_sec"),
                totalSec = json.optInt("total_sec"),
                socTempC = if (json.has("soc_temp_c")) json.optInt("soc_temp_c") else null,
                freeMb = if (json.has("free_mb")) json.optLong("free_mb") else null,
                upload = json.optJSONObject("upload")?.let { UploadSummary.parse(it) },
                receivedAtMs = receivedAtMs,
            )
        }
    }
}

/** Acknowledgement of a command the phone sent. */
data class CyberCapAck(
    val command: String,
    val ok: Boolean,
    val detail: String,
    val sessionId: String,
) {
    companion object {
        const val TYPE = "ack"

        fun parse(json: JSONObject): CyberCapAck? {
            if (json.optString("type") != TYPE) return null
            return CyberCapAck(
                command = json.optString("command"),
                ok = json.optBoolean("ok"),
                detail = json.optString("detail"),
                sessionId = json.optString("session_id"),
            )
        }
    }
}

/**
 * A low-res still from the live recording, sent on the device's own timer
 * while it is actually recording (roughly every couple of seconds - see
 * PreviewConfig on the device side for the real cadence). Decoded from
 * base64 here so nothing downstream needs to know the wire format; a
 * malformed payload (a truncated transfer, say) yields null bytes rather
 * than throwing, so one bad frame just gets skipped instead of taking
 * down the line-reassembly path that heartbeats also depend on.
 */
data class CyberCapPreviewFrame(
    val seq: Long,
    val jpegBytes: ByteArray,
    val receivedAtMs: Long,
) {
    companion object {
        const val TYPE = "preview_frame"

        fun parse(json: JSONObject, receivedAtMs: Long): CyberCapPreviewFrame? {
            if (json.optString("type") != TYPE) return null
            val bytes = runCatching {
                Base64.decode(json.optString("data"), Base64.DEFAULT)
            }.getOrNull()
            if (bytes == null || bytes.isEmpty()) return null
            return CyberCapPreviewFrame(
                seq = json.optLong("seq"),
                jpegBytes = bytes,
                receivedAtMs = receivedAtMs,
            )
        }
    }
}

/**
 * The single cyber-cap this phone is paired with.
 *
 * The address is remembered only as a reconnect hint: replacing the device's USB
 * Bluetooth dongle changes it, so [deviceId] - which comes from the QR sticker
 * and is provisioned per board - is the real identity.
 */
data class PairedDevice(
    val deviceId: String,
    val name: String,
    val lastAddress: String,
    val pairedAtMs: Long,
) {
    fun label(): String = if (name.isNotEmpty()) name else deviceId.take(8)
}

// UnresolvedRecording / UnresolvedReason are deliberately absent here.
//
// In the recorder app they answer "did the recording *I* started finish?" - a
// question about one operator's own in-flight session. A supervisor is not the
// owner of any session; they watch live fleet state, and a cap that goes quiet
// is shown as "no signal" rather than raised as an unresolved recording to
// adjudicate.

/**
 * The `upload` block carried on every heartbeat.
 *
 * Deliberately small - it rides a BLE MTU several times a second. The full
 * per-file inventory is fetched on demand with `list_recordings`.
 */
data class UploadSummary(
    /**
     * What the device knows about its own uploader:
     *
     *  - [State.UNKNOWN] - no status published at all. Either the uploader has
     *    never run, or the firmware predates this feature. NOT the same as
     *    "nothing to upload", and must not be shown as healthy.
     *  - [State.STALE] - a snapshot exists but has not been refreshed. The
     *    uploader died holding these numbers; they are history, not status.
     *  - [State.LIVE] - current.
     */
    val state: State,
    val pending: Int,
    val pendingMb: Long,
    val waitingEncrypt: Int,
    val uploadedTotal: Long,
    val failedTotal: Long,
    /** An upload is in flight right now. */
    val busy: Boolean,
    /** Segments uploaded but not yet acknowledged by the backend. */
    val queuedNotify: Int,
    /** Seconds since the uploader last published, or null when unknown. */
    val ageSec: Long?,
    val lastError: String?,
    /** Byte progress of the transfer in flight, or null when nothing is going out. */
    val progress: UploadProgress?,
) {
    enum class State { UNKNOWN, STALE, LIVE }

    val isHealthy: Boolean get() = state == State.LIVE
    val hasBacklog: Boolean get() = pending > 0 || waitingEncrypt > 0

    companion object {
        fun parse(json: JSONObject): UploadSummary {
            val state = when (json.optString("state")) {
                "live" -> State.LIVE
                "stale" -> State.STALE
                else -> State.UNKNOWN
            }
            val age = if (json.has("age_sec")) json.optLong("age_sec") else -1L
            return UploadSummary(
                state = state,
                pending = json.optInt("pending"),
                pendingMb = json.optLong("pending_mb"),
                waitingEncrypt = json.optInt("waiting_encrypt"),
                uploadedTotal = json.optLong("uploaded"),
                failedTotal = json.optLong("failed"),
                busy = json.optBoolean("busy"),
                queuedNotify = json.optInt("queued_notify"),
                ageSec = if (age >= 0) age else null,
                lastError = json.optString("last_error").ifBlank { null },
                progress = json.optJSONObject("progress")?.let { UploadProgress.parse(it) },
            )
        }
    }
}

/** Where a recording sits in the capture -> encrypt -> upload pipeline. */
enum class SegmentState(val wire: String) {
    RECORDING("recording"),
    AWAITING_ENCRYPT("awaiting_encrypt"),
    ENCRYPTING("encrypting"),
    PENDING_UPLOAD("pending_upload"),
    UPLOADING("uploading"),
    UNKNOWN("unknown");

    companion object {
        fun from(wire: String): SegmentState =
            entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/**
 * One recording on the device.
 *
 * [durationExact] matters for display: the device reports an exact duration
 * only when the segment's sidecar still exists. Once the uploader has notified
 * the backend it deletes that sidecar, and the duration becomes an estimate
 * derived from file timestamps. Showing an estimate as a measurement would be
 * misleading, so the UI marks it with a leading "~".
 */
data class DeviceRecording(
    val name: String,
    val state: SegmentState,
    val sizeBytes: Long,
    val mtimeUnix: Long,
    val startTimeUnix: Long,
    val durationSeconds: Double,
    val durationExact: Boolean,
    val sessionId: String?,
    val taskId: String?,
    val operatorId: String?,
    val recordingUuid: String?,
    val segmentIndex: Long?,
) {
    companion object {
        fun parse(json: JSONObject): DeviceRecording = DeviceRecording(
            name = json.optString("name"),
            state = SegmentState.from(json.optString("state")),
            sizeBytes = json.optLong("size_bytes"),
            mtimeUnix = json.optLong("mtime_unix"),
            startTimeUnix = json.optLong("start_time_unix"),
            durationSeconds = json.optDouble("duration_seconds", 0.0),
            durationExact = json.optString("duration_source") == "sidecar",
            sessionId = json.optString("session_id").ifBlank { null },
            taskId = json.optString("task_id").ifBlank { null },
            operatorId = json.optString("operator_id").ifBlank { null },
            recordingUuid = json.optString("recording_uuid").ifBlank { null },
            segmentIndex = if (json.has("segment_index")) json.optLong("segment_index") else null,
        )
    }
}

/** One page of the reply to `list_recordings`. */
data class RecordingsPage(
    val deviceId: String,
    val offset: Int,
    val limit: Int,
    val total: Int,
    val dirOk: Boolean,
    val dirError: String?,
    val freeBytes: Long,
    val bytesOnDisk: Long,
    val items: List<DeviceRecording>,
) {
    val hasMore: Boolean get() = offset + items.size < total

    companion object {
        const val TYPE = "recordings"

        fun parse(json: JSONObject): RecordingsPage? {
            if (json.optString("type") != TYPE) return null
            val arr = json.optJSONArray("items")
            val items = buildList {
                for (i in 0 until (arr?.length() ?: 0)) {
                    arr?.optJSONObject(i)?.let { add(DeviceRecording.parse(it)) }
                }
            }
            return RecordingsPage(
                deviceId = json.optString("device_id"),
                offset = json.optInt("offset"),
                limit = json.optInt("limit"),
                total = json.optInt("total"),
                dirOk = json.optBoolean("dir_ok"),
                dirError = json.optString("dir_error").ifBlank { null },
                freeBytes = json.optLong("free_bytes"),
                bytesOnDisk = json.optLong("bytes_on_disk"),
                items = items,
            )
        }
    }
}

/**
 * Byte progress of the segment currently uploading.
 *
 * Carried on the heartbeat as raw byte counts rather than a percentage, so the
 * UI can show "128 MB of 693 MB" as well as a bar - a bare percent cannot be
 * turned back into sizes.
 *
 * Its absence, not [sentBytes] reaching [totalBytes], is what means "done":
 * the device sizes the transfer from the encrypted file, which is fractionally
 * larger than the decrypted stream actually being sent, so the count creeps
 * slightly ahead and the last percent is never reported.
 */
data class UploadProgress(
    val name: String,
    val sentBytes: Long,
    val totalBytes: Long,
    val percent: Int,
    val bytesPerSecond: Long,
    /** Seconds remaining at the current rate, or null when not computable. */
    val etaSeconds: Long?,
) {
    companion object {
        fun parse(json: JSONObject): UploadProgress {
            val eta = json.optLong("eta", -1L)
            return UploadProgress(
                name = json.optString("name"),
                sentBytes = json.optLong("sent"),
                totalBytes = json.optLong("total"),
                percent = json.optInt("pct").coerceIn(0, 100),
                bytesPerSecond = json.optLong("bps"),
                etaSeconds = if (eta >= 0) eta else null,
            )
        }
    }
}
