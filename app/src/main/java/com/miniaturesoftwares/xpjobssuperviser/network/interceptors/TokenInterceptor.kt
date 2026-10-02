package com.miniaturesoftwares.xpjobssuperviser.network.interceptors

import com.miniaturesoftwares.xpjobssuperviser.network.SecureMMKVStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the supervisor's bearer token to every request.
 *
 * Deliberately thinner than the recorder app's version, which also force-logs
 * out and bounces to a login screen on 401. A supervisor may be mid-session
 * watching a fleet record; yanking them to a login screen because one polling
 * call expired would be worse than letting the call fail. Expiry is handled
 * where the call is made instead.
 */
class TokenInterceptor @Inject constructor(
    private val storage: SecureMMKVStorage,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = storage.get<String>(SecureMMKVStorage.SecureKey.USER_TOKEN)
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
            addHeader("Accept", "application/json")
        }.build()
        return chain.proceed(request)
    }
}
