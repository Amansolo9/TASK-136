package com.eaglepoint.task136.shared.security

import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.datetime.Clock

class DeviceBindingService(
    private val deviceBindingDao: DeviceBindingDao,
    private val clock: Clock,
    private val maxDevices: Int = 2,
) {
    suspend fun checkAndBindDevice(userId: String, deviceFingerprint: String): DeviceBindingResult {
        val existing = deviceBindingDao.findByUserAndDevice(userId, deviceFingerprint)
        if (existing != null) {
            return DeviceBindingResult.Allowed
        }

        val count = deviceBindingDao.countByUserId(userId)
        if (count >= maxDevices) {
            return DeviceBindingResult.LimitExceeded(bound = count, max = maxDevices)
        }

        deviceBindingDao.upsert(
            DeviceBindingEntity(
                id = "$userId-$deviceFingerprint",
                userId = userId,
                deviceFingerprint = deviceFingerprint,
                boundAt = clock.now().toEpochMilliseconds(),
            ),
        )
        return DeviceBindingResult.Allowed
    }

    suspend fun isDeviceTrusted(userId: String, deviceFingerprint: String): Boolean {
        return deviceBindingDao.findByUserAndDevice(userId, deviceFingerprint) != null
    }

    suspend fun adminResetBindings(adminRole: Role, userId: String): Boolean {
        if (adminRole != Role.Admin) return false
        deviceBindingDao.deleteAllForUser(userId)
        return true
    }

    suspend fun getDeviceCount(userId: String): Int {
        return deviceBindingDao.countByUserId(userId)
    }

}

sealed class DeviceBindingResult {
    data object Allowed : DeviceBindingResult()
    data class LimitExceeded(val bound: Int, val max: Int) : DeviceBindingResult()
}
