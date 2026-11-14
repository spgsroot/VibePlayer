package ru.spgsroot.peachy.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
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
        typography = Typography
    ) {
        // Material 3 leaves the default content colour black; anything drawn on a
        // custom-coloured surface (for example one with alpha) would be dark on dark.
        CompositionLocalProvider(
            LocalContentColor provides DarkColorScheme.onBackground,
            content = content
        )
    }
}
