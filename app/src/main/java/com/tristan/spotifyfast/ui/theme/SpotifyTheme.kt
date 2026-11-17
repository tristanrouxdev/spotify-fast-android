package com.tristan.spotifyfast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens derived from the Artcraft `design/spotify-fast.figcraft` specification.
 */
object SpotifyTokens {
    val Background = Color(0xFF121212)
    val SurfaceCard = Color(0xFF181818)
    val SurfaceElevated = Color(0xFF282828)
    val SurfaceHighlight = Color(0xFF333333)
    val AccentGreen = Color(0xFF1DB954)
    val AccentGreenBright = Color(0xFF1ED760)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF727272)
    val Divider = Color(0xFF2A2A2A)
    val ErrorRed = Color(0xFFE91429)

    val MiniPlayerHeight = 58.dp
    val BottomNavHeight = 64.dp
    val ScreenHorizontalPadding = 16.dp
    val CardCornerRadius = 6.dp
    val ChipCornerRadius = 20.dp
}

private val SpotifyDarkColorScheme = darkColorScheme(
    primary = SpotifyTokens.AccentGreen,
    onPrimary = Color.Black,
    secondary = SpotifyTokens.AccentGreenBright,
    onSecondary = Color.Black,
    background = SpotifyTokens.Background,
    onBackground = SpotifyTokens.TextPrimary,
    surface = SpotifyTokens.SurfaceCard,
    onSurface = SpotifyTokens.TextPrimary,
    surfaceVariant = SpotifyTokens.SurfaceElevated,
    onSurfaceVariant = SpotifyTokens.TextSecondary,
    error = SpotifyTokens.ErrorRed,
    onError = Color.White,
)

private val SpotifyTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = SpotifyTokens.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        color = SpotifyTokens.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        color = SpotifyTokens.TextPrimary,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = SpotifyTokens.TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        color = SpotifyTokens.TextPrimary,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = SpotifyTokens.TextSecondary,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = SpotifyTokens.TextPrimary,
    ),
)

@Composable
fun SpotifyFastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotifyDarkColorScheme,
        typography = SpotifyTypography,
        content = content,
    )
}
