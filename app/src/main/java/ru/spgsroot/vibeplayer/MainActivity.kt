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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.locale.LocaleManager
import ru.spgsroot.vibeplayer.security.AuthManager
import ru.spgsroot.vibeplayer.ui.auth.AuthScreen
import ru.spgsroot.vibeplayer.ui.gallery.GalleryScreen
import ru.spgsroot.vibeplayer.ui.player.PlayerScreen
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

    LaunchedEffect(Unit) {
        scope.launch {
            startDestination = if (authManager.isPasswordSet()) "auth" else "player"
        }
    }

    startDestination?.let { start ->
        NavHost(navController = navController, startDestination = start) {
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
                PlayerScreen(navController = navController)
            }
            composable("gallery") {
                GalleryScreen(navController = navController)
            }
        }
    }
}