package com.example.data.remote

import com.example.data.remote.dto.DriveAboutResponse
import com.example.data.remote.dto.DriveFileListResponse
import com.example.data.remote.dto.OAuthTokenResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleDriveApiService {

    @GET("drive/v3/about")
    suspend fun getAbout(
        @Header("Authorization") authHeader: String,
        @Query("fields") fields: String = "user,storageQuota"
    ): Response<DriveAboutResponse>

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("q") query: String = "trashed = false",
        @Query("fields") fields: String = "files(id,name,mimeType,size,modifiedTime,webViewLink,iconLink,thumbnailLink),nextPageToken",
        @Query("orderBy") orderBy: String = "modifiedTime desc"
    ): Response<DriveFileListResponse>

    @retrofit2.http.DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String
    ): Response<Unit>

    @GET("drive/v3/changes/startPageToken")
    suspend fun getStartPageToken(
        @Header("Authorization") authHeader: String
    ): Response<com.example.data.remote.dto.DriveStartPageTokenResponse>

    @GET("drive/v3/changes")
    suspend fun getChanges(
        @Header("Authorization") authHeader: String,
        @Query("pageToken") pageToken: String,
        @Query("fields") fields: String = "changes(file(id,name,mimeType,size,modifiedTime,trashed,webViewLink,iconLink,thumbnailLink),removed,fileId),nextPageToken,newStartPageToken",
        @Query("pageSize") pageSize: Int = 100
    ): Response<com.example.data.remote.dto.DriveChangesResponse>
}

interface GoogleOAuthService {
    @FormUrlEncoded
    @POST("token")
    suspend fun exchangeCode(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): Response<OAuthTokenResponse>

    @FormUrlEncoded
    @POST("token")
    suspend fun refreshToken(
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("grant_type") grantType: String = "refresh_token"
    ): Response<OAuthTokenResponse>
}
