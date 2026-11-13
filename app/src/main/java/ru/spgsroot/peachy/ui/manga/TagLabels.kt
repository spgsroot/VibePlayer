package ru.spgsroot.peachy.ui.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The language tags are shown in: the app language, including the one picked in Settings, because the app
 * applies it to the configuration. Reading it here makes a language change redraw every tag.
 */
@Composable
internal fun tagLanguage(): String = LocalConfiguration.current.locales[0].language
