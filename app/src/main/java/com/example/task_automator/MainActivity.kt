package com.example.task_automator

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import com.example.task_automator.services.NotificationListener
import com.example.task_automator.ui.theme.AppThemeMode
import com.example.task_automator.ui.theme.TaskAutomatorTheme
import com.example.task_automator.ui.theme.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val SIGNUP_ROUTE = "signup"
    const val HOME_ROUTE = "home"
    const val CHAT_ROUTE = "chat"
    const val SENSOR_ROUTE = "sensor"
    const val LOCATION_ROUTE = "location"
}

class MainActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ThemeManager.init(this)
        firebaseAuth = Firebase.auth

        // Request all necessary permissions
        requestPermissions()

        // Check and request notification access
        checkNotificationAccess()

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            TaskAutomatorTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val guestPreferences = remember { getSharedPreferences("guest_session", MODE_PRIVATE) }
                var isGuest by remember { mutableStateOf(guestPreferences.getBoolean("active", false)) }
                val startDestination = remember {
                    if (isGuest || firebaseAuth.currentUser != null) AppDestinations.HOME_ROUTE
                    else AppDestinations.LOGIN_ROUTE
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = { fadeIn(tween(260)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(260)) },
                        exitTransition = { fadeOut(tween(260)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(260)) },
                        popEnterTransition = { fadeIn(tween(260)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260)) },
                        popExitTransition = { fadeOut(tween(260)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260)) }
                    ) {
                        composable(AppDestinations.LOGIN_ROUTE) {
                            LoginScreen(
                                onLoginClicked = { email, password, onFinished ->
                                    firebaseAuth.signInWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(this@MainActivity) { task ->
                                            onFinished()
                                            if (task.isSuccessful) {
                                                guestPreferences.edit().putBoolean("active", false).apply()
                                                isGuest = false
                                                navController.navigate(AppDestinations.HOME_ROUTE) {
                                                    popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Login failed: ${task.exception?.message ?: "Unknown error"}"
                                                    )
                                                }
                                            }
                                        }
                                },
                                onNavigateToSignup = {
                                    navController.navigate(AppDestinations.SIGNUP_ROUTE)
                                },
                                onGoogleSignInSuccess = {
                                    guestPreferences.edit().putBoolean("active", false).apply()
                                    isGuest = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Google Sign-In Successful")
                                    }
                                    navController.navigate(AppDestinations.HOME_ROUTE) {
                                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                                    }
                                },
                                onGoogleSignInFailed = { message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Google Sign-In Failed: $message")
                                    }
                                },
                                onContinueAsGuest = {
                                    firebaseAuth.signOut()
                                    guestPreferences.edit().putBoolean("active", true).apply()
                                    isGuest = true
                                    navController.navigate(AppDestinations.HOME_ROUTE) {
                                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(AppDestinations.SIGNUP_ROUTE) {
                            SignupScreen(
                                onSignupClicked = { username, email, password ->
                                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(this@MainActivity) { task ->
                                            if (task.isSuccessful) {
                                                val user = firebaseAuth.currentUser
                                                user?.updateProfile(
                                                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                        .setDisplayName(username)
                                                        .build()
                                                )?.addOnCompleteListener { profileTask ->
                                                    if (profileTask.isSuccessful) {
                                                        Toast.makeText(
                                                            context,
                                                            "Signup successful. Please login.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                                                            popUpTo(AppDestinations.SIGNUP_ROUTE) { inclusive = true }
                                                        }
                                                    } else {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "Failed to set username: ${profileTask.exception?.message ?: "Unknown error"}"
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Signup failed: ${task.exception?.message ?: "Unknown error"}"
                                                    )
                                                }
                                            }
                                        }
                                },
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(AppDestinations.HOME_ROUTE) {
                            val currentUser = firebaseAuth.currentUser
                            HomeScreen(
                                name = if (isGuest) "Guest" else currentUser?.displayName ?: currentUser?.email ?: "User",
                                isGuest = isGuest,
                                onSignOut = {
                                    if (!isGuest) firebaseAuth.signOut()
                                    guestPreferences.edit().putBoolean("active", false).apply()
                                    isGuest = false
                                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onChatClicked = {
                                    navController.navigate(AppDestinations.CHAT_ROUTE)
                                },
                                onSensorClicked = {
                                    navController.navigate(AppDestinations.SENSOR_ROUTE)
                                },
                                onLocationClicked = {
                                    navController.navigate(AppDestinations.LOCATION_ROUTE)
                                }
                            )
                        }

                        composable(AppDestinations.CHAT_ROUTE) {
                            ChatScreen(
                                userName = if (isGuest) "Guest" else firebaseAuth.currentUser?.displayName ?: "You",
                                onBackClicked = { navController.popBackStack() }
                            )
                        }

                        composable(AppDestinations.SENSOR_ROUTE) {
                            SensorScreen(
                                onBackClicked = { navController.popBackStack() }
                            )
                        }

                        composable(AppDestinations.LOCATION_ROUTE) {
                            LocationScreen(
                                onBackClicked = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val deniedPermissions = permissions.filterValues { !it }.keys

            if (deniedPermissions.isNotEmpty()) {
                val permissionMessages = mutableListOf<String>()

                if (deniedPermissions.any { it.contains("STORAGE") }) {
                    permissionMessages.add("Storage access for LLM models")
                }
                if (deniedPermissions.contains(Manifest.permission.CAMERA)) {
                    permissionMessages.add("Camera access for image capture")
                }
                if (deniedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    permissionMessages.add("Fine location access")
                }
                if (deniedPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    permissionMessages.add("Coarse location access")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                    permissionMessages.add("Notification permissions")
                }

                if (permissionMessages.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "Please grant permissions for: ${permissionMessages.joinToString(", ")}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            }
        }

        // Build permission list based on API level
        val permissionsToRequest = mutableListOf<String>()

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkNotificationAccess() {
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(
                this,
                "Please enable notification access for ${getString(R.string.app_name)} in the next screen",
                Toast.LENGTH_LONG
            ).show()

            // Small delay to ensure the toast is visible before opening settings
            window.decorView.postDelayed({
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Unable to open notification settings. Please enable manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 1000)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return try {
            val componentName = ComponentName(this, NotificationListener::class.java)
            val enabledListeners = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            enabledListeners?.contains(componentName.flattenToString()) == true
        } catch (e: Exception) {
            false
        }
    }
}
