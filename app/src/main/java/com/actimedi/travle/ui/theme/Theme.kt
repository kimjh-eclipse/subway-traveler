package com.actimedi.travle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TravleColorScheme = lightColorScheme(
    primary = AmColor.Blue,
    onPrimary = AmColor.White,
    primaryContainer = AmColor.Blue100,
    onPrimaryContainer = AmColor.Navy,
    secondary = AmColor.Navy,
    onSecondary = AmColor.White,
    tertiary = AmColor.SkyBlue,
    background = AmColor.SurfacePage,
    onBackground = AmColor.Ink900,
    surface = AmColor.SurfacePage,
    onSurface = AmColor.Ink900,
    surfaceVariant = AmColor.SurfaceSunken,
    onSurfaceVariant = AmColor.Ink500,
    outline = AmColor.Line,
    outlineVariant = AmColor.Ink200,
)

private val TravleTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = SuiteFamily),
        displayMedium = displayMedium.copy(fontFamily = SuiteFamily),
        displaySmall = displaySmall.copy(fontFamily = SuiteFamily),
        headlineLarge = headlineLarge.copy(fontFamily = SuiteFamily),
        headlineMedium = headlineMedium.copy(fontFamily = SuiteFamily),
        headlineSmall = headlineSmall.copy(fontFamily = SuiteFamily),
        titleLarge = titleLarge.copy(fontFamily = SuiteFamily),
        titleMedium = titleMedium.copy(fontFamily = SuitFamily),
        titleSmall = titleSmall.copy(fontFamily = SuitFamily),
        bodyLarge = bodyLarge.copy(fontFamily = SuitFamily),
        bodyMedium = bodyMedium.copy(fontFamily = SuitFamily),
        bodySmall = bodySmall.copy(fontFamily = SuitFamily),
        labelLarge = labelLarge.copy(fontFamily = SuitFamily),
        labelMedium = labelMedium.copy(fontFamily = SuitFamily),
        labelSmall = labelSmall.copy(fontFamily = SuitFamily),
    )
}

@Composable
fun TravleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TravleColorScheme,
        typography = TravleTypography,
        content = content,
    )
}
