package ru.spgsroot.vibeplayer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.security.AuthManager
import ru.spgsroot.vibeplayer.locale.LocaleManager
import ru.spgsroot.vibeplayer.ui.auth.AuthScreen
import ru.spgsroot.vibeplayer.security.AuthSession
import ru.spgsroot.vibeplayer.ui.gallery.GalleryScreen
import ru.spgsroot.vibeplayer.ui.manga.MangaLibraryScreen
import ru.spgsroot.vibeplayer.ui.manga.MangaReaderScreen
import ru.spgsroot.vibeplayer.ui.gallery.GalleryViewModel
import ru.spgsroot.vibeplayer.ui.onboarding.OnboardingScreen
import ru.spgsroot.vibeplayer.ui.player.PlayerScreen
import ru.spgsroot.vibeplayer.ui.player.PlayerViewModel
import ru.spgsroot.vibeplayer.ui.theme.VibePlayerTheme
import ru.spgsroot.vibeplayer.ui.share.PendingImport
import ru.spgsroot.vibeplayer.ui.share.ShareUriPolicy
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var authSession: AuthSession

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
        acceptSharedUri(intent)
        enableEdgeToEdge()

        setContent {
            VibePlayerTheme {
                AppNavigation(authManager, authSession)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedUri(intent)
    }

    override fun onStop() {
        if (::authSession.isInitialized) {
            authSession.lock()
        }
        super.onStop()
    }

    private fun acceptSharedUri(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && ShareUriPolicy.isImportable(uri.scheme)) {
            PendingImport.uri = uri
        }
    }
}

@Composable
fun AppNavigation(authManager: AuthManager, authSession: AuthSession) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val unlocked by authSession.unlocked.collectAsState()

    LaunchedEffect(Unit) {
        startDestination = when {
            !authManager.isOnboardingCompleted() -> "onboarding"
            authManager.isPasswordSet() -> "auth"
            else -> "player"
        }
    }


    LaunchedEffect(unlocked, startDestination) {
        val destination = startDestination ?: return@LaunchedEffect
        if (destination == "onboarding") return@LaunchedEffect
        val needsAuth = authManager.isPasswordSet() && !unlocked
        val current = navController.currentDestination?.route
        if (needsAuth && current != null && current != "auth") {
            navController.navigate("auth") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    startDestination?.let { start ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
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
                            authSession.unlock()
                            navController.navigate("player") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable("auth") {
                var status by remember { mutableStateOf<String?>(null) }
                var inputEnabled by remember { mutableStateOf(true) }
                var resetRequired by remember { mutableStateOf(false) }
                val lockedTemplate = stringResource(R.string.auth_locked_seconds)
                val resetText = stringResource(R.string.auth_reset_required)
                LaunchedEffect(Unit) {
                    resetRequired = authManager.isResetRequired()
                    while (true) {
                        val remaining = authManager.lockRemainingMs()
                        val seconds = ((remaining + 999) / 1000).toInt()
                        inputEnabled = remaining == 0L
                        status = when {
                            resetRequired -> resetText
                            remaining > 0L -> lockedTemplate.format(seconds)
                            else -> null
                        }
                        delay(500)
                    }
                }
                AuthScreen(
                    onVerifyPassword = { password ->
                        if (resetRequired) {
                            authManager.setPassword(password)
                            resetRequired = false
                            authSession.unlock()
                            true
                        } else {
                            val accepted = authManager.verifyPassword(password)
                            resetRequired = authManager.isResetRequired()
                            if (accepted) authSession.unlock()
                            accepted
                        }
                    },
                    onAuthenticated = {
                        navController.navigate("player") {
                            popUpTo("auth") { inclusive = true }
                        }
                    },
                    statusMessage = status,
                    inputEnabled = inputEnabled
                )
            }
            composable("player") {
                val playerViewModel: PlayerViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    PendingImport.consume()?.let { playerViewModel.importVideoFromUri(it) }
                }
                PlayerScreen(navController = navController, viewModel = playerViewModel)
            }
            composable("gallery") {
                val galleryViewModel: GalleryViewModel = hiltViewModel()
                GalleryScreen(navController = navController, viewModel = galleryViewModel)
            }
            composable("manga") {
                MangaLibraryScreen(navController = navController)
            }
            composable(
                "manga/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                MangaReaderScreen(
                    galleryId = entry.arguments?.getInt("id") ?: return@composable,
                    navController = navController
                )
            }
        }
    }
}
