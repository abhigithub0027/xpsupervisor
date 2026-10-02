package com.miniaturesoftwares.xpjobssuperviser.network.supervisor

import kotlinx.coroutines.delay
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in for the supervisor endpoints until the backend has them.
 *
 * Returns the exact shapes documented in docs/SUPERVISOR_API.md, so the screens
 * built against it need no changes when the real API arrives - the Hilt binding
 * in SupervisorModule swaps and nothing else moves.
 *
 * The delay is deliberate: data that appears instantly hides loading and empty
 * states, and those are exactly the states that break first against a real
 * network.
 */
@Singleton
class SupervisorApiStub @Inject constructor() : SupervisorApi {

    override suspend fun getStaff(status: String?, query: String?): Response<StaffRes> {
        delay(NETWORK_DELAY_MS)
        var people = STAFF
        if (!status.isNullOrBlank()) people = people.filter { it.status == status }
        if (!query.isNullOrBlank()) {
            people = people.filter { it.fullName.contains(query, ignoreCase = true) }
        }
        return Response.success(StaffRes(staff = people, total = people.size))
    }

    override suspend fun getStaffMember(id: String): Response<StaffMember> {
        delay(NETWORK_DELAY_MS)
        val member = STAFF.firstOrNull { it.id == id }
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, ""))
        return Response.success(member)
    }

    override suspend fun getDevices(): Response<DevicesRes> {
        delay(NETWORK_DELAY_MS)
        return Response.success(DevicesRes(devices = DEVICES))
    }

    override suspend fun assignDevice(
        deviceId: String,
        body: AssignDeviceReq,
    ): Response<DeviceRecord> {
        delay(NETWORK_DELAY_MS)
        val device = DEVICES.firstOrNull { it.deviceId == deviceId }
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, ""))
        val person = STAFF.firstOrNull { it.id == body.assignedToId }
        return Response.success(
            device.copy(assignedToId = person?.id, assignedToName = person?.fullName)
        )
    }

    private companion object {
        const val NETWORK_DELAY_MS = 600L

        // Two of these device ids are the real units on the bench (D7A3 is the
        // LubanCat, CC843 the LuckFox) so the People and Fleet screens line up
        // while testing.
        val STAFF = listOf(
            StaffMember(
                id = "stf_001", fullName = "Abhay Jadon", phoneNumber = "9598888369",
                status = "active", assignedDeviceId = "CC843", assignedDeviceName = "CC843",
                currentTaskTitle = "Cleaning kitchen slab / counter",
                sessionsToday = 4, sessionsTotal = 189, lastSeenUnix = 1787000000,
            ),
            StaffMember(
                id = "stf_002", fullName = "Pavan Nanjunda", phoneNumber = "9876543210",
                status = "active", assignedDeviceId = "D7A3", assignedDeviceName = "1",
                currentTaskTitle = "Washing utensils & vessels",
                sessionsToday = 2, sessionsTotal = 76, lastSeenUnix = 1787000000,
            ),
            StaffMember(
                id = "stf_003", fullName = "Aarti Gupta", phoneNumber = "9812345678",
                status = "idle", assignedDeviceId = null, assignedDeviceName = null,
                currentTaskTitle = null,
                sessionsToday = 0, sessionsTotal = 41, lastSeenUnix = 1786900000,
            ),
            StaffMember(
                id = "stf_004", fullName = "Ramesh Kumar", phoneNumber = "9700011122",
                status = "offline", assignedDeviceId = null, assignedDeviceName = null,
                currentTaskTitle = null,
                sessionsToday = 0, sessionsTotal = 12, lastSeenUnix = 1786500000,
            ),
        )

        val DEVICES = listOf(
            DeviceRecord(
                deviceId = "CC843", name = "CC843", deviceKitId = "kit-ego-luckfox-v3",
                firmwareVersion = "v2.1", assignedToId = "stf_001",
                assignedToName = "Abhay Jadon", lastSeenUnix = 1787000000,
                lastSessionId = "sess_1786789323121", totalSessions = 189,
            ),
            DeviceRecord(
                deviceId = "D7A3", name = "1", deviceKitId = "kit-ego-lubancat-v3",
                firmwareVersion = "v2.1", assignedToId = "stf_002",
                assignedToName = "Pavan Nanjunda", lastSeenUnix = 1787000000,
                lastSessionId = null, totalSessions = 76,
            ),
            DeviceRecord(
                deviceId = "CC901", name = "Cap 09", deviceKitId = "kit-ego-luckfox-v3",
                firmwareVersion = "v2.0", assignedToId = null, assignedToName = null,
                lastSeenUnix = 1786820000, lastSessionId = null, totalSessions = 33,
            ),
        )
    }
}
