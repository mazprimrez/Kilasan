package com.onedayonesecond.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
import androidx.compose.ui.unit.sp
import com.onedayonesecond.R
import com.onedayonesecond.data.Mood

/**
 * The whole palette: white paper, plum ink, and one pink. There is one rose button
 * per screen, and nothing else competes with it. There is no black and no grey.
 */
object Palette {
    /** Every background. */
    val Paper = Color(0xFFFFFFFF)

    /** Text and icons, and the background of the message bar. */
    val Ink = Color(0xFF2E2329)

    /** Dates, secondary text, inactive tabs, the outline of fields. */
    val Muted = Color(0xFF6E6168)

    /** Actions, selection, today's outline, progress, mood faces. */
    val Rose = Color(0xFFAE4A6B)

    /** Fills only: the tab indicator, tonal buttons, a selected segment, progress tracks. */
    val Blush = Color(0xFFFBCCDD)

    /** The tab bar, tinted cards, the search bar, the circles behind list icons. */
    val BlushTint = Color(0xFFFDF2F6)

    /** Chart bars that are not the top value: a deeper blush, so Rose stays the one highlight. */
    val BlushDeep = Color(0xFFF7BDD0)

    /** 1 dp dividers, and the frames of days that have not come yet. */
    val Hairline = Color(0xFFF1E6EA)

    /** Outlined buttons and chips, the dashed frames of missed days, the sheet handle. */
    val FrameLine = Color(0xFFE8C2D1)

    /** Things going wrong, and actions that delete. */
    val Error = Color(0xFFB3261E)

    /** Behind a warning that something will be replaced. */
    val ErrorTint = Color(0xFFFCEEEE)

    /** The dashed outline of a day with no mood in the year in pixels: dark enough to read at that size. */
    val PixelEmpty = Color(0xFFD9A7BB)

    /** Over the page, behind a sheet or dialog. */
    val Scrim = Ink.copy(alpha = 0.32f)

    /**
     * Only for the year in pixels: the darker the colour, the harder the day. Everywhere
     * else a mood is its face and its word.
     */
    fun mood(mood: Mood): Color = when (mood) {
        Mood.GREAT -> Color(0xFFF9D3E0)
        Mood.GOOD -> Color(0xFFF7BDD0)
        Mood.NEUTRAL -> Color(0xFFE58AAB)
        Mood.BAD -> Color(0xFFB8527A)
        Mood.ROUGH -> Color(0xFF6E2445)
    }
}

/** The corners. A frame of the contact sheet is nearly square-cornered; buttons are pills. */
object Corners {
    /** A day's frame on the contact sheet, and a book cover. */
    val Frame = RoundedCornerShape(3.dp)

    /** The small frames of a week strip or the trim strip. */
    val MiniFrame = RoundedCornerShape(2.dp)

    /** Text fields, and the message bar. */
    val Field = RoundedCornerShape(4.dp)

    /** Chips, and the large still on a day page. */
    val Chip = RoundedCornerShape(8.dp)
    val Still = RoundedCornerShape(8.dp)

    /** The reel and trim previews, and inline alerts. */
    val Preview = RoundedCornerShape(12.dp)
    val Alert = RoundedCornerShape(12.dp)

    /** Tinted cards, and the floating action button. */
    val Card = RoundedCornerShape(16.dp)

    /** Buttons: 48 dp high, fully rounded. */
    val Button = RoundedCornerShape(24.dp)

    val Search = RoundedCornerShape(28.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

// Roboto Flex ships as one variable font file; each weight is the same file with a
// different setting on its weight axis, and the slanted cut is its slant axis.
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: FontWeight, slanted: Boolean = false) = Font(
    resId = R.font.roboto_flex,
    weight = weight,
    style = if (slanted) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.slant(if (slanted) -10f else 0f),
    ),
)

/** Every word in the app. */
val RobotoFlex = FontFamily(
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    flex(FontWeight.Medium, slanted = true),
)

