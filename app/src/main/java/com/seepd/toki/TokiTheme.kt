package com.seepd.toki

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E2),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF9C423D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD7),
    onSecondaryContainer = Color(0xFF3F0303),
    tertiary = Color(0xFF48617D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1E4FF),
    onTertiaryContainer = Color(0xFF001D35),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C6),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF9EF2E2),
    secondary = Color(0xFFFFB3AD),
    onSecondary = Color(0xFF5F1412),
    secondaryContainer = Color(0xFF7D2B28),
    onSecondaryContainer = Color(0xFFFFDAD7),
    tertiary = Color(0xFFAFC9E9),
    onTertiary = Color(0xFF17324C),
    tertiaryContainer = Color(0xFF304963),
    onTertiaryContainer = Color(0xFFD1E4FF),
    background = Color(0xFF101412),
    onBackground = Color(0xFFDFE3E0),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFDFE3E0),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
)

private val TokiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
internal fun TokiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = TokiShapes,
        content = content,
    )
}
