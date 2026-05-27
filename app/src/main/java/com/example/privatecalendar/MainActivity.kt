package com.example.privatecalendar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.privatecalendar.data.SettingsManager
import com.example.privatecalendar.ui.CalendarScreen
import com.example.privatecalendar.ui.CalendarViewModel
import com.example.privatecalendar.ui.SettingsScreen
import com.example.privatecalendar.ui.tasks.QuickTasksScreen
import com.example.privatecalendar.ui.theme.PrivateCalendarTheme
import kotlinx.coroutines.flow.first
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val CHANNEL_ID = "events_channel"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("androidx.datastore.preferences.settings", MODE_PRIVATE)
        val language = prefs.getString("language_code", "es") ?: "es"
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            val viewModel: CalendarViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkMode.collectAsState()
            val themeName by viewModel.themeName.collectAsState()
            
            val appTheme = try {
                com.example.privatecalendar.ui.theme.AppTheme.valueOf(themeName)
            } catch (_: Exception) {
                com.example.privatecalendar.ui.theme.AppTheme.DEFAULT
            }
            
            var isAuthenticated by remember { mutableStateOf(value = false) }
            var isAuthChecked by remember { mutableStateOf(value = false) }

            val context = LocalContext.current
            val authTitle = stringResource(R.string.secure_access)
            val authSubtitle = stringResource(R.string.auth_subtitle)
            val authErrorMsg = stringResource(R.string.auth_required)

            LaunchedEffect(Unit) {
                val settingsManager = SettingsManager(context)
                val biometricEnabled = settingsManager.isBiometricEnabled.first()
                
                if (biometricEnabled) {
                    val biometricManager = BiometricManager.from(context)
                    val canAuthenticate = biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )

                    if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                        showBiometricPrompt(
                            title = authTitle,
                            subtitle = authSubtitle,
                            onSuccess = { 
                                isAuthenticated = true
                                isAuthChecked = true
                                checkAndRequestNotifications()
                            }
                        ) { 
                            Toast.makeText(context, authErrorMsg, Toast.LENGTH_SHORT).show()
                            isAuthChecked = true
                            finish() 
                        }
                    } else {
                        // Si está activado pero no hay hardware o no está configurado, dejamos entrar
                        isAuthenticated = true
                        isAuthChecked = true
                        checkAndRequestNotifications()
                    }
                } else {
                    isAuthenticated = true
                    isAuthChecked = true
                    checkAndRequestNotifications()
                }
            }

            PrivateCalendarTheme(darkTheme = isDarkTheme, theme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = isAuthenticated to isAuthChecked,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "RootTransition"
                    ) { (auth, checked) ->
                        when {
                            auth -> {
                                val navController = rememberNavController()
                                NavHost(
                                    navController = navController, 
                                    startDestination = "calendar",
                                    enterTransition = {
                                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) + slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                                        )
                                    },
                                    exitTransition = {
                                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                                        )
                                    },
                                    popEnterTransition = {
                                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) + slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                                        )
                                    },
                                    popExitTransition = {
                                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                                        )
                                    }
                                ) {
                                    composable("calendar") {
                                        CalendarScreen(
                                            viewModel = viewModel,
                                            onNavigateToSettings = { navController.navigate("settings") },
                                            onNavigateToTasks = { navController.navigate("quick_tasks") },
                                        ) { date -> 
                                            navController.navigate("day_view/$date") 
                                        }
                                    }
                                    composable("day_view/{date}") { backStackEntry ->
                                        val dateString = backStackEntry.arguments?.getString("date")
                                        val date = try {
                                            java.time.LocalDate.parse(dateString)
                                        } catch (_: Exception) {
                                            java.time.LocalDate.now()
                                        }
                                        com.example.privatecalendar.ui.DayViewScreen(
                                            date = date,
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("quick_tasks") {
                                        QuickTasksScreen(
                                            onBack = { navController.popBackStack() },
                                            taskDao = viewModel.taskDao
                                        )
                                    }
                                    composable("settings") {
                                        SettingsScreen(
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() },
                                            onNavigateToCountrySelector = { navController.navigate("holiday_country_selector") },
                                            onRecreate = { recreate() }
                                        )
                                    }
                                    composable("holiday_country_selector") {
                                        com.example.privatecalendar.ui.HolidayCountrySelectorScreen(
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }
                            }
                            !checked -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.auth_required))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { 
                                                isAuthChecked = false
                                                // Esto disparará el LaunchedEffect de nuevo
                                            }
                                        ) {
                                            Text(stringResource(android.R.string.ok)) // O un recurso de reintento si existe
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showBiometricPrompt(title: String, subtitle: String, onSuccess: () -> Unit, onError: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError()
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
