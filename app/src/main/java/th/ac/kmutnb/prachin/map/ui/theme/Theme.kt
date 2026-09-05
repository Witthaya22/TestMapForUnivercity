package th.ac.kmutnb.prachin.map.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = KmutnbBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3F7),
    onPrimaryContainer = KmutnbBlueDark,
    secondary = KmutnbGold,
    onSecondary = Color(0xFF2A2000),
    secondaryContainer = Color(0xFFFBE7B4),
    onSecondaryContainer = Color(0xFF2A2000),
    error = ErrorRed,
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF17457F),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = KmutnbGoldLight,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4400),
    onSecondaryContainer = Color(0xFFFFE08C),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
)

@Composable
fun KmutnbMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
