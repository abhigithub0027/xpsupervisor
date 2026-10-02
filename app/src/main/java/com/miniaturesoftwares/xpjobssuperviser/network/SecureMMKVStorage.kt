package com.miniaturesoftwares.xpjobssuperviser.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureMMKVStorage @Inject constructor() {

    val gson = Gson()

    enum class SecureKey(val key: String) {

        // Other data
        USER_PROFILE("user_profile"),
        USER_TOKEN("user_token"),
        USER_ID("user_id"),
        AUTH_SESSION("auth_session"),
        IS_NOT_FIRST_LAUNCH("is_first_launch"),
        IS_REGISTRATION_FORM_INCOMPLETE("is_registration_form_incomplete"),
        CONSENT_COMPLETED("consent_completed"),
        PREF_FACE_LOGIN("pref_face_login"),
        PREF_AUTO_SYNC("pref_auto_sync"),
        SELECTED_LANGUAGE("selected_language"),
        USER_FULL_NAME("user_full_name"),
        DEVICE_ID("device_id"),
        APP_THEME("app_theme"),
        STORAGE_DESTINATION("storage_destination"),
        EXTERNAL_STORAGE_PATH("external_storage_path"),
        DASHBOARD_CACHE("dashboard_cache"),
        NOTIFICATIONS_CACHE("notifications_cache"),
        TASKS_CACHE("tasks_cache"),
        TASK_DETAILS_CACHE_PREFIX("task_details_"),
        EARNINGS_CACHE("earnings_cache"),
    }

    // Global encrypted storage
    val prefs: MMKV = MMKV.mmkvWithID(
        "secure_ring_storage",
        MMKV.MULTI_PROCESS_MODE,
        "Miniature@2024#Key"
    )

    /** -------- Generic SAVE (Any Type) -------- */
    fun <T> set(key: SecureKey, value: T) {
        when (value) {
            is String -> prefs.putString(key.key, value)
            is Int -> prefs.putInt(key.key, value)
            is Boolean -> prefs.putBoolean(key.key, value)
            is Float -> prefs.putFloat(key.key, value)
            is Long -> prefs.putLong(key.key, value)
            is Enum<*> -> prefs.putString(key.key, value.name)
            else -> prefs.putString(key.key, gson.toJson(value))
        }
    }

    /** -------- Generic GET (Auto-type Convert) -------- */
    inline fun <reified T> get(key: SecureKey, default: T? = null): T? {
        return when (T::class) {
            String::class -> prefs.getString(key.key, default as? String) as T?
            Int::class -> prefs.getInt(key.key, default as? Int ?: 0) as T?
            Boolean::class -> prefs.getBoolean(key.key, default as? Boolean ?: false) as T?
            Float::class -> prefs.getFloat(key.key, default as? Float ?: 0f) as T?
            Long::class -> prefs.getLong(key.key, default as? Long ?: 0L) as T?
            else -> gson.fromJson(prefs.getString(key.key, null), T::class.java)
        }
    }

    /** Helper functions */
    fun remove(key: SecureKey) = prefs.removeValueForKey(key.key)
    fun clear() = prefs.clearAll()

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)

    fun saveAuthSession(session: AuthSession) {
        set(SecureKey.AUTH_SESSION, session)
        set(SecureKey.USER_TOKEN, session.accessToken)
    }

    fun getAuthSession(): AuthSession? {
        return get<AuthSession>(SecureKey.AUTH_SESSION)
    }

    fun saveDeviceId(id: String) {
        set(SecureKey.DEVICE_ID, id)
    }

    fun getDeviceId(): String {
        return get<String>(SecureKey.DEVICE_ID) ?: ""
    }

    fun printAll(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        val allKeys = prefs.allKeys() ?: emptyArray()

        for (key in allKeys) {
            val value: Any? = when {
                prefs.containsKey(key) -> {
                    // Try all primitive types first
                    when {
                        prefs.getString(key, null) != null -> {
                            val raw = prefs.getString(key, null)

                            // Try JSON decode for complex objects
                            try {
                                gson.fromJson(raw, Any::class.java)
                            } catch (e: Exception) {
                                raw
                            }
                        }

                        prefs.getInt(key, Int.MIN_VALUE) != Int.MIN_VALUE ->
                            prefs.getInt(key, 0)

                        prefs.getBoolean(key, false) || prefs.getBoolean(key, true) ->
                            prefs.getBoolean(key, false)

                        prefs.getLong(key, Long.MIN_VALUE) != Long.MIN_VALUE ->
                            prefs.getLong(key, 0L)

                        prefs.getFloat(key, Float.MIN_VALUE) != Float.MIN_VALUE ->
                            prefs.getFloat(key, 0f)

                        else -> null
                    }
                }

                else -> null
            }

            result[key] = value
        }

        return result
    }



    fun logAllData() {
        val all = printAll()
        println("----- MMKV STORED DATA START -----")
        all.forEach { (k, v) ->
            println("$k → $v")
        }
        println("----- MMKV STORED DATA END -----")
    }


    fun isLoggedIn(): Boolean {
        val token = get<String>(SecureKey.USER_TOKEN)
        return !token.isNullOrEmpty()
    }





}