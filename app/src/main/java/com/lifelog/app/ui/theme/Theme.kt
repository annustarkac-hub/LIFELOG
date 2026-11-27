package com.lifelog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifelog.app.data.ThemeMode

/**
 * Provenance palette — the same colour always means the same data origin, in every chart and chip:
 *  recorded  = LIFELOG-recorded since tracking began
 *  historical = returned by Android for periods before tracking began (events)
 *  summary   = Android aggregate summary (no session detail)
 *  gap       = data unavailable / coverage gap (drawn hatched, never as zero bar)
 */
@Immutable
data class ProvenanceColors(val recorded: Color, val historical: Color, val summary: Color, val gap: Color, val partial: Color, val screen: Color)

private val DarkProvenance = ProvenanceColors(Color(0xFF2DD4BF), Color(0xFF818CF8), Color(0xFFA5B4FC), Color(0xFF475569), Color(0xFFFBBF24), Color(0xFF38BDF8))

val LocalProvenance = staticCompositionLocalOf { DarkProvenance }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF818CF8), onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81), onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFFBBF24), onTertiary = Color(0xFF422006),
    background = Color(0xFF0B0E14), onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0B0E14), onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1B2130), onSurfaceVariant = Color(0xFFA9B4C7),
    surfaceContainer = Color(0xFF131824), surfaceContainerHigh = Color(0xFF1B2130),
    surfaceContainerLow = Color(0xFF10141D), surfaceContainerHighest = Color(0xFF232A3B),
    outline = Color(0xFF3A4458), outlineVariant = Color(0xFF262E3F),
    error = Color(0xFFF87171), onError = Color(0xFF450A0A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF4F46E5), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC), onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0), onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF1F5F9),
    outline = Color(0xFFCBD5E1), error = Color(0xFFB91C1C),
)

private val LightProvenance = ProvenanceColors(Color(0xFF0D9488), Color(0xFF4F46E5), Color(0xFF818CF8), Color(0xFF94A3B8), Color(0xFFB45309), Color(0xFF0284C7))

private val Typo = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.6.sp),
    )
}

/** Tabular numerals for metric values so digits don't jitter between refreshes. */
val MetricStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, fontFeatureSettings = "tnum")

@Composable
fun LifelogTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalProvenance provides if (dark) DarkProvenance else LightProvenance) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Typo, content = content)
    }
}
