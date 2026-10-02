package com.miniaturesoftwares.xpjobssuperviser.cybercap

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every cyber-cap this supervisor phone is paired with.
 *
 * The recorder app deliberately stores exactly one device - the product rule
 * there is one cap per operator, and storing a set would allow precisely the
 * ambiguity that rule exists to prevent. A supervisor is the opposite case:
 * they hold the whole fleet, so this keeps an ordered set and the "one device"
 * guard is absent by design.
 */
@Singleton
class FleetDeviceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("supervisor_fleet", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): List<PairedDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<PairedDevice>>(
                raw, object : TypeToken<List<PairedDevice>>() {}.type
            )
        }.getOrNull().orEmpty()
    }

    /** Add or update by device id. Order is preserved; new devices append. */
    fun put(device: PairedDevice) {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.deviceId.equals(device.deviceId, ignoreCase = true) }
        if (index >= 0) current[index] = device else current.add(device)
        save(current)
    }

    fun remove(deviceId: String) {
        save(all().filterNot { it.deviceId.equals(deviceId, ignoreCase = true) })
    }

    fun get(deviceId: String): PairedDevice? =
        all().firstOrNull { it.deviceId.equals(deviceId, ignoreCase = true) }

    fun isPaired(deviceId: String): Boolean = get(deviceId) != null

    /**
     * Refresh a device's reconnect hint without disturbing identity or the
     * order the supervisor arranged them in.
     */
    fun updateAddress(deviceId: String, address: String) {
        if (address.isBlank()) return
        get(deviceId)?.let { put(it.copy(lastAddress = address)) }
    }

    fun clear() = prefs.edit().remove(KEY_DEVICES).apply()

    private fun save(devices: List<PairedDevice>) {
        prefs.edit().putString(KEY_DEVICES, gson.toJson(devices)).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
    }
}
