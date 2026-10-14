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

private val DarkColorScheme = darkColorScheme(
    primary = CloudPrimaryDark,
    onPrimary = CloudOnPrimaryDark,
    primaryContainer = CloudPrimaryContainerDark,
    onPrimaryContainer = CloudOnPrimaryContainerDark,
    secondary = CloudSecondaryDark,
    onSecondary = CloudOnSecondaryDark,
    secondaryContainer = CloudSecondaryContainerDark,
    onSecondaryContainer = CloudOnSecondaryContainerDark,
    tertiary = CloudTertiaryDark,
    onTertiary = CloudOnTertiaryDark,
    tertiaryContainer = CloudTertiaryContainerDark,
    onTertiaryContainer = CloudOnTertiaryContainerDark,
    background = CloudBackgroundDark,
    onBackground = CloudOnBackgroundDark,
    surface = CloudSurfaceDark,
    onSurface = CloudOnSurfaceDark,
    surfaceVariant = CloudSurfaceVariantDark,
    onSurfaceVariant = CloudOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = CloudPrimary,
    onPrimary = CloudOnPrimary,
    primaryContainer = CloudPrimaryContainer,
    onPrimaryContainer = CloudOnPrimaryContainer,
    secondary = CloudSecondary,
    onSecondary = CloudOnSecondary,
    secondaryContainer = CloudSecondaryContainer,
    onSecondaryContainer = CloudOnSecondaryContainer,
    tertiary = CloudTertiary,
    onTertiary = CloudOnTertiary,
    tertiaryContainer = CloudTertiaryContainer,
    onTertiaryContainer = CloudOnTertiaryContainer,
    background = CloudBackground,
    onBackground = CloudOnBackground,
    surface = CloudSurface,
    onSurface = CloudOnSurface,
    surfaceVariant = CloudSurfaceVariant,
    onSurfaceVariant = CloudOnSurfaceVariant
)

@Composable
fun UnifiedCloudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
