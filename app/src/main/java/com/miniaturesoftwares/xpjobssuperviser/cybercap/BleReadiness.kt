package com.miniaturesoftwares.xpjobssuperviser.cybercap

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Everything that has to be true before a cyber-cap can be scanned and paired.
 *
 * Checked *before* opening the camera rather than after, because the old flow
 * discovered a missing prerequisite only once a sticker had already been
 * scanned - the operator got a toast and had to start over.
 *
 * The requirements differ sharply by OS version, and getting that wrong is the
 * usual cause of "scanning finds nothing" bug reports:
 *
 *  - **Android 12+ (API 31+)**: BLUETOOTH_SCAN + BLUETOOTH_CONNECT. The scan
 *    permission is declared `neverForLocation`, so location is not involved.
 *  - **Android 11 and below**: BLE scanning is a location capability. It needs
 *    ACCESS_FINE_LOCATION *and* location services actually switched on -
 *    holding the permission alone yields zero results, silently.
 */
object BleReadiness {

    enum class Requirement {
        /** The QR scanner needs the camera. */
        CAMERA_PERMISSION,

        /** Runtime Bluetooth (or, pre-12, location) permission. */
        BLUETOOTH_PERMISSION,

        /** The radio itself is switched off. */
        BLUETOOTH_DISABLED,

        /** Location services off - blocks BLE scanning on API <= 30 only. */
        LOCATION_SERVICES_DISABLED,
    }

    /**
     * The permissions a BLE scan needs on this OS version.
     *
     * Requesting the Android 12 names on an older device silently never
     * grants, and requesting FINE_LOCATION on 12+ asks for something the app
     * does not need - so this must be branched, not unioned.
     */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Location services gate BLE scanning only up to Android 11. */
    fun locationServicesMatter(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    fun hasCameraPermission(context: Context): Boolean = granted(context, Manifest.permission.CAMERA)

    fun hasBluetoothPermissions(context: Context): Boolean =
        requiredPermissions().all { granted(context, it) }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        return adapter?.isEnabled == true
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    /**
     * What still stands in the way, in the order it should be resolved.
     *
     * Permissions come before the toggles they govern: on Android 12+ the
     * system enable-Bluetooth prompt itself requires BLUETOOTH_CONNECT, so
     * asking to turn the radio on first just fails.
     */
    fun missing(context: Context): List<Requirement> = buildList {
        if (!hasCameraPermission(context)) add(Requirement.CAMERA_PERMISSION)
        if (!hasBluetoothPermissions(context)) add(Requirement.BLUETOOTH_PERMISSION)
        if (!isBluetoothEnabled(context)) add(Requirement.BLUETOOTH_DISABLED)
        if (locationServicesMatter() && !isLocationEnabled(context)) {
            add(Requirement.LOCATION_SERVICES_DISABLED)
        }
    }

    fun isReady(context: Context): Boolean = missing(context).isEmpty()

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
