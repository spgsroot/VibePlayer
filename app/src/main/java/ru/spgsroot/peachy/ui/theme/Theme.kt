package ru.spgsroot.peachy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = InkText,
    primaryContainer = AmberContainer,
    onPrimaryContainer = Foam,
    secondary = Mist,
    onSecondary = Ink,
    background = Ink,
    onBackground = Foam,
    surface = InkSurface,
    onSurface = Foam,
    surfaceContainer = InkElevated,
    surfaceContainerHigh = InkElevated,
    outline = Mist
)

@Composable
fun PeachyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
