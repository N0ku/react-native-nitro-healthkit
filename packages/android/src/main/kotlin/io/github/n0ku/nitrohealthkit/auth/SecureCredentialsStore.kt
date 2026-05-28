package io.github.n0ku.nitrohealthkit.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted persistent storage for the JWT and backend URL that
 * [io.github.n0ku.nitrohealthkit.workers.HealthSyncWorker] needs at execution time.
 *
 * Backed by [EncryptedSharedPreferences] (AES-256-GCM via a [MasterKey] in the Android Keystore).
 * Values are wiped via [clear] on logout / unregister.
 *
 * The store falls back to a plain [SharedPreferences] only on devices where the Keystore is
 * unavailable (rare, mostly emulators with broken HW backed keys). The fallback is logged loudly
 * so the failure mode is visible to operators.
 */
class SecureCredentialsStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { openPrefs() }

    fun store(
        apiBaseUrl: String,
        jwtToken: String,
        syncPath: String,
        types: List<String>,
        intervalMinutes: Int,
    ) {
        prefs.edit()
            .putString(KEY_API_BASE_URL, apiBaseUrl)
            .putString(KEY_JWT, jwtToken)
            .putString(KEY_SYNC_PATH, syncPath)
            .putString(KEY_TYPES, types.joinToString(","))
            .putInt(KEY_INTERVAL_MIN, intervalMinutes)
            .apply()
    }

    fun apiBaseUrl(): String? = prefs.getString(KEY_API_BASE_URL, null)

    fun jwt(): String? = prefs.getString(KEY_JWT, null)

    fun syncPath(): String = prefs.getString(KEY_SYNC_PATH, DEFAULT_SYNC_PATH) ?: DEFAULT_SYNC_PATH

    fun types(): List<String> =
        prefs.getString(KEY_TYPES, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun intervalMinutes(): Int = prefs.getInt(KEY_INTERVAL_MIN, DEFAULT_INTERVAL_MIN)

    fun storedChangesTokens(): Map<String, String> =
        prefs.all.entries
            .filter { it.key.startsWith(KEY_CHANGES_TOKEN_PREFIX) && it.value is String }
            .associate { it.key.removePrefix(KEY_CHANGES_TOKEN_PREFIX) to it.value as String }

    fun saveChangesToken(recordTypeName: String, token: String) {
        prefs.edit().putString(KEY_CHANGES_TOKEN_PREFIX + recordTypeName, token).apply()
    }

    fun clearChangesTokens() {
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(KEY_CHANGES_TOKEN_PREFIX) }.forEach { remove(it) }
            apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun openPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.e(
            TAG,
            "EncryptedSharedPreferences unavailable — falling back to plain SharedPreferences. " +
                "This should not happen on a real device.",
            e,
        )
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "HCSecureStore"
        private const val ENCRYPTED_PREFS_NAME = "healthkit_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "healthkit_fallback_prefs"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_JWT = "jwt"
        private const val KEY_SYNC_PATH = "sync_path"
        private const val KEY_TYPES = "types"
        private const val KEY_INTERVAL_MIN = "interval_minutes"
        private const val KEY_CHANGES_TOKEN_PREFIX = "changes_token_"
        const val DEFAULT_SYNC_PATH = "/users/health-data"
        const val DEFAULT_INTERVAL_MIN = 15
    }
}
