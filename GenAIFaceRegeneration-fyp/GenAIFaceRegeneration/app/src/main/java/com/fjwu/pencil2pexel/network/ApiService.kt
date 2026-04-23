package com.fjwu.pencil2pexel.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ============ Authentication Endpoints ============

    /**
     * Create a new user account
     * POST /auth/signup
     */
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    /**
     * Login to existing account
     * POST /auth/login
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    /**
     * Get current user profile
     * GET /auth/profile
     */
    @GET("auth/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    /**
     * Update user profile
     * PUT /auth/profile
     */
    @PUT("auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<UpdateProfileResponse>

    // ============ Image Generation Endpoints ============

    /**
     * Check API health status
     * GET /health
     */
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    /**
     * Generate single image (returns base64 JSON)
     * POST /generate
     */
    @Multipart
    @POST("generate")
    suspend fun generateImage(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part,
        @Part("attributes") attributes: RequestBody? = null,
        @Part("format") format: RequestBody? = null,
        @Part("save") save: RequestBody? = null,
        @Part("quality") quality: RequestBody? = null,
        @Part("enhancement") enhancement: RequestBody? = null,
        @Part("upscale") upscale: RequestBody? = null
    ): Response<GenerateImageResponse>

    /**
     * Generate single image (returns PNG binary)
     * POST /generate
     */
    @Multipart
    @POST("generate")
    suspend fun generateImageBinary(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part,
        @Part("attributes") attributes: RequestBody? = null,
        @Part("format") format: RequestBody? = null,
        @Part("quality") quality: RequestBody? = null,
        @Part("enhancement") enhancement: RequestBody? = null,
        @Part("upscale") upscale: RequestBody? = null
    ): Response<ResponseBody>

    /**
     * Generate batch images
     * POST /generate-batch
     */
    @Multipart
    @POST("generate-batch")
    suspend fun generateBatch(
        @Header("Authorization") token: String,
        @Part images: List<MultipartBody.Part>,
        @Part("attributes") attributes: RequestBody? = null,
        @Part("save") save: RequestBody? = null,
        @Part("quality") quality: RequestBody? = null,
        @Part("enhancement") enhancement: RequestBody? = null,
        @Part("upscale") upscale: RequestBody? = null
    ): Response<BatchGenerateResponse>

    // ============ History Endpoints ============

    /**
     * Get paginated history
     * GET /history
     */
    @GET("history")
    suspend fun getHistory(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): Response<HistoryResponse>

    /**
     * Get specific history image (returns PNG binary)
     * GET /history/{image_id}
     */
    @GET("history/{image_id}")
    suspend fun getHistoryImage(
        @Header("Authorization") token: String,
        @Path("image_id") imageId: String
    ): Response<ResponseBody>

    /**
     * Delete specific image from history
     * DELETE /history/{image_id}
     */
    @DELETE("history/{image_id}")
    suspend fun deleteHistoryImage(
        @Header("Authorization") token: String,
        @Path("image_id") imageId: String
    ): Response<DeleteHistoryResponse>

    /**
     * Clear all history
     * DELETE /history
     */
    @DELETE("history")
    suspend fun clearHistory(
        @Header("Authorization") token: String
    ): Response<DeleteHistoryResponse>

    /**
     * Save already-generated image to history
     * POST /history/save
     */
    @POST("history/save")
    suspend fun saveToHistory(
        @Header("Authorization") token: String,
        @Body request: SaveToHistoryRequest
    ): Response<SaveToHistoryResponse>
}

