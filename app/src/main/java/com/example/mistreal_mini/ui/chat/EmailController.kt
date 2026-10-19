package com.example.mistreal_mini.ui.chat

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.EmailContactSummary
import com.example.mistreal_mini.data.api.EmailMessage
import com.example.mistreal_mini.data.repository.InfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Compose & send email (drawer "EMAIL" category) — send-only, no inbox
 * sync. Extracted out of ChatViewModel, which just owns an instance of
 * this and forwards to it; was fully self-contained there already.
 */
class EmailController(
    private val infoRepository: InfoRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val emitEvent: (String) -> Unit
) {
    private val _emailContacts = mutableStateListOf<EmailContactSummary>()
    val emailContacts: List<EmailContactSummary> = _emailContacts

    private val _activeEmailThread = mutableStateListOf<EmailMessage>()
    val activeEmailThread: List<EmailMessage> = _activeEmailThread

    private val _isSendingEmail = mutableStateOf(false)
    val isSendingEmail: State<Boolean> = _isSendingEmail

    private fun deviceId() = com.example.mistreal_mini.util.DeviceIdProvider(context).deviceId

    fun fetchEmailContacts() {
        scope.launch {
            val result = infoRepository.getEmailContacts(deviceId())
            if (result is Resource.Success) {
                _emailContacts.clear()
                _emailContacts.addAll(result.data ?: emptyList())
            }
        }
    }

    fun openEmailThread(toEmail: String) {
        scope.launch {
            val result = infoRepository.getEmailHistory(deviceId(), toEmail)
            _activeEmailThread.clear()
            if (result is Resource.Success) {
                _activeEmailThread.addAll(result.data ?: emptyList())
            }
        }
    }

    fun clearActiveEmailThread() {
        _activeEmailThread.clear()
    }

    fun sendEmail(toEmail: String, toName: String?, subject: String, body: String, onResult: (Boolean) -> Unit) {
        if (toEmail.isBlank() || subject.isBlank() || body.isBlank()) {
            emitEvent("Address, subject and message are all required.")
            onResult(false)
            return
        }
        _isSendingEmail.value = true
        scope.launch {
            val result = infoRepository.sendEmail(deviceId(), toEmail, toName, subject, body)
            _isSendingEmail.value = false
            when (result) {
                is Resource.Success -> {
                    emitEvent("Email sent to $toEmail")
                    fetchEmailContacts()
                    openEmailThread(toEmail)
                    onResult(true)
                }
                is Resource.Error -> {
                    emitEvent("Email failed: ${result.message}")
                    onResult(false)
                }
                else -> onResult(false)
            }
        }
    }
}
