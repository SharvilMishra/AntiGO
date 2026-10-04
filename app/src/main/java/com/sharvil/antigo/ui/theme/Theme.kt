package com.sharvil.antigo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sharvil.antigo.domain.model.ThemeChoice

private val Yellow = Color(0xFFFFD60A)
private val Black = Color(0xFF000000)
private val Ink = Color(0xFF0D0D0D)
private val SurfaceDark = Color(0xFF1A1A1A)
private val White = Color(0xFFFFFFFF)
private val Paper = Color(0xFFF7F7F5)
private val Grey = Color(0xFF8A8A8A)

private val Light = lightColorScheme(primary = Yellow, onPrimary = Black, background = Paper, onBackground = Black, surface = White, onSurface = Black, surfaceVariant = Paper, onSurfaceVariant = Grey)
private val Dark = darkColorScheme(primary = Yellow, onPrimary = Black, background = Ink, onBackground = White, surface = SurfaceDark, onSurface = White, surfaceVariant = SurfaceDark, onSurfaceVariant = Grey)

@Composable fun AppTheme(theme: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (theme) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
