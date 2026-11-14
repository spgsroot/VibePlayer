package ru.spgsroot.peachy

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.navigation.compose.currentBackStackEntryAsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.security.AuthManager
import ru.spgsroot.peachy.locale.LocaleManager
import ru.spgsroot.peachy.ui.auth.AuthScreen
import ru.spgsroot.peachy.security.AuthSession
import ru.spgsroot.peachy.ui.gallery.GalleryScreen
import ru.spgsroot.peachy.ui.manga.GalleryDetailScreen
import ru.spgsroot.peachy.ui.manga.MangaHubScreen
import ru.spgsroot.peachy.ui.manga.MangaReaderScreen
import ru.spgsroot.peachy.ui.manga.TagBrowserScreen
import ru.spgsroot.peachy.ui.manga.searchCatalog
import ru.spgsroot.peachy.ui.gallery.GalleryViewModel
import ru.spgsroot.peachy.ui.onboarding.OnboardingScreen
import ru.spgsroot.peachy.ui.player.PlayerScreen
import ru.spgsroot.peachy.ui.player.PlayerViewModel
import ru.spgsroot.peachy.ui.theme.PeachyTheme
import ru.spgsroot.peachy.ui.share.ShareImportViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var authSession: AuthSession

    private val shareImportViewModel: ShareImportViewModel by viewModels()

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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) shareImportViewModel.enqueue(intent)
        // The app is dark in every system theme: light status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            PeachyTheme {
                AppNavigation(authManager, authSession, shareImportViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareImportViewModel.enqueue(intent)
    }

    override fun onStop() {
        if (::authSession.isInitialized && !isChangingConfigurations) {
            authSession.lock()
        }
        super.onStop()
    }

}

@Composable
fun AppNavigation(authManager: AuthManager, authSession: AuthSession, shareImports: ShareImportViewModel) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val unlocked by authSession.unlocked.collectAsState()
    val hasPendingShare by shareImports.hasPending.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val canImport = route != null && route != "onboarding" && route != "auth"
    val context = androidx.compose.ui.platform.LocalContext.current

    // Import only inside the unlocked app, not in the player destination. An onNewIntent share
    // therefore works while gallery/manga is already open, and photos never enter PlayerViewModel.
    LaunchedEffect(hasPendingShare, canImport, unlocked) {
        if (hasPendingShare && canImport && (!authManager.isPasswordSet() || unlocked)) {
            shareImports.importPending()
        }
    }
    LaunchedEffect(canImport, unlocked) {
        if (!canImport || (authManager.isPasswordSet() && !unlocked)) return@LaunchedEffect
        shareImports.results.collect { result ->
            Toast.makeText(
                context,
                context.getString(R.string.share_import_result, result.imported, result.failed),
                Toast.LENGTH_LONG
            ).show()
            val destination = when {
                result.includesPhotos -> "gallery"
                result.includesVideos -> "player"
                else -> null
            }
            if (destination != null && navController.currentDestination?.route != destination) {
                navController.navigate(destination) { launchSingleTop = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        startDestination = when {
            !authManager.isOnboardingCompleted() -> "onboarding"
            authManager.isPasswordSet() && !authSession.unlocked.value -> "auth"
            else -> "player"
        }
    }


    val authGateActive = startDestination != null && route != null && route != "onboarding"
    LaunchedEffect(unlocked, authGateActive) {
        if (!authGateActive) return@LaunchedEffect
        val needsAuth = authManager.isPasswordSet() && !unlocked
        if (needsAuth && navController.currentDestination?.route != "auth") {
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
                PlayerScreen(navController = navController, viewModel = playerViewModel)
            }
            composable("gallery") {
                val galleryViewModel: GalleryViewModel = hiltViewModel()
                GalleryScreen(navController = navController, viewModel = galleryViewModel)
            }
            composable("manga") { entry ->
                MangaHubScreen(navController = navController, entry = entry)
            }
            composable(
                "manga/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                GalleryDetailScreen(
                    galleryId = entry.arguments?.getInt("id") ?: return@composable,
                    navController = navController
                )
            }
            composable(
                "manga/{id}/read?page={page}",
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { entry ->
                MangaReaderScreen(
                    galleryId = entry.arguments?.getInt("id") ?: return@composable,
                    startAt = entry.arguments?.getInt("page")?.takeIf { it >= 0 },
                    navController = navController
                )
            }
            composable(
                "mangatags/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { entry ->
                TagBrowserScreen(
                    onSearch = navController::searchCatalog,
                    onBack = { navController.popBackStack() },
                    initialType = entry.arguments?.getString("type") ?: "tag"
                )
            }
        }
    }
}
