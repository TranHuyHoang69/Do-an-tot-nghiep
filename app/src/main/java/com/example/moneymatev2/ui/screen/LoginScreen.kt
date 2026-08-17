package com.example.moneymatev2.ui.screen


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.R
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.ui.theme.StringResource
import com.example.moneymatev2.ui.viewmodel.AuthUiState
import com.example.moneymatev2.ui.viewmodel.AuthViewModel
import com.example.moneymatev2.util.toUiMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel()
){
    val uiState by viewModel.uiState.collectAsState()
    var emailState by remember { mutableStateOf("") }
    var passwordState by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val isLoading = uiState is AuthUiState.Loading
    val error = (uiState as? AuthUiState.Error)?.error

    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
    ){
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.2f))
            Text(
                text = StringResource(StringRes.app_name),
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color =MaterialTheme.colorScheme.primaryContainer
            )
            Text(
                text = StringResource(StringRes.app_slogan),
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color =MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.weight(0.1f))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = StringResource(StringRes.welcome_back),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    EmailInputField(email = emailState, onEmailChange = { emailState = it })

                    Spacer(modifier = Modifier.height(16.dp))

                    PasswordInputField(password = passwordState, onPasswordChange = { passwordState = it })

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick ={
                            focusManager.clearFocus()
                            viewModel.signIn(emailState,passwordState)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = StringResource(StringRes.login),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                                )
                        }
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error.toUiMessage(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row {
                        Text(
                            text = StringResource(StringRes.no_account),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = StringResource(StringRes.register),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable{}
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = StringResource(StringRes.or),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    GoogleLoginButton(
                        onClick = {
                            viewModel.signInWithGoogle(context, context.getString(R.string.default_web_client_id))
                        },
                        isLoading = uiState is AuthUiState.Loading
                    )
                }
            }

        }
    }
}

@Composable
fun EmailInputField(email: String, onEmailChange: (String) -> Unit){
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = {Text(StringResource(StringRes.email))},
        placeholder ={Text(StringResource(StringRes.email))},
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
}

@Composable
fun PasswordInputField(password: String, onPasswordChange: (String) -> Unit){
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = {Text(StringResource(StringRes.password))},
        placeholder = {Text(StringResource(StringRes.password))},
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

@Composable
fun GoogleLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
){
    Surface(
        onClick = {if(!isLoading) onClick()},
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        color = if(isLoading){
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        }else{
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier.fillMaxWidth().height(50.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ){
            if (isLoading){
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }else{
                Icon(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = StringResource(StringRes.sign_in_with_google),
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = StringResource(StringRes.sign_in_with_google),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
