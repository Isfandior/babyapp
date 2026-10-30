package app.birthbaby.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * A calm night-light, not a hospital dashboard. Teal for everyday actions,
 * amber only for "time to call", red only for "act now". Follows the phone's dark mode.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F7F7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBE8),
    onPrimaryContainer = Color(0xFF0E3633),
    secondary = Color(0xFF6B5B7B),
    onSecondary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF7F4),
    onBackground = Color(0xFF1F1B24),
    surface = Color(0xFFFBF7F4),
    onSurface = Color(0xFF1F1B24),
    surfaceVariant = Color(0xFFF1EBE6),
    onSurfaceVariant = Color(0xFF4A4550),
    outline = Color(0xFF7B7480),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FB3AC),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF1F4A47),
    onPrimaryContainer = Color(0xFFD3EBE8),
    secondary = Color(0xFFCDBEDD),
    onSecondary = Color(0xFF2A2036),
    error = Color(0xFFE5534B),
    onError = Color.White,
    errorContainer = Color(0xFF5C1814),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1B1726),
    onBackground = Color(0xFFEDE7F0),
    surface = Color(0xFF1B1726),
    onSurface = Color(0xFFEDE7F0),
    surfaceVariant = Color(0xFF2A2538),
    onSurfaceVariant = Color(0xFFCAC3D0),
    outline = Color(0xFF948E9A),
)

/** Card colours for each kind of message. */
@Immutable
data class Tone(val container: Color, val content: Color)

@Immutable
data class Tones(val neutral: Tone, val ok: Tone, val warning: Tone, val urgent: Tone, val amber: Color)

private val LightTones = Tones(
    neutral = Tone(LightColors.surfaceVariant, LightColors.onSurface),
    ok = Tone(LightColors.primaryContainer, LightColors.onPrimaryContainer),
    warning = Tone(Color(0xFFFFE8A8), Color(0xFF3D2E00)),
    urgent = Tone(LightColors.errorContainer, LightColors.onErrorContainer),
    amber = Color(0xFFF2A900),
)

private val DarkTones = Tones(
    neutral = Tone(DarkColors.surfaceVariant, DarkColors.onSurface),
    ok = Tone(DarkColors.primaryContainer, DarkColors.onPrimaryContainer),
    warning = Tone(Color(0xFF4A3A00), Color(0xFFFFE8A8)),
    urgent = Tone(DarkColors.errorContainer, DarkColors.onErrorContainer),
    amber = Color(0xFFF2A900),
)

val LocalTones = staticCompositionLocalOf { LightTones }

val tones: Tones @Composable get() = LocalTones.current

/** One type scale for the whole app, large enough to read mid-contraction. */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun BirthBabyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalTones provides if (dark) DarkTones else LightTones) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
