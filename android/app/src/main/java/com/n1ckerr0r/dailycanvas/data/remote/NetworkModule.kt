package com.n1ckerr0r.dailycanvas.data.remote

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.n1ckerr0r.dailycanvas.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }
    lateinit var session: SessionStore
        private set

    fun initialize(context: Context) {
        if (!::session.isInitialized) session = SessionStore(context.applicationContext)
    }

    private val refreshClient = OkHttpClient.Builder().build()

    private fun refreshedAccessToken(): String? {
        val refreshToken = session.refreshToken ?: return null
        val body = json.encodeToString(RefreshTokenRequestDto(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}auth/token/refresh")
            .post(body)
            .build()
        return runCatching {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    session.clear()
                    return null
                }
				val tokens = json.decodeFromString<RefreshTokenResponseDto>(response.body?.string().orEmpty())
				session.saveTokens(tokens)
				tokens.accessToken
            }
        }.getOrElse {
            session.clear()
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val token = if (::session.isInitialized) session.accessToken else null
            val authorized = if (token.isNullOrBlank() || request.url.encodedPath.endsWith("/auth/login") || request.url.encodedPath.endsWith("/auth/register") || request.url.encodedPath.endsWith("/auth/token/refresh")) request else request.newBuilder().header("Authorization", "Bearer $token").build()
            chain.proceed(authorized)
        })
        .addInterceptor(HttpLoggingInterceptor().apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE })
        .authenticator { _, response ->
            if (responseCount(response) >= 2 || response.request.url.encodedPath.contains("/auth/")) return@authenticator null
            synchronized(this) {
                val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                val currentToken = session.accessToken
                val token = if (!currentToken.isNullOrBlank() && currentToken != failedToken) currentToken else refreshedAccessToken()
                token?.let { response.request.newBuilder().header("Authorization", "Bearer $it").build() }
            }
        }
        .build()

    val api: DailyCanvasApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DailyCanvasApi::class.java)
}
