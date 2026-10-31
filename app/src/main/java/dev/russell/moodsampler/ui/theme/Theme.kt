package dev.russell.moodsampler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.russell.moodsampler.R

/** The journal palette: warm paper by day, deep ink by night. Deliberately not wallpaper-derived. */
private val Paper = lightColorScheme(
    primary = Color(0xFFB3452D),
    onPrimary = Color(0xFFFFF8F2),
    primaryContainer = Color(0xFFF6D7CA),
    onPrimaryContainer = Color(0xFF5B1C0E),
    secondary = Color(0xFF00736A),
    onSecondary = Color(0xFFFFF8F2),
    secondaryContainer = Color(0xFFCDE6E1),
    onSecondaryContainer = Color(0xFF0B3B38),
    tertiary = Color(0xFF8F5C0B),
    onTertiary = Color(0xFFFFF8F2),
    tertiaryContainer = Color(0xFFF5E0B5),
    onTertiaryContainer = Color(0xFF4A2E00),
    error = Color(0xFFA8201A),
    onError = Color(0xFFFFF8F2),
    errorContainer = Color(0xFFF9DAD4),
    onErrorContainer = Color(0xFF5C0F0B),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFE9E1D0),
    onSurfaceVariant = Color(0xFF675E52),
    surfaceDim = Color(0xFFE1D7C3),
    surfaceBright = Color(0xFFFFFCF5),
    surfaceContainerLowest = Color(0xFFFFFCF5),
    surfaceContainerLow = Color(0xFFFBF7EE),
    surfaceContainer = Color(0xFFF0E9DB),
    surfaceContainerHigh = Color(0xFFE9E1D0),
    surfaceContainerHighest = Color(0xFFE1D7C3),
    inverseSurface = Color(0xFF2B2621),
    inverseOnSurface = Color(0xFFF2EADC),
    inversePrimary = Color(0xFFF0927A),
    outline = Color(0xFF9C9180),
    outlineVariant = Color(0xFFDCD2BF),
)

private val Ink = darkColorScheme(
    primary = Color(0xFFF0927A),
    onPrimary = Color(0xFF4A1408),
    primaryContainer = Color(0xFF6E2A1A),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFF7CCBC3),
    onSecondary = Color(0xFF08322F),
    secondaryContainer = Color(0xFF1D4F4B),
    onSecondaryContainer = Color(0xFFBFEAE4),
    tertiary = Color(0xFFE9BC6B),
    onTertiary = Color(0xFF3E2900),
    tertiaryContainer = Color(0xFF5B3F0D),
    onTertiaryContainer = Color(0xFFFCE3B2),
    error = Color(0xFFF2887E),
    onError = Color(0xFF4F0D08),
    errorContainer = Color(0xFF6F1E17),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF1C1917),
    onBackground = Color(0xFFF2EADC),
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFF2EADC),
    surfaceVariant = Color(0xFF342E28),
    onSurfaceVariant = Color(0xFFB8AD9C),
    surfaceDim = Color(0xFF171411),
    surfaceBright = Color(0xFF3E3730),
    surfaceContainerLowest = Color(0xFF171411),
    surfaceContainerLow = Color(0xFF25211C),
    surfaceContainer = Color(0xFF2B2621),
    surfaceContainerHigh = Color(0xFF342E28),
    surfaceContainerHighest = Color(0xFF3E3730),
    inverseSurface = Color(0xFFF2EADC),
    inverseOnSurface = Color(0xFF2B2621),
    inversePrimary = Color(0xFFB3452D),
    outline = Color(0xFF7D7365),
    outlineVariant = Color(0xFF3E3730),
)

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    if (style == FontStyle.Italic) R.font.fraunces_italic else R.font.fraunces,
    weight,
    style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    R.font.figtree,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Display serif: headlines, big numbers and asides. Bundled subset to Latin, with only the weight axis left (see README). */
val Fraunces = FontFamily(
    fraunces(FontWeight.Normal),
    fraunces(FontWeight.Medium),
    fraunces(FontWeight.SemiBold),
    fraunces(FontWeight.Normal, FontStyle.Italic),
)

/** Text sans: body copy, labels and controls. */
val Figtree = FontFamily(
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
)

private fun serif(size: Int, line: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = Fraunces,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.01).em,
    fontFeatureSettings = "lnum",
)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private val JournalTypography = Typography(
    displayLarge = serif(64, 68),
    displayMedium = serif(52, 56),
    displaySmall = serif(40, 46),
    headlineLarge = serif(34, 40),
    headlineMedium = serif(30, 36),
    headlineSmall = serif(24, 30),
    titleLarge = serif(21, 28),
    titleMedium = sans(16, 22, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 20),
    bodySmall = sans(13, 18),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(11, 16, FontWeight.Medium, tracking = 0.01),
)

/** Small letterspaced caps over a section, like a journal's running head. */
val EyebrowStyle = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.12.em,
)

/** The journal's aside: italic serif for reassurance, placeholders and fine print worth reading. */
val AsideStyle = TextStyle(
    fontFamily = Fraunces,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

private val JournalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MoodSamplerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Ink else Paper,
        typography = JournalTypography,
        shapes = JournalShapes,
        content = content,
    )
}
