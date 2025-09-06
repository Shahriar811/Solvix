package com.example.task_automator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent

@Composable
fun HomeScreen(
    name: String,
    onChatClicked: () -> Unit,
    onSensorClicked: () -> Unit,
    onLocationClicked: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Welcome, $name!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onChatClicked) {
            Text("Chat with Assistant")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onSensorClicked) {
            Text("Test Sensors")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onLocationClicked) {
            Text("Location")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                FirebaseAuth.getInstance().signOut()
                // Navigate to MainActivity (assumed to host LoginScreen)
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            }
        ) {
            Text("Sign Out")
        }
    }
}