package th.ac.kmutnb.prachin.map.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3EBCB),
    onPrimaryContainer = ForestDeep,
    secondary = ForestLeaf,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EFC9),
    onSecondaryContainer = Color(0xFF1C3A12),
    tertiary = HornbillAmber,
    onTertiary = Color(0xFF2E1F00),
    tertiaryContainer = Color(0xFFFFE6B3),
    onTertiaryContainer = Color(0xFF3D2900),
    error = ErrorRed,
    background = ForestCream,
    onBackground = Color(0xFF17201A),
    surface = ForestCream,
    onSurface = Color(0xFF17201A),
    surfaceVariant = Color(0xFFDCE7D9),
    onSurfaceVariant = Color(0xFF414A42),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5EB),
    surfaceContainer = ForestMist,
    surfaceContainerHigh = Color(0xFFDDEBDA),
    outline = Color(0xFF717A71),
    outlineVariant = Color(0xFFC1CCBF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD7A6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0F5232),
    onPrimaryContainer = Color(0xFFC3EBCB),
    secondary = Color(0xFFA9D48F),
    onSecondary = Color(0xFF173807),
    secondaryContainer = Color(0xFF2D4F1E),
    onSecondaryContainer = Color(0xFFD9EFC9),
    tertiary = HornbillAmberLight,
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFE6B3),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0F1611),
    onBackground = Color(0xFFDDE5DA),
    surface = Color(0xFF0F1611),
    onSurface = Color(0xFFDDE5DA),
    surfaceVariant = Color(0xFF414A42),
    onSurfaceVariant = Color(0xFFC1CCBF),
    surfaceContainerLowest = Color(0xFF0A110C),
    surfaceContainerLow = Color(0xFF172019),
    surfaceContainer = Color(0xFF1B241D),
    surfaceContainerHigh = Color(0xFF252F27),
    outline = Color(0xFF8B948A),
    outlineVariant = Color(0xFF414A42),
)

@Composable
fun KhaoYaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/**
 * The one top bar colour for every screen, old map screens and new Khao Yai screens alike:
 * a light canopy green. Light rather than full green so the text buttons some screens put
 * in their bar (in the primary colour) stay readable on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun forestTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
