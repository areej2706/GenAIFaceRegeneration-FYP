package com.fjwu.pencil2pexel.network

import android.content.Context
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:5000/" // Standard Android emulator host IP
    private const val PREFS_NAME = "pencil2pixel_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null
    private var currentBaseUrl: String = DEFAULT_BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Get or create the API service instance
     */
    fun getApiService(context: Context): ApiService {
        val baseUrl = getBaseUrl(context)

        // Recreate if base URL changed
        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit!!.create(ApiService::class.java)
        }

        return apiService!!
    }

    /**
     * Get the base URL from preferences or use default
     */
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return normalizeUrl(url)
    }

    /**
     * Set a custom base URL
     */
    fun setBaseUrl(context: Context, url: String) {
        val processedUrl = normalizeUrl(url.trim())

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, processedUrl).apply()
        
        // Force recreation of retrofit instance
        retrofit = null
        apiService = null
        currentBaseUrl = processedUrl
    }

    /**
     * Normalize URL to ensure proper format with http:// scheme and trailing slash.
     * This handles common input errors like leading/trailing slashes or missing schemes.
     */
    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return DEFAULT_BASE_URL

        // 1. Remove any existing scheme-like prefix (http://, https://, http:/, or //)
        val noScheme = trimmed.replace(Regex("^(https?://|https?:|//)", RegexOption.IGNORE_CASE), "")
        
        // 2. Remove ALL leading and trailing slashes to get a clean host[:port]
        // This specifically fixes the "http:///ip" (triple slash) issue
        val cleanHost = noScheme.trim { it == '/' || it.isWhitespace() }
        
        if (cleanHost.isEmpty()) return DEFAULT_BASE_URL
        
        // 3. Reconstruct the URL forcing http:// and exactly one trailing slash
        // Retrofit requires base URLs to end with a forward slash.
        return "http://$cleanHost/"
    }

    /**
     * Reset base URL to default
     */
    fun resetBaseUrl(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_BASE_URL).apply()

        // Force recreation of retrofit instance
        retrofit = null
        apiService = null
        currentBaseUrl = DEFAULT_BASE_URL
    }

    /**
     * Save authentication data after successful login/signup
     */
    fun saveAuthData(context: Context, token: String, user: User) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            putString(KEY_USER_ID, user.userId)
            putString(KEY_USER_NAME, user.username)
            putString(KEY_USER_EMAIL, user.email)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    /**
     * Get the auth token formatted for Authorization header
     */
    fun getAuthToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_AUTH_TOKEN, null)
        return token?.let { "Bearer $it" }
    }

    /**
     * Get raw token without Bearer prefix
     */
    fun getRawToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) &&
               prefs.getString(KEY_AUTH_TOKEN, null) != null
    }

    /**
     * Get saved user ID
     */
    fun getUserId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null)
    }

    /**
     * Get saved username
     */
    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, null)
    }

    /**
     * Get saved user email
     */
    fun getUserEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Clear all auth data (logout)
     */
    fun clearAuthData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
    }

    /**
     * Update saved user data
     */
    fun updateUserData(context: Context, username: String? = null, email: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            username?.let { putString(KEY_USER_NAME, it) }
            email?.let { putString(KEY_USER_EMAIL, it) }
            apply()
        }
    }

    /**
     * Parse error response from API
     */
    fun parseError(errorBody: String?): String {
        return try {
            errorBody?.let {
                val errorResponse = Gson().fromJson(it, ErrorResponse::class.java)
                errorResponse.error
            } ?: "Unknown error occurred"
        } catch (e: Exception) {
            errorBody ?: "Unknown error occurred"
        }
    }
}
