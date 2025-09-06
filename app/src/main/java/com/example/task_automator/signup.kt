package com.example.task_automator // Or your UI package e.g., com.example.task_automator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.task_automator.ui.theme.TaskAutomatorTheme // Make sure this import matches your theme location

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupClicked: (String, String, String) -> Unit, // username, email, password
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var usernameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Makes the column scrollable if content overflows
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Username Input Field
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                usernameError = null
            },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = usernameError != null
        )
        usernameError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Email Input Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            isError = emailError != null
        )
        emailError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Password Input Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
                // Also clear confirm password error if password changes and they matched
                if (confirmPasswordError == "Passwords do not match.") {
                    confirmPasswordError = null
                }
            },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = passwordError != null,
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        )
        passwordError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Confirm Password Input Field
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmPasswordError = null
            },
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = confirmPasswordError != null,
            trailingIcon = {
                val image = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                val description = if (confirmPasswordVisible) "Hide password" else "Show password"
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        )
        confirmPasswordError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Signup Button
        Button(
            onClick = {
                var valid = true
                if (username.isBlank()) {
                    usernameError = "Username cannot be empty"
                    valid = false
                }
                if (email.isBlank()) {
                    emailError = "Email cannot be empty"
                    valid = false
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // Basic email format check
                    emailError = "Enter a valid email address"
                    valid = false
                }
                if (password.isBlank()) {
                    passwordError = "Password cannot be empty"
                    valid = false
                } else if (password.length < 6) { // Example: minimum password length
                    passwordError = "Password must be at least 6 characters"
                    valid = false
                }
                if (confirmPassword.isBlank()) {
                    confirmPasswordError = "Please confirm your password"
                    valid = false
                } else if (password != confirmPassword) {
                    confirmPasswordError = "Passwords do not match"
                    valid = false
                }

                if (valid) {
                    onSignupClicked(username, email, password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Link to Login Screen
        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Login")
        }
    }
}

@Preview(showBackground = true, device = "spec:width=360dp,height=740dp,dpi=480")
@Composable
fun SignupScreenPreview() {
    TaskAutomatorTheme {
        SignupScreen(
            onSignupClicked = { user, mail, pass ->
                println("Preview Signup: User: $user, Email: $mail, Pass: $pass")
            },
            onNavigateToLogin = {
                println("Preview Navigate to Login Clicked")
            }
        )
    }
}