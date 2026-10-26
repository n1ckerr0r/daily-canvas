package com.n1ckerr0r.dailycanvas.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionStore(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "daily_canvas_session_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    val accessToken: String? get() = preferences.getString("access_token", null)
    val refreshToken: String? get() = preferences.getString("refresh_token", null)
    val email: String? get() = preferences.getString("email", null)
    fun hasSession(): Boolean = !accessToken.isNullOrBlank()
    fun save(auth: AuthResponseDto) = preferences.edit()
        .putString("access_token", auth.accessToken)
        .putString("refresh_token", auth.refreshToken)
        .putString("email", auth.user.email)
        .apply()
    fun saveTokens(tokens: RefreshTokenResponseDto) = preferences.edit()
        .putString("access_token", tokens.accessToken)
        .putString("refresh_token", tokens.refreshToken)
        .apply()
    fun clear() = preferences.edit().clear().apply()
}
