package com.miniaturesoftwares.xpjobssuperviser.repo

import android.os.Build
import android.provider.Settings
import com.miniaturesoftwares.xpjobssuperviser.network.ApiService
import com.miniaturesoftwares.xpjobssuperviser.network.DeviceDetails
import com.miniaturesoftwares.xpjobssuperviser.network.SecureMMKVStorage
import com.miniaturesoftwares.xpjobssuperviser.network.SendRecorderOtpReq
import com.miniaturesoftwares.xpjobssuperviser.network.VerifyRecorderOtpReq
import com.miniaturesoftwares.xpjobssuperviser.network.VerifyRecorderOtpRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Phone + OTP sign-in, against the same endpoints the recorder app uses.
 *
 * Supervisors authenticate as the same kind of account; what differs is what
 * they do afterwards, not how they get a token. If the backend later issues a
 * distinct supervisor role, only the endpoint path here changes.
 */
class AuthRepo @Inject constructor(
    private val api: ApiService,
    private val storage: SecureMMKVStorage,
    @ApplicationContext private val context: android.content.Context,
) {
    fun sendOtp(phone: String): Flow<Unit> = flow {
        val response = api.sendRecorderOtp(SendRecorderOtpReq(phone))
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.string() ?: "Could not send the code")
        }
        emit(Unit)
    }.flowOn(Dispatchers.IO)

    fun verifyOtp(phone: String, otp: String): Flow<VerifyRecorderOtpRes> = flow {
        val deviceId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()

        val response = api.verifyRecorderOtp(
            VerifyRecorderOtpReq(
                phoneNumber = phone,
                otp = otp,
                deviceId = deviceId,
                deviceDetails = DeviceDetails(
                    deviceId = deviceId,
                    model = Build.MODEL,
                    osVersion = Build.VERSION.RELEASE,
                ),
            )
        )
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.string() ?: "That code was not accepted")
        }
        val body = response.body() ?: throw Exception("Empty response")
        // Persist before emitting: the very next call the UI makes needs the
        // token already attached by TokenInterceptor.
        storage.set(SecureMMKVStorage.SecureKey.USER_TOKEN, body.accessToken)
        storage.set(SecureMMKVStorage.SecureKey.USER_ID, body.userId)
        emit(body)
    }.flowOn(Dispatchers.IO)

    fun isLoggedIn(): Boolean =
        !storage.get<String>(SecureMMKVStorage.SecureKey.USER_TOKEN).isNullOrBlank()

    fun logout() {
        storage.remove(SecureMMKVStorage.SecureKey.USER_TOKEN)
        storage.remove(SecureMMKVStorage.SecureKey.USER_ID)
    }
}
