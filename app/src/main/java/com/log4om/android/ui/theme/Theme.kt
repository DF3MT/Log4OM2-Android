package com.log4om.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary          = Green80,
    onPrimary        = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary        = Amber80,
    onSecondary      = Amber20,
    secondaryContainer = Amber40,
    onSecondaryContainer = Amber90,
    error            = Red80,
    onError          = Red10,
    errorContainer   = Red40,
    onErrorContainer = Red90,
    background       = Grey10,
    onBackground     = Grey90,
    surface          = Grey10,
    onSurface        = Grey90,
    surfaceVariant   = Grey20,
    onSurfaceVariant = Grey90
)

private val LightColorScheme = lightColorScheme(
    primary          = Green40,
    onPrimary        = Grey99,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary        = Amber40,
    onSecondary      = Grey99,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber10,
    error            = Red40,
    onError          = Grey99,
    errorContainer   = Red90,
    onErrorContainer = Red10,
    background       = Grey99,
    onBackground     = Grey10,
    surface          = Grey95,
    onSurface        = Grey10,
    surfaceVariant   = Grey90,
    onSurfaceVariant = Grey20
)

@Composable
fun Log4OMTheme(
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
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
