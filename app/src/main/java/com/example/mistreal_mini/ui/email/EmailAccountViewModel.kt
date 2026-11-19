package com.example.mistreal_mini.ui.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.EmailAccountConfig
import com.example.mistreal_mini.data.api.EmailInboxMessage
import com.example.mistreal_mini.data.api.EmailMessageDetail
import com.example.mistreal_mini.data.repository.EmailAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmailAccountViewModel @Inject constructor(
    private val repository: EmailAccountRepository
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<EmailAccountConfig>>(emptyList())
    val accounts: StateFlow<List<EmailAccountConfig>> = _accounts.asStateFlow()

    val activeAccount: EmailAccountConfig?
        get() = _accounts.value.firstOrNull { it.isActive }

    private val _inbox = MutableStateFlow<List<EmailInboxMessage>>(emptyList())
    val inbox: StateFlow<List<EmailInboxMessage>> = _inbox.asStateFlow()

    private val _isLoadingInbox = MutableStateFlow(false)
    val isLoadingInbox: StateFlow<Boolean> = _isLoadingInbox.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    private val _selectedMessage = MutableStateFlow<EmailMessageDetail?>(null)
    val selectedMessage: StateFlow<EmailMessageDetail?> = _selectedMessage.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sendResult = MutableSharedFlow<Resource<Unit>>()
    val sendResult = _sendResult.asSharedFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        viewModelScope.launch {
            val result = repository.getAccounts()
            if (result is Resource.Success) {
                _accounts.value = result.data ?: emptyList()
                if (activeAccount != null) loadInbox()
            }
        }
    }

    fun connectAccount(
        label: String, emailAddress: String,
        imapHost: String, imapPort: Int, imapSecure: Boolean,
        smtpHost: String, smtpPort: Int, smtpSecure: Boolean,
        username: String, password: String
    ) {
        viewModelScope.launch {
            _isConnecting.value = true
            _connectError.value = null
            val result = repository.connectAccount(label, emailAddress, imapHost, imapPort, imapSecure, smtpHost, smtpPort, smtpSecure, username, password)
            _isConnecting.value = false
            if (result is Resource.Success) {
                loadAccounts()
            } else {
                _connectError.value = (result as? Resource.Error)?.message ?: "Couldn't connect this account."
            }
        }
    }

    fun switchAccount(accountId: Int) {
        viewModelScope.launch {
            val result = repository.activateAccount(accountId)
            if (result is Resource.Success) loadAccounts()
        }
    }

    fun removeAccount(accountId: Int) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
            loadAccounts()
        }
    }

    fun loadInbox() {
        viewModelScope.launch {
            _isLoadingInbox.value = true
            val result = repository.getInbox()
            if (result is Resource.Success) _inbox.value = result.data ?: emptyList()
            _isLoadingInbox.value = false
        }
    }

    fun loadMessage(uid: Int) {
        viewModelScope.launch {
            _selectedMessage.value = null
            val result = repository.getMessage(uid)
            if (result is Resource.Success) _selectedMessage.value = result.data
        }
    }

    fun sendEmail(toEmail: String, subject: String, body: String) {
        viewModelScope.launch {
            _isSending.value = true
            val result = repository.sendEmail(toEmail, subject, body)
            _isSending.value = false
            _sendResult.emit(result)
        }
    }
}
