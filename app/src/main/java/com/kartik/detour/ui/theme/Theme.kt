@file:OptIn(ExperimentalTextApi::class)

package com.kartik.detour.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartik.detour.R
import com.kartik.detour.data.LoopType

/** Night-drive palette: deep indigo road, lilac-white text, detour-sign amber. */
object Ink {
    val Night = Color(0xFF120F1F)     // app background
    val Dusk = Color(0xFF1B1730)      // surfaces
    val Haze = Color(0xFF262140)      // raised surfaces, pressed states
    val Line = Color(0xFF332D52)      // hairlines, the route line
    val Paper = Color(0xFFF3EFFF)     // primary text
    val Fog = Color(0xFFA69FC4)       // secondary text
    val Dim = Color(0xFF6E6790)       // tertiary text
    val Sign = Color(0xFFFFB020)      // detour-sign amber: brand, progress, primary action
    val SignInk = Color(0xFF1A1206)   // text on amber
    val StickerInk = Color(0xFF141024) // text on sticker cards
    val Saffron = Color(0xFFFF9933)   // India news marker
    val Danger = Color(0xFFFF6B6B)

    // "Stay in the loop" sticker colours
    val Slang = Color(0xFFFF8FC7)
    val Psych = Color(0xFFB9A4FF)
    val Acronym = Color(0xFF7FD8F5)
    val Paradox = Color(0xFFFF9B5E)
    val Meme = Color(0xFF8EF0B4)

    fun sticker(type: LoopType): Color = when (type) {
        LoopType.Slang -> Slang
        LoopType.Psych -> Psych
        LoopType.Acronym -> Acronym
        LoopType.Paradox -> Paradox
        LoopType.Meme -> Meme
    }

    fun category(key: String): Color = when (key) {
        "india" -> Saffron
        "history" -> Paradox
        "money" -> Meme
        "mind" -> Psych
        "ai" -> Acronym
        "philosophy" -> Slang
        "science" -> Sign
        else -> Fog
    }
}

private fun bricolage(weight: Int, width: Float, opsz: Float) = Font(
    resId = R.font.bricolage,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("opsz", opsz),
    ),
)

/** Condensed, heavy cut for big moments: greetings, card terms, numbers. */
val Display = FontFamily(
    bricolage(800, 75f, 96f),
    bricolage(700, 75f, 72f),
)

/** Text cut: full width, optical size tuned for reading. */
val Text = FontFamily(
    bricolage(400, 100f, 14f),
    bricolage(500, 100f, 14f),
    bricolage(600, 100f, 18f),
    bricolage(700, 100f, 24f),
)

val DetourType = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 30.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight(700), fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Text, fontWeight = FontWeight(700), fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight(600), fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = Text, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Text, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Text, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Text, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 14.sp),
)

private val scheme = darkColorScheme(
    primary = Ink.Sign,
    onPrimary = Ink.SignInk,
    secondary = Ink.Psych,
    onSecondary = Ink.StickerInk,
    background = Ink.Night,
    onBackground = Ink.Paper,
    surface = Ink.Dusk,
    onSurface = Ink.Paper,
    surfaceVariant = Ink.Haze,
    onSurfaceVariant = Ink.Fog,
    surfaceContainer = Ink.Dusk,
    surfaceContainerHigh = Ink.Haze,
    surfaceContainerHighest = Ink.Haze,
    outline = Ink.Line,
    outlineVariant = Ink.Line,
    error = Ink.Danger,
)

@Composable
fun DetourTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = DetourType,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(28.dp),
        ),
    ) {
        // No Surface at the root, so set the default text/icon colour explicitly.
        CompositionLocalProvider(LocalContentColor provides Ink.Paper, content = content)
    }
}
