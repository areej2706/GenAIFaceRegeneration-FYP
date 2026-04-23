package com.fjwu.pencil2pexel.network

import com.google.gson.annotations.SerializedName

// ============ Authentication Models ============

// Signup Request - matches API: POST /auth/signup
data class SignupRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

// Login Request - matches API: POST /auth/login
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

// User data returned in auth responses
data class User(
    @SerializedName("user_id") val userId: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("total_images") val totalImages: Int? = null
)

// Auth Response - used for both signup and login
data class AuthResponse(
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: User,
    @SerializedName("token") val token: String
)

// Profile Response - matches API: GET /auth/profile
data class ProfileResponse(
    @SerializedName("user") val user: User
)

// Update Profile Request - matches API: PUT /auth/profile
data class UpdateProfileRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String? = null
)

// Update Profile Response
data class UpdateProfileResponse(
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: User
)

// ============ Image Generation Models ============

// Generate image response (when format=base64)
data class GenerateImageResponse(
    @SerializedName("image") val image: String,
    @SerializedName("format") val format: String,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("size") val size: List<Int>? = null,
    @SerializedName("image_id") val imageId: String? = null,
    @SerializedName("saved") val saved: Boolean? = null
)

// Batch generate response
data class BatchGenerateResponse(
    @SerializedName("results") val results: List<BatchImageResult>,
    @SerializedName("count") val count: Int
)

data class BatchImageResult(
    @SerializedName("filename") val filename: String,
    @SerializedName("image") val image: String,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("size") val size: List<Int>? = null,
    @SerializedName("image_id") val imageId: String? = null,
    @SerializedName("saved") val saved: Boolean? = null
)

// ============ History Models ============

// History list response - matches API: GET /history
data class HistoryResponse(
    @SerializedName("images") val images: List<HistoryImage>,
    @SerializedName("pagination") val pagination: Pagination
)

data class HistoryImage(
    @SerializedName("image_id") val imageId: String,
    @SerializedName("original_filename") val originalFilename: String,
    @SerializedName("attributes") val attributes: String,
    @SerializedName("created_at") val createdAt: String
)

data class Pagination(
    @SerializedName("page") val page: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("pages") val pages: Int
)

// Delete history response
data class DeleteHistoryResponse(
    @SerializedName("message") val message: String,
    @SerializedName("deleted_count") val deletedCount: Int? = null
)

// ============ Health Check Models ============

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("device") val device: String
)

// ============ Error Response ============

data class ErrorResponse(
    @SerializedName("error") val error: String
)

// ============ Save to History Models ============

// Save to History Request
data class SaveToHistoryRequest(
    @SerializedName("image") val image: String, // base64 encoded image
    @SerializedName("filename") val filename: String,
    @SerializedName("attributes") val attributes: String? = null
)

// Save to History Response
data class SaveToHistoryResponse(
    @SerializedName("message") val message: String,
    @SerializedName("image_id") val imageId: String,
    @SerializedName("saved") val saved: Boolean
)

