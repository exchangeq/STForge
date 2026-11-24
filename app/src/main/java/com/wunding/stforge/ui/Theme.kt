package com.wunding.stforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0B111B)
private val Panel = Color(0xFF141D2B)
private val Amber = Color(0xFFE3A857)
private val Paper = Color(0xFFF3EEE2)
private val Slate = Color(0xFF8FA0B5)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B05),
    secondary = Color(0xFF7FD1C0),
    onSecondary = Color(0xFF04211C),
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = Color(0xFF1E2A3A),
    onSurfaceVariant = Slate,
    outline = Color(0xFF35465C),
    error = Color(0xFFE07A6A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A18),
    secondary = Color(0xFF17695C),
    background = Color(0xFFF7F2E7),
    surface = Color(0xFFFFFBF2),
)

@Composable
fun StForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Dark, content = content)
}
