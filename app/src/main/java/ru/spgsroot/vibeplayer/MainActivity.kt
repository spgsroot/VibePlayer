package ru.spgsroot.vibeplayer

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.locale.LocaleManager
import ru.spgsroot.vibeplayer.security.AuthManager
import ru.spgsroot.vibeplayer.ui.auth.AuthScreen
import ru.spgsroot.vibeplayer.ui.gallery.GalleryScreen
import ru.spgsroot.vibeplayer.ui.gallery.GalleryViewModel
import ru.spgsroot.vibeplayer.ui.onboarding.OnboardingScreen
import ru.spgsroot.vibeplayer.ui.player.PlayerScreen
import ru.spgsroot.vibeplayer.ui.player.PlayerViewModel
import ru.spgsroot.vibeplayer.ui.theme.VibePlayerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getCurrentLocale()
        val locale = LocaleManager.getLocale(languageCode)
        val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            newBase.createConfigurationContext(
                newBase.resources.configuration.apply {
                    setLocales(android.os.LocaleList(locale))
                }
            )
        } else {
            newBase
        }
        super.attachBaseContext(context)
        LocaleManager.initialize(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        
        setContent {
            VibePlayerTheme {
                AppNavigation(authManager)
            }
        }
    }
}

@Composable
fun AppNavigation(authManager: AuthManager) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 💡 Preload (подгружаем данные в фоне до открытия экранов)
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val galleryViewModel: GalleryViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        scope.launch {
            if (!authManager.isOnboardingCompleted()) {
                startDestination = "onboarding"
            } else {
                startDestination = if (authManager.isPasswordSet()) "auth" else "player"
            }
        }
    }

    startDestination?.let { start ->
        NavHost(navController = navController, startDestination = start) {
            composable("onboarding") {
                OnboardingScreen(
                    onComplete = {
                        scope.launch {
                            authManager.skipOnboarding()
                            navController.navigate("player") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    },
                    onPasswordSet = { password ->
                        scope.launch {
                            authManager.setPassword(password)
                            navController.navigate("player") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable("auth") {
                AuthScreen(
                    onVerifyPassword = { password ->
                        authManager.verifyPassword(password)
                    },
                    onAuthenticated = {
                        navController.navigate("player") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }
            composable("player") {
                // Передаем предварительно загруженные viewmodels
                PlayerScreen(navController = navController, viewModel = playerViewModel)
            }
            composable("gallery") {
                // Передаем предварительно загруженные viewmodels
                GalleryScreen(navController = navController, viewModel = galleryViewModel)
            }
        }
    }
}
