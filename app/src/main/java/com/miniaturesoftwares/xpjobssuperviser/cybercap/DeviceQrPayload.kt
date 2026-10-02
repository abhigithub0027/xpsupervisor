package com.miniaturesoftwares.xpjobssuperviser.cybercap

import android.net.Uri

/**
 * Contents of a cyber-cap pairing sticker: `cybercap://device/<device_id>?name=<name>`.
 *
 * Produced by the cyber-cap repo's scripts/make_device_qr.py. The sticker carries
 * the device_id rather than the Bluetooth address, because replacing a device's
 * USB Bluetooth dongle changes its BD address but not its device_id - so printed
 * stickers survive a dongle swap.
 *
 * Self-contained by design: nothing here touches existing NeuroCapture flows.
 */
data class DeviceQrPayload(
    val deviceId: String,
    val name: String,
) {
    /** Short form for the UI: "CC843 (f4511f82…)". */
    fun label(): String {
        val short = if (deviceId.length > 8) deviceId.take(8) + "…" else deviceId
        return if (name.isNotEmpty()) "$name ($short)" else short
    }

    companion object {
        const val SCHEME = "cybercap"
        private const val HOST = "device"

        /**
         * Returns null for anything that is not a cyber-cap sticker, so an
         * unrelated QR cannot steer the app at a device.
         */
        fun parse(raw: String?): DeviceQrPayload? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
            if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
            if (!HOST.equals(uri.host, ignoreCase = true)) return null
            val id = uri.path?.trim('/').orEmpty()
            if (id.isEmpty()) return null
            val name = runCatching { uri.getQueryParameter("name") }.getOrNull().orEmpty()
            return DeviceQrPayload(id, name)
        }
    }
}
