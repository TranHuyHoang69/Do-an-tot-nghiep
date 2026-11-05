package com.example.moneymatev2.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.R
import com.example.moneymatev2.domain.model.PasswordResetError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordResetScreen(
    onBack: () -> Unit,
    viewModel: PasswordResetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val loading = state.status == PasswordResetStatus.Loading
    val accepted = state.status == PasswordResetStatus.Accepted
    val error = (state.status as? PasswordResetStatus.Error)?.reason

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.password_reset_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.password_reset_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                isError = error == PasswordResetError.EmptyEmail ||
                    error == PasswordResetError.InvalidEmail,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                val message = when (error) {
                    PasswordResetError.EmptyEmail -> R.string.password_reset_empty_email
                    PasswordResetError.InvalidEmail -> R.string.password_reset_invalid_email
                    PasswordResetError.Network -> R.string.password_reset_network_error
                    PasswordResetError.TooManyRequests -> R.string.password_reset_too_many_requests
                    PasswordResetError.Unavailable -> R.string.password_reset_unavailable
                }
                Text(stringResource(message), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth())
            }
            if (accepted) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.password_reset_accepted),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = viewModel::submit, enabled = !loading && !accepted,
                modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(stringResource(R.string.password_reset_send))
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.password_reset_back_to_login))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.password_reset_google_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
