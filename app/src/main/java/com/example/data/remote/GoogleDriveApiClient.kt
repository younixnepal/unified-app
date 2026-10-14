package com.example.data.remote

import com.example.data.remote.dto.DriveAboutResponse
import com.example.data.remote.dto.DriveFileDto
import com.example.data.remote.dto.OAuthTokenResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class DriveApiException(message: String, val statusCode: Int? = null) : Exception(message) {
    class TokenExpiredException(msg: String = "Google OAuth access token has expired. Please re-authenticate.") : DriveApiException(msg, 401)
    class RateLimitExceededException(msg: String = "Google Drive API rate limit reached. Please wait before retrying.") : DriveApiException(msg, 429)
    class InsufficientStorageException(msg: String = "Google Drive account has insufficient storage quota.") : DriveApiException(msg, 403)
    class InsufficientPermissionsException(msg: String = "Drive file permission denied. Reconnect with drive.file scope.") : DriveApiException(msg, 403)
    class GenericApiException(msg: String, code: Int? = null) : DriveApiException(msg, code)
}

class GoogleDriveApiClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val driveRetrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val oauthRetrofit = Retrofit.Builder()
        .baseUrl("https://oauth2.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val driveService = driveRetrofit.create(GoogleDriveApiService::class.java)
    private val oauthService = oauthRetrofit.create(GoogleOAuthService::class.java)

    suspend fun getAbout(accessToken: String): Result<DriveAboutResponse> {
        return try {
            val response = driveService.getAbout("Bearer $accessToken")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun listFiles(accessToken: String, searchQuery: String? = null): Result<List<DriveFileDto>> {
        return try {
            val query = if (searchQuery.isNullOrBlank()) {
                "trashed = false"
            } else {
                "trashed = false and name contains '${searchQuery.replace("'", "\\'")}'"
            }
            val response = driveService.listFiles(
                authHeader = "Bearer $accessToken",
                pageSize = 100,
                query = query
            )
            if (response.isSuccessful) {
                Result.success(response.body()?.files ?: emptyList())
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun uploadFile(
        accessToken: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): Result<DriveFileDto> {
        return try {
            // Google Drive API Multipart upload:
            // POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("mimeType", mimeType)
            }.toString()

            val boundary = "====UnifiedCloudDriveBoundary${System.currentTimeMillis()}===="
            val multipartBody = MultipartBody.Builder(boundary)
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addFormDataPart(
                    "file",
                    fileName,
                    fileBytes.toRequestBody(mimeType.toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val fileDto = DriveFileDto(
                    id = json.optString("id", System.currentTimeMillis().toString()),
                    name = json.optString("name", fileName),
                    mimeType = json.optString("mimeType", mimeType),
                    size = fileBytes.size.toString(),
                    modifiedTime = json.optString("modifiedTime", null),
                    webViewLink = json.optString("webViewLink", null),
                    iconLink = json.optString("iconLink", null),
                    thumbnailLink = json.optString("thumbnailLink", null)
                )
                Result.success(fileDto)
            } else {
                Result.failure(mapErrorResponse(response.code, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> {
        return try {
            val response = driveService.deleteFile("Bearer $accessToken", fileId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun getStartPageToken(accessToken: String): Result<String> {
        return try {
            val response = driveService.getStartPageToken("Bearer $accessToken")
            if (response.isSuccessful && response.body()?.startPageToken != null) {
                Result.success(response.body()!!.startPageToken!!)
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun getChanges(
        accessToken: String,
        pageToken: String
    ): Result<com.example.data.remote.dto.DriveChangesResponse> {
        return try {
            val response = driveService.getChanges(
                authHeader = "Bearer $accessToken",
                pageToken = pageToken
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun refreshToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String?
    ): Result<OAuthTokenResponse> {
        return try {
            val response = oauthService.refreshToken(
                refreshToken = refreshToken,
                clientId = clientId,
                clientSecret = clientSecret
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun exchangeCode(
        code: String,
        clientId: String,
        clientSecret: String?,
        redirectUri: String
    ): Result<OAuthTokenResponse> {
        return try {
            val response = oauthService.exchangeCode(
                code = code,
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(mapErrorResponse(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    private fun mapErrorResponse(code: Int, errorBody: String?): DriveApiException {
        val message = parseErrorMessage(errorBody) ?: "HTTP $code"
        return when (code) {
            401 -> DriveApiException.TokenExpiredException("Token expired or unauthorized: $message")
            403 -> {
                if (message.contains("quota", ignoreCase = true) || message.contains("storage", ignoreCase = true)) {
                    DriveApiException.InsufficientStorageException("Insufficient Drive storage quota: $message")
                } else if (message.contains("rate", ignoreCase = true) || message.contains("userRateLimitExceeded", ignoreCase = true)) {
                    DriveApiException.RateLimitExceededException("Rate limit exceeded: $message")
                } else {
                    DriveApiException.InsufficientPermissionsException("Access denied: $message")
                }
            }
            429 -> DriveApiException.RateLimitExceededException("Google Drive API rate limit reached (HTTP 429). Please retry later.")
            else -> DriveApiException.GenericApiException("Google Drive API error ($code): $message", code)
        }
    }

    private fun mapException(e: Exception): Exception {
        return when (e) {
            is DriveApiException -> e
            is IOException -> DriveApiException.GenericApiException("Network connection error. Please verify your internet connection.")
            else -> DriveApiException.GenericApiException(e.message ?: "Unexpected error occurred")
        }
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                if (errObj != null) {
                    errObj.optString("message", errorBody)
                } else {
                    json.optString("error_description", json.optString("error", errorBody))
                }
            } else {
                errorBody
            }
        } catch (_: Exception) {
            errorBody
        }
    }
}
