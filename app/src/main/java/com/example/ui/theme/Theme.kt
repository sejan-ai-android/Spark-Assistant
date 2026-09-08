package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SparkDarkColorScheme =
  darkColorScheme(
    primary = SparkCyan,
    onPrimary = ObsidianBg,
    primaryContainer = SparkPurpleDark,
    onPrimaryContainer = SparkCyan,
    secondary = SparkPurple,
    onSecondary = ObsidianBg,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = SparkCyan,
    tertiary = SparkAmber,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = SparkDarkColorScheme,
    typography = Typography,
    content = content
  )
}
