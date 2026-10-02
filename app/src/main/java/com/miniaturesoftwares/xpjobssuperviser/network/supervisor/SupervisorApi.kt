package com.miniaturesoftwares.xpjobssuperviser.network.supervisor

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints this app needs that the backend does not have yet.
 *
 * The shapes here are the contract - they are what [SupervisorApiStub] returns
 * today and what docs/SUPERVISOR_API.md asks the backend team to build. When
 * the real endpoints land, only the Hilt binding changes; nothing above this
 * layer knows the difference.
 */
interface SupervisorApi {

    /** Recording staff this supervisor is responsible for. */
    @GET("api/v1/supervisor/staff")
    suspend fun getStaff(
        @Query("status") status: String? = null,
        @Query("q") query: String? = null,
    ): Response<StaffRes>

    @GET("api/v1/supervisor/staff/{id}")
    suspend fun getStaffMember(@Path("id") id: String): Response<StaffMember>

    /** Devices registered to this supervisor's fleet, as the backend knows them. */
    @GET("api/v1/supervisor/devices")
    suspend fun getDevices(): Response<DevicesRes>

    /** Hand a device to a person, or pass null to unassign. */
    @PATCH("api/v1/supervisor/devices/{deviceId}/assignment")
    suspend fun assignDevice(
        @Path("deviceId") deviceId: String,
        @retrofit2.http.Body body: AssignDeviceReq,
    ): Response<DeviceRecord>
}

// ---------------- staff ----------------

data class StaffRes(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("staff") val staff: List<StaffMember> = emptyList(),
    @SerializedName("total") val total: Int = 0,
)

data class StaffMember(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    /** "active" | "idle" | "offline" */
    @SerializedName("status") val status: String,
    @SerializedName("assigned_device_id") val assignedDeviceId: String?,
    @SerializedName("assigned_device_name") val assignedDeviceName: String?,
    @SerializedName("current_task_title") val currentTaskTitle: String?,
    @SerializedName("sessions_today") val sessionsToday: Int = 0,
    @SerializedName("sessions_total") val sessionsTotal: Int = 0,
    @SerializedName("last_seen_unix") val lastSeenUnix: Long? = null,
)

// ---------------- devices ----------------

data class DevicesRes(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("devices") val devices: List<DeviceRecord> = emptyList(),
)

/**
 * The backend's record of a cap.
 *
 * Deliberately excludes anything live - recording state, storage, temperature
 * come from the cap over BLE and would be stale here. This carries only what
 * the phone cannot know by itself: who the device belongs to, who is holding
 * it, and when the server last heard from it.
 */
data class DeviceRecord(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("name") val name: String?,
    @SerializedName("device_kit_id") val deviceKitId: String?,
    @SerializedName("firmware_version") val firmwareVersion: String?,
    @SerializedName("assigned_to_id") val assignedToId: String?,
    @SerializedName("assigned_to_name") val assignedToName: String?,
    @SerializedName("last_seen_unix") val lastSeenUnix: Long? = null,
    @SerializedName("last_session_id") val lastSessionId: String? = null,
    @SerializedName("total_sessions") val totalSessions: Int = 0,
)

data class AssignDeviceReq(
    @SerializedName("assigned_to_id") val assignedToId: String?,
)
