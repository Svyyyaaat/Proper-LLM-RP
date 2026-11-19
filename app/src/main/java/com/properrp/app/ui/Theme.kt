package com.properrp.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.R

/*
 * The game master's table at night: green baize under a lamp, the story in bone-coloured ink, brass for the one
 * thing to do next (and for the dice). By day the same table in daylight: a cool green-grey linen, felt-green ink.
 */

private val Night = darkColorScheme(
    // brass: the main action, the dice
    primary = Color(0xFFD9A84E), onPrimary = Color(0xFF2A1C02),
    primaryContainer = Color(0xFF3B3218), onPrimaryContainer = Color(0xFFF3DDA8),
    // your own lines: a lighter patch of felt
    secondary = Color(0xFFA9CFC6), onSecondary = Color(0xFF0E2A26),
    secondaryContainer = Color(0xFF29453F), onSecondaryContainer = Color(0xFFE2EEEA),
    // a roll that went your way
    tertiary = Color(0xFF8FCBA7), onTertiary = Color(0xFF0B2A18),
    tertiaryContainer = Color(0xFF1F4332), onTertiaryContainer = Color(0xFFCDEBD8),
    background = Color(0xFF0E2422), onBackground = Color(0xFFECE5D5),
    surface = Color(0xFF0E2422), onSurface = Color(0xFFECE5D5),
    surfaceVariant = Color(0xFF223E3A), onSurfaceVariant = Color(0xFFA6BBB4),
    surfaceContainerLowest = Color(0xFF0A1C1A), surfaceContainerLow = Color(0xFF122B28),
    surfaceContainer = Color(0xFF16312E), surfaceContainerHigh = Color(0xFF1C3935), surfaceContainerHighest = Color(0xFF23423D),
    outline = Color(0xFF557069), outlineVariant = Color(0xFF29453F),
    error = Color(0xFFF2907F), onError = Color(0xFF4A0F06), errorContainer = Color(0xFF5A2219), onErrorContainer = Color(0xFFFFDAD3),
)

private val Day = lightColorScheme(
    primary = Color(0xFF1F4A52), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE2DE), onPrimaryContainer = Color(0xFF0B2A2F),
    secondary = Color(0xFF4C6560), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE6E1), onSecondaryContainer = Color(0xFF14302B),
    tertiary = Color(0xFF2E7550), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3EBDC), onTertiaryContainer = Color(0xFF0B2A18),
    background = Color(0xFFEDF0EA), onBackground = Color(0xFF172E2B),
    surface = Color(0xFFEDF0EA), onSurface = Color(0xFF172E2B),
    surfaceVariant = Color(0xFFDDE4DF), onSurfaceVariant = Color(0xFF4F625D),
    surfaceContainerLowest = Color(0xFFF8FAF6), surfaceContainerLow = Color(0xFFF2F5F0),
    surfaceContainer = Color(0xFFE6EBE5), surfaceContainerHigh = Color(0xFFDFE5DF), surfaceContainerHighest = Color(0xFFD7DED8),
    outline = Color(0xFF8A9C96), outlineVariant = Color(0xFFCAD4CE),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

/** Colours outside Material's slots: brass, the colour of the dice and of what to do next. */
data class Accents(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
)

private val NightAccents = Accents(Color(0xFFD9A84E), Color(0xFF2A1C02), Color(0xFF3B3218), Color(0xFFF3DDA8))
private val DayAccents = Accents(Color(0xFF8E6210), Color.White, Color(0xFFF2E2BE), Color(0xFF3A2700))

val androidx.compose.material3.ColorScheme.accent: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalAccents.current.accent
val androidx.compose.material3.ColorScheme.onAccent: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalAccents.current.onAccent
val androidx.compose.material3.ColorScheme.accentContainer: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalAccents.current.accentContainer
val androidx.compose.material3.ColorScheme.onAccentContainer: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalAccents.current.onAccentContainer

val LocalAccents = androidx.compose.runtime.staticCompositionLocalOf { NightAccents }

// Golos Text (ParaType, made for Russian public services; good Latin): the interface. Literata (made for e-reading,
// full Cyrillic): the story. Both are variable fonts; each weight is one setting of the same file.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun golos(w: Int) = Font(R.font.golos_text, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun literata(w: Int, italic: Boolean = false) = Font(if (italic) R.font.literata_italic else R.font.literata, FontWeight(w),
    if (italic) FontStyle.Italic else FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Golos = FontFamily(golos(400), golos(500), golos(600), golos(700))
val Literata = FontFamily(literata(400), literata(500), literata(600), literata(400, italic = true))

private val Type = Typography().let { t ->
    fun TextStyle.g(w: Int, size: Int, line: Int, track: Double = 0.0) =
        copy(fontFamily = Golos, fontWeight = FontWeight(w), fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp)
    t.copy(
        // titles in the reading face: the app is a book first
        displaySmall = t.displaySmall.copy(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 21.sp, lineHeight = 27.sp),
        titleMedium = t.titleMedium.g(600, 16, 22),
        titleSmall = t.titleSmall.g(600, 14, 20, 0.1),
        bodyLarge = t.bodyLarge.g(400, 16, 24),
        bodyMedium = t.bodyMedium.g(400, 14, 21),
        bodySmall = t.bodySmall.g(400, 12, 18),
        labelLarge = t.labelLarge.g(600, 15, 20, 0.1),
        labelMedium = t.labelMedium.g(500, 13, 18, 0.2),
        labelSmall = t.labelSmall.g(500, 11, 16, 0.3),
    )
}

/** The story text. */
val ReadingText = TextStyle(fontFamily = Literata, fontSize = 18.sp, lineHeight = 30.sp, letterSpacing = 0.1.sp)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
)

/** True when the colours come from the wallpaper (Material You): the app's mark follows them. */
val LocalDynamicColor = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), materialYou: Boolean = false, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Material You: the phone's wallpaper palette (Android 12+); otherwise the table's own
    val dynamic = materialYou && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val scheme = when {
        dynamic && dark -> androidx.compose.material3.dynamicDarkColorScheme(context)
        dynamic -> androidx.compose.material3.dynamicLightColorScheme(context)
        dark -> Night
        else -> Day
    }
    val accents = if (dynamic) Accents(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else if (dark) NightAccents else DayAccents
    androidx.compose.runtime.CompositionLocalProvider(LocalAccents provides accents, LocalDynamicColor provides dynamic) {
        MaterialTheme(colorScheme = scheme, typography = Type, shapes = AppShapes, content = content)
    }
}

/** The reader's text size setting (Settings → Text size), as a multiple of [ReadingText]. */
val LocalReadingScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** [ReadingText] at the reader's chosen size. */
@Composable
fun readingStyle(): TextStyle {
    val k = LocalReadingScale.current
    return ReadingText.copy(fontSize = ReadingText.fontSize * k, lineHeight = ReadingText.lineHeight * k)
}

/** The widest a column of text or controls gets (tablets, unfolded phones): a comfortable line, centred. */
val ContentWidth = 640.dp
