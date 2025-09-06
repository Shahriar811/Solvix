package com.example.task_automator // Or your UI package e.g., com.example.task_automator.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility // For password toggle
import androidx.compose.material.icons.filled.VisibilityOff // For password toggle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.task_automator.ui.theme.TaskAutomatorTheme // Make sure this import matches your theme location
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginClicked: (String, String) -> Unit,
    onNavigateToSignup: () -> Unit,
    onGoogleSignInSuccess: () -> Unit,      // Callback for successful Google Sign-In
    onGoogleSignInFailed: (String) -> Unit // Callback for failed Google Sign-In
) {
    // State variables for email/password login
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) } // For password visibility toggle

    // --- Google Sign-In Specific Code ---
    val context = LocalContext.current
    val firebaseAuth: FirebaseAuth = remember { Firebase.auth } // Use remember for Firebase.auth

    // Configure Google Sign-In
    // IMPORTANT: Replace "YOUR_WEB_CLIENT_ID" with your actual Web Client ID from Firebase console
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("893162076258-8mv5t2cu60821rt4p999qngf3c8g3pfb.apps.googleusercontent.com") // Request ID token for Firebase
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            // Google Sign In was successful, authenticate with Firebase
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)!!
            Log.d("LOGIN_GOOGLE", "firebaseAuthWithGoogle:${account.id}")
            firebaseAuthWithGoogle(account.idToken!!, firebaseAuth, onGoogleSignInSuccess, onGoogleSignInFailed)
        } catch (e: ApiException) {
            // Google Sign In failed
            Log.w("LOGIN_GOOGLE", "Google sign in failed", e)
            onGoogleSignInFailed("Google Sign-In failed (code: ${e.statusCode}). Please try again.")
        }
    }
    // --- End Google Sign-In Specific Code ---

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Make it scrollable
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

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

        Spacer(modifier = Modifier.height(24.dp))

        // Login Button (Email/Password)
        Button(
            onClick = {
                var valid = true
                if (email.isBlank()) {
                    emailError = "Email cannot be empty"
                    valid = false
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                    emailError = "Enter a valid email address"
                    valid = false
                }
                if (password.isBlank()) {
                    passwordError = "Password cannot be empty"
                    valid = false
                }
                // Add more password validation if needed (e.g., length)

                if (valid) {
                    onLoginClicked(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login with Email")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Sign-In Button
        Button(
            onClick = {
                // Optional: Sign out previous user first to ensure a fresh sign-in attempt
                // This can help if the user wants to switch Google accounts.
                // googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                // }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // Example styling
        ) {
            // Consider adding a Google Icon here
            // e.g., Icon(painter = painterResource(id = R.drawable.ic_google_logo), contentDescription = "Google logo")
            Text("Sign in with Google", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Link to Signup Screen
        TextButton(onClick = onNavigateToSignup) {
            Text("Don't have an account? Sign Up")
        }
    }
}

private fun firebaseAuthWithGoogle(
    idToken: String,
    auth: FirebaseAuth,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("LOGIN_GOOGLE_FIREBASE", "Firebase signInWithCredential:success")
                // val user = auth.currentUser // You can get user info here if needed
                onSuccess()
            } else {
                Log.w("LOGIN_GOOGLE_FIREBASE", "Firebase signInWithCredential:failure", task.exception)
                onFailure("Firebase Authentication with Google failed. ${task.exception?.message ?: ""}")
            }
        }
}

@Preview(showBackground = true, device = "spec:width=360dp,height=740dp,dpi=480")
@Composable
fun LoginScreenPreview() {
    TaskAutomatorTheme {
        LoginScreen(
            onLoginClicked = { mail, pass ->
                println("Preview Email Login: $mail, $pass")
            },
            onNavigateToSignup = {
                println("Preview Navigate to Signup Clicked")
            },
            onGoogleSignInSuccess = {
                println("Preview Google Sign In Success")
            },
            onGoogleSignInFailed = { errorMsg ->
                println("Preview Google Sign In Failed: $errorMsg")
            }
        )
    }
}