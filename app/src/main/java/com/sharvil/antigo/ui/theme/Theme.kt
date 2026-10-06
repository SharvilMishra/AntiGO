package com.sharvil.antigo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sharvil.antigo.domain.model.ThemeChoice

private val Yellow = Color(0xFFFFD60A)
private val Ink = Color(0xFF1A1B22)
private val Canvas = Color(0xFFFBF8FF)
private val InputSurface = Color(0xFFF0EDF8)
private val MutedInk = Color(0xFF4D4632)
private val DarkCanvas = Color(0xFF111116)
private val DarkSurface = Color(0xFF1D1D24)
private val DarkMuted = Color(0xFFBDB8C8)

private val Light = lightColorScheme(
    primary = Color(0xFF735C00), onPrimary = Color.White,
    primaryContainer = Yellow, onPrimaryContainer = Color(0xFF3D3100),
    background = Canvas, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = InputSurface, onSurfaceVariant = MutedInk,
    outline = Color(0xFF7F7660), outlineVariant = Color(0xFFD8D2E1)
)

private val Dark = darkColorScheme(
    primary = Yellow, onPrimary = Color(0xFF241E00),
    primaryContainer = Color(0xFF514400), onPrimaryContainer = Color(0xFFFFE277),
    background = DarkCanvas, onBackground = Color(0xFFF2F0F7),
    surface = DarkSurface, onSurface = Color(0xFFF2F0F7),
    surfaceVariant = Color(0xFF292832), onSurfaceVariant = DarkMuted,
    outline = Color(0xFF8D8996), outlineVariant = Color(0xFF46434D)
)

@Composable
fun AppTheme(theme: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