/** Only for measurements: frame numbers, timecodes, page numbers, counts. */
@OptIn(ExperimentalTextApi::class)
val SplineSansMono = FontFamily(
    Font(
        resId = R.font.spline_sans_mono,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

private fun text(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = RobotoFlex,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private fun mono(size: Int, line: Int) = TextStyle(
    fontFamily = SplineSansMono,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = "tnum",
)

/** The type scale. Words are Roboto Flex; anything measured is Spline Sans Mono. */
object AppType {
    /** The big day number on a day page. */
    val Display = text(36, 44, FontWeight.Medium)

    /** The title of a tab, and the headline of a recap. */
    val Title = text(28, 36, FontWeight.Medium)

    /** The title inside a detail screen or a sheet. */
    val Headline = text(24, 32, FontWeight.Medium)

    /** The title in a top bar, and the headline of a card. */
    val TitleLarge = text(22, 28, FontWeight.Medium)

    /** Section headings, and the title of a row. */
    val Heading = text(16, 24, FontWeight.SemiBold)
    val TitleSmall = text(14, 20, FontWeight.SemiBold)

    val Body = text(16, 24, FontWeight.Normal)
    val BodyStrong = Heading

    /** Secondary lines. */
    val Meta = text(14, 20, FontWeight.Normal)
    val Small = text(12, 16, FontWeight.Normal)

    /** Buttons. */
    val Label = text(14, 20, FontWeight.SemiBold)

    /** Tab labels and small captions. */
    val Tab = text(12, 16, FontWeight.SemiBold)
    val LabelSmall = text(11, 16, FontWeight.SemiBold)

    /** Words quoted back to the person who wrote them. Nothing else is slanted. */
    val Quote = text(22, 28, FontWeight.Medium).copy(fontStyle = FontStyle.Italic)

    /** The number under a frame. */
    val FrameNumber = mono(11, 15)
    val Timecode = mono(12, 16)

    /** A value at the end of a row: a time of day, a count. */
    val Value = mono(14, 20)

    /** A page number beside a thought. */
    val NumeralMedium = mono(18, 22)

    /** A count that stands on its own: seconds kept, the page of a bookmark. */
    val NumeralLarge = mono(20, 24)
}

// Material's fifteen text styles are pointed at the same scale, so its own
// components (dialogs, text fields, menus) cannot introduce another size.
private val AppTypography = Typography(
    displayLarge = AppType.Display,
    displayMedium = AppType.Display,
    displaySmall = AppType.Display,
    headlineLarge = AppType.Title,
    headlineMedium = AppType.Title,
    headlineSmall = AppType.Headline,
    titleLarge = AppType.TitleLarge,
    titleMedium = AppType.Heading,
    titleSmall = AppType.TitleSmall,
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Meta,
    bodySmall = AppType.Small,
    labelLarge = AppType.Label,
    labelMedium = AppType.Tab,
    labelSmall = AppType.LabelSmall,
)

// Light only for now. Every surface role is paper: tinted cards and the tab bar ask
// for blush tint by name instead of getting it from a surface role.
private val AppColors = lightColorScheme(
    primary = Palette.Rose,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.Blush,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Rose,
    onSecondary = Palette.Paper,
    secondaryContainer = Palette.Blush,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Rose,
    onTertiary = Palette.Paper,
    tertiaryContainer = Palette.Blush,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Paper,
    onSurfaceVariant = Palette.Muted,
    surfaceTint = Palette.Paper,
    surfaceBright = Palette.Paper,
    surfaceDim = Palette.Paper,
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Palette.Paper,
    surfaceContainerHighest = Palette.Paper,
    surfaceContainerLow = Palette.Paper,
    surfaceContainerLowest = Palette.Paper,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Paper,
    inversePrimary = Palette.Blush,
    outline = Palette.Muted,
    outlineVariant = Palette.Hairline,
    error = Palette.Error,
    onError = Palette.Paper,
    errorContainer = Palette.ErrorTint,
    onErrorContainer = Palette.Ink,
    scrim = Palette.Ink,
)

private val AppShapes = Shapes(
    extraSmall = Corners.Field,
    small = Corners.Chip,
    medium = Corners.Alert,
    large = Corners.Card,
    extraLarge = Corners.Search,
)

@Composable
fun OneDayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
