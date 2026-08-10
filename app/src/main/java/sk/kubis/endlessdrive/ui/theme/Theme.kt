package sk.kubis.endlessdrive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

private val Dust = Color(0xFFC4A35A)
private val Road = Color(0xFF3A342C)
private val Sky = Color(0xFF7A8FA0)
private val Night = Color(0xFF1A1612)
private val Accent = Color(0xFFB85C38)
private val Sage = Color(0xFF6B7F5A)

private val DarkColors = darkColorScheme(
    primary = Dust,
    onPrimary = Night,
    secondary = Sage,
    onSecondary = Night,
    tertiary = Accent,
    background = Night,
    onBackground = Color(0xFFE8DFD0),
    surface = Color(0xFF242018),
    onSurface = Color(0xFFE8DFD0),
    surfaceVariant = Road,
    onSurfaceVariant = Color(0xFFC8BFAE)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B5344),
    onPrimary = Color.White,
    secondary = Sage,
    tertiary = Accent,
    background = Color(0xFFEDE4D4),
    onBackground = Night,
    surface = Color(0xFFF5EFE3),
    onSurface = Night
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = 1.5.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun EndlessDriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
