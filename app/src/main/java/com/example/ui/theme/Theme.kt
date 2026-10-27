package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = CyanOnPrimaryDark,
    primaryContainer = CyanPrimaryContainerDark,
    onPrimaryContainer = CyanOnPrimaryContainerDark,
    secondary = VioletSecondaryDark,
    onSecondary = VioletOnSecondaryDark,
    secondaryContainer = VioletSecondaryContainerDark,
    onSecondaryContainer = VioletOnSecondaryContainerDark,
    tertiary = EmeraldTertiaryDark,
    onTertiary = EmeraldOnTertiaryDark,
    tertiaryContainer = EmeraldTertiaryContainerDark,
    onTertiaryContainer = EmeraldOnTertiaryContainerDark,
    background = ObsidianBackgroundDark,
    onBackground = SlateOnSurfaceDark,
    surface = ObsidianSurfaceDark,
    onSurface = SlateOnSurfaceDark,
    surfaceVariant = ObsidianSurfaceVariantDark,
    onSurfaceVariant = SlateOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = CyanOnPrimaryLight,
    primaryContainer = CyanPrimaryContainerLight,
    onPrimaryContainer = CyanOnPrimaryContainerLight,
    secondary = VioletSecondaryLight,
    onSecondary = VioletOnSecondaryLight,
    secondaryContainer = VioletSecondaryContainerLight,
    onSecondaryContainer = VioletOnSecondaryContainerLight,
    tertiary = EmeraldTertiaryLight,
    onTertiary = EmeraldOnTertiaryLight,
    tertiaryContainer = EmeraldTertiaryContainerLight,
    onTertiaryContainer = EmeraldOnTertiaryContainerLight,
    background = DaylightBackgroundLight,
    onBackground = SlateOnSurfaceLight,
    surface = DaylightSurfaceLight,
    onSurface = SlateOnSurfaceLight,
    surfaceVariant = DaylightSurfaceVariantLight,
    onSurfaceVariant = SlateOnSurfaceVariantLight
)

val ClonexaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClonexaProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ClonexaShapes,
        content = content
    )
}
