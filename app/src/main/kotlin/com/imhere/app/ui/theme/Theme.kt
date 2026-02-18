package com.imhere.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Mint,
    secondary = Ember,
    tertiary = SoftGreen,
    background = Paper,
    surface = Clay,
    onPrimary = Paper,
    onSecondary = Paper,
    onBackground = Ink,
    onSurface = Ink
)

private val DarkColors = darkColorScheme(
    primary = SoftGreen,
    secondary = Ember,
    tertiary = Mint,
    background = Ink,
    surface = Slate,
    onPrimary = Ink,
    onSecondary = Paper,
    onBackground = Paper,
    onSurface = Paper
)

@Composable
fun ImHereTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = ImHereTypography,
        content = content
    )
}
