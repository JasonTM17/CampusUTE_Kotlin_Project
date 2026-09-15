package com.campusute.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Session token storage contract (unit tests use an in-memory fake). */
interface TokenStore {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun accessToken(): String?
    fun refreshToken(): String?
    fun clear()
}

/**
 * Keystore-backed storage (AES-256-GCM master key in Android Keystore).
 * Tokens never touch plaintext prefs — verified by review + the security
 * checklist in docs/security.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "campusute_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    override fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)

    override fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
