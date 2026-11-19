package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.ActivateEmailAccountRequest
import com.example.mistreal_mini.data.api.ConnectEmailAccountRequest
import com.example.mistreal_mini.data.api.EmailAccountApiService
import com.example.mistreal_mini.data.api.EmailAccountConfig
import com.example.mistreal_mini.data.api.EmailInboxMessage
import com.example.mistreal_mini.data.api.EmailMessageDetail
import com.example.mistreal_mini.data.api.SendEmailAccountMessageRequest
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.util.DeviceIdProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BYOK personal email — connect your own account via IMAP/SMTP + an app
 * password and send/receive as yourself, distinct from the existing
 * send-only Email feature (InfoRepository's /email/send, which relays from
 * the app's own shared Gmail account and never touches an inbox).
 */
@Singleton
class EmailAccountRepository @Inject constructor(
    private val api: EmailAccountApiService,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId

    suspend fun connectAccount(
        label: String,
        emailAddress: String,
        imapHost: String,
        imapPort: Int,
        imapSecure: Boolean,
        smtpHost: String,
        smtpPort: Int,
        smtpSecure: Boolean,
        username: String,
        password: String
    ): Resource<EmailAccountConfig> = safeApiCall("Failed to connect email account") {
        val response = api.connectAccount(
            ConnectEmailAccountRequest(
                deviceId, label, emailAddress, imapHost, imapPort, imapSecure, smtpHost, smtpPort, smtpSecure, username, password
            )
        )
        if (response.success && response.config != null) Resource.Success(response.config)
        else Resource.Error(response.error ?: "Failed to connect email account")
    }

    suspend fun getAccounts(): Resource<List<EmailAccountConfig>> = safeApiCall("Failed to load email accounts") {
        val response = api.getAccounts(deviceId)
        if (response.success) Resource.Success(response.accounts ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load email accounts")
    }

    suspend fun activateAccount(accountId: Int): Resource<EmailAccountConfig> = safeApiCall("Failed to switch email account") {
        val response = api.activateAccount(ActivateEmailAccountRequest(deviceId, accountId))
        if (response.success && response.config != null) Resource.Success(response.config)
        else Resource.Error(response.error ?: "Failed to switch email account")
    }

    suspend fun deleteAccount(accountId: Int): Resource<Unit> = safeApiCall("Failed to remove email account") {
        val response = api.deleteAccount(accountId, deviceId)
        if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to remove email account")
    }

    suspend fun getInbox(): Resource<List<EmailInboxMessage>> = safeApiCall("Failed to load inbox") {
        val response = api.getInbox(deviceId)
        if (response.success) Resource.Success(response.messages ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load inbox")
    }

    suspend fun getMessage(uid: Int): Resource<EmailMessageDetail> = safeApiCall("Failed to load message") {
        val response = api.getMessage(uid, deviceId)
        if (response.success && response.message != null) Resource.Success(response.message)
        else Resource.Error(response.error ?: "Failed to load message")
    }

    suspend fun sendEmail(toEmail: String, subject: String, body: String): Resource<Unit> = safeApiCall("Failed to send email") {
        val response = api.sendEmail(SendEmailAccountMessageRequest(deviceId, toEmail, subject, body))
        if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to send email")
    }
}
