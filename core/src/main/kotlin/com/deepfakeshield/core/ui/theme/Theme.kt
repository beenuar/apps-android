package com.deepfakeshield.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = ShieldBlue,
    onPrimary = Color.White,
    primaryContainer = ShieldBlueLight,
    onPrimaryContainer = ShieldBlueDark,
    
    secondary = SafeGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF1B5E20),
    
    tertiary = WarningYellow,
    onTertiary = Gray900,
    tertiaryContainer = Color(0xFFFFF9C4),
    onTertiaryContainer = Color(0xFFF57F17),
    
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),
    
    background = BackgroundLight,
    onBackground = Gray900,
    surface = SurfaceLight,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    
    outline = Gray400,
    outlineVariant = Gray200
)

private val DarkColorScheme = darkColorScheme(
    primary = ShieldBlueLight,
    onPrimary = ShieldBlueDark,
    primaryContainer = ShieldBlueDark,
    onPrimaryContainer = ShieldBlueLight,
    
    secondary = SafeGreen,
    onSecondary = Color(0xFF003300),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFA5D6A7),
    
    tertiary = WarningYellow,
    onTertiary = Gray900,
    tertiaryContainer = Color(0xFFF57F17),
    onTertiaryContainer = Color(0xFFFFF9C4),
    
    error = Color(0xFFEF5350),
    onError = Color(0xFF5D0000),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color(0xFFFFCDD2),
    
    background = BackgroundDark,
    onBackground = Gray100,
    surface = SurfaceDark,
    onSurface = Gray100,
    surfaceVariant = DarkGray200,
    onSurfaceVariant = Gray400,
    
    outline = DarkGray400,
    outlineVariant = DarkGray200
)

// AMOLED Dark Color Scheme - true black for OLED screens
private val AmoledDarkColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212)
)

@Composable
fun DeepfakeShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Dynamic color enabled by default on Android 12+
    amoledMode: Boolean = false,
    simpleMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            when {
                darkTheme && amoledMode -> dynamicDarkColorScheme(context).copy(
                    background = Color.Black,
                    surface = Color.Black
                )
                darkTheme -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
        }
        darkTheme && amoledMode -> AmoledDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val typography = if (simpleMode) SimpleTypography else Typography
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
