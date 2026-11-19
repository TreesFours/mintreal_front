package com.example.mistreal_mini.ui.email

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.Resource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposeScreen(
    onBack: () -> Unit,
    viewModel: EmailAccountViewModel = hiltViewModel()
) {
    var toEmail by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sendResult.collect { result ->
            when (result) {
                is Resource.Success -> onBack()
                is Resource.Error -> snackbarHostState.showSnackbar(result.message ?: "Failed to send")
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Email") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = toEmail, onValueChange = { toEmail = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Button(
                onClick = { viewModel.sendEmail(toEmail, subject, body) },
                enabled = !isSending && toEmail.isNotBlank() && subject.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
}
