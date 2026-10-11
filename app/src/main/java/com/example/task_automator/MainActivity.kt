package com.example.task_automator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
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
                                onSignupClicked = { username, email, password, onFinished ->
                                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(this@MainActivity) { task ->
                                            if (task.isSuccessful) {
                                                val user = firebaseAuth.currentUser
                                                val completeSignup: (Boolean) -> Unit = { profileSaved ->
                                                    onFinished()
                                                    guestPreferences.edit().putBoolean("active", false).apply()
                                                    isGuest = false
                                                    navController.navigate(AppDestinations.HOME_ROUTE) {
                                                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                    if (!profileSaved) scope.launch {
                                                        snackbarHostState.showSnackbar("Your account was created, but your display name could not be saved.")
                                                    }
                                                }
                                                user?.updateProfile(
                                                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                        .setDisplayName(username)
                                                        .build()
                                                )?.addOnCompleteListener { profileTask ->
                                                    completeSignup(profileTask.isSuccessful)
                                                }
                                                if (user == null) {
                                                    onFinished()
                                                    scope.launch { snackbarHostState.showSnackbar("Account created. Please sign in to continue.") }
                                                    navController.popBackStack(AppDestinations.LOGIN_ROUTE, false)
                                                }
                                            } else {
                                                onFinished()
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

}
