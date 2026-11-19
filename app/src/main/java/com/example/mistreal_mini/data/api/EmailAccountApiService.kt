package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EmailAccountApiService {

    @POST("api/email-account/connect")
    suspend fun connectAccount(@Body request: ConnectEmailAccountRequest): EmailAccountResponse

    @GET("api/email-account/accounts")
    suspend fun getAccounts(@Query("deviceId") deviceId: String): EmailAccountListResponse

    @POST("api/email-account/accounts/activate")
    suspend fun activateAccount(@Body request: ActivateEmailAccountRequest): EmailAccountResponse

    @DELETE("api/email-account/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Int, @Query("deviceId") deviceId: String): GenericSuccessResponse

    @GET("api/email-account/inbox")
    suspend fun getInbox(@Query("deviceId") deviceId: String): EmailInboxResponse

    @GET("api/email-account/messages/{uid}")
    suspend fun getMessage(@Path("uid") uid: Int, @Query("deviceId") deviceId: String): EmailMessageDetailResponse

    @POST("api/email-account/send")
    suspend fun sendEmail(@Body request: SendEmailAccountMessageRequest): GenericSuccessResponse
}

data class ConnectEmailAccountRequest(
    val deviceId: String,
    val label: String,
    val emailAddress: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecure: Boolean = true,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecure: Boolean = true,
    val username: String,
    val password: String
)

data class ActivateEmailAccountRequest(val deviceId: String, val accountId: Int)

data class SendEmailAccountMessageRequest(val deviceId: String, val toEmail: String, val subject: String, val body: String)

// Password never comes back from the server — write-only, same convention
// as cloud storage / AI BYOK config endpoints.
data class EmailAccountConfig(
    val id: Int,
    val label: String,
    val emailAddress: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val isActive: Boolean
)

data class EmailAccountResponse(val success: Boolean, val config: EmailAccountConfig? = null, val error: String? = null)
data class EmailAccountListResponse(val success: Boolean, val accounts: List<EmailAccountConfig>? = null, val error: String? = null)

data class EmailInboxMessage(
    val uid: Int,
    val from: String?,
    val subject: String?,
    val date: String?,
    val seen: Boolean
)

data class EmailInboxResponse(val success: Boolean, val messages: List<EmailInboxMessage>? = null, val error: String? = null)

data class EmailMessageDetail(
    val subject: String?,
    val from: String?,
    val date: String?,
    val text: String?,
    val html: String?
)

data class EmailMessageDetailResponse(val success: Boolean, val message: EmailMessageDetail? = null, val error: String? = null)
