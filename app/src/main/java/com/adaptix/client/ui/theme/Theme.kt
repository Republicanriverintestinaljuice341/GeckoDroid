package com.adaptix.client.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// Console theme colors (matches desktop console_*.json)
data class ConsoleColors(
    val background: Color,
    @DrawableRes val backgroundImage: Int? = null,
    val backgroundDimming: Float = 0.72f,   // 0.0 = no dim, 1.0 = full black
    val text: Color,                         // default output text
    val inputSymbol: String = ">",
    val inputColor: Color,                   // prompt symbol color
    val commandColor: Color,                 // typed command text
    val agentMarkerColor: Color,             // username/client label
    val operatorColor: Color,                // separators, muted elements
    val taskColor: Color,                    // task ID / metadata
    val successColor: Color,
    val errorColor: Color,
    val infoColor: Color,
    val debugColor: Color
)

// Theme palette
data class AdaptixColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val surfaceBlack: Color,
    val surfaceDark: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val greenOnline: Color,
    val yellowWarning: Color,
    val redError: Color,
    val blueInfo: Color,
    val purpleAccent: Color,
    val terminalGreen: Color,
    val terminalText: Color,
    val cardBorder: Color,
    val divider: Color,
    val console: ConsoleColors
)

// Matched exactly to adaptix_dark.json desktop theme
// Softer dark palette with green accents and more visible chrome
val AdaptixDarkPalette = AdaptixColors(
    primary = Color(0xFF3D9E6E),         // primaryColor
    primaryDark = Color(0xFF2D8B5A),     // primaryAlternativeColor
    primaryLight = Color(0xFF4DAE7E),    // primaryColorHovered
    surfaceBlack = Color(0xFF121212),    // backgroundColorWorkspace
    surfaceDark = Color(0xFF151515),     // backgroundColorMain4
    surfaceCard = Color(0xFF1A1A1A),     // backgroundColorMain2
    surfaceElevated = Color(0xFF2A2A2A), // neutralColor
    textPrimary = Color(0xFFE0E0E0),     // secondaryColorPressed
    textSecondary = Color(0xFFBEBEBE),   // secondaryColor
    textMuted = Color(0xFF5A5A5A),       // secondaryColorDisabled
    greenOnline = Color(0xFF2D7A5A),     // statusColorSuccess
    yellowWarning = Color(0xFFFBC064),   // statusColorWarning
    redError = Color(0xFFE96B72),        // statusColorError
    blueInfo = Color(0xFF1BA8D5),        // statusColorInfo
    purpleAccent = Color(0xFF9070C0),
    terminalGreen = Color(0xFF3D9E6E),   // matches primary
    terminalText = Color(0xFFBEBEBE),
    cardBorder = Color(0xFF3A3A3A),      // borderColor — visible chrome
    divider = Color(0xFF2A2A2A),         // neutralColor
    console = ConsoleColors(
        background = Color(0xFF000000),
        backgroundImage = com.adaptix.client.R.drawable.console_bg_adaptix_dark,
        backgroundDimming = 0.85f,
        text = Color(0xFFE0E0E0),            // #E0E0E0
        inputSymbol = ">",
        inputColor = Color(0xFF808080),      // #808080
        commandColor = Color(0xFFE0E0E0),    // #E0E0E0
        agentMarkerColor = Color(0xFF808080),// #808080
        operatorColor = Color(0xFF808080),   // #808080
        taskColor = Color(0xFF606060),       // #606060
        successColor = Color(0xFFFFFF00),    // #FFFF00
        errorColor = Color(0xFFE32227),      // #E32227
        infoColor = Color(0xFF89CFF0),       // #89CFF0
        debugColor = Color(0xFF606060)       // #606060
    )
)

// Matched to dark_ice.json desktop theme
// Cyan/blue accented deep navy palette
val DarkIcePalette = AdaptixColors(
    primary = Color(0xFF20A0F0),         // primaryColor
    primaryDark = Color(0xFF106090),     // primaryColorDisabled
    primaryLight = Color(0xFF40B0FF),    // primaryColorHovered
    surfaceBlack = Color(0xFF000000),    // backgroundColorWorkspace — true black
    surfaceDark = Color(0xFF020405),     // backgroundColorMain4
    surfaceCard = Color(0xFF0A0F1D),     // backgroundColorMain2
    surfaceElevated = Color(0xFF0F172A), // neutralColor
    textPrimary = Color(0xFFE2E8F0),     // secondaryColor
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF475569),       // secondaryColorDisabled
    greenOnline = Color(0xFF22C55E),     // statusColorSuccess
    yellowWarning = Color(0xFFF59E0B),   // statusColorWarning
    redError = Color(0xFFEF4444),        // statusColorError
    blueInfo = Color(0xFF00F0FF),        // statusColorInfo — cyan
    purpleAccent = Color(0xFF8B5CF6),
    terminalGreen = Color(0xFF22C55E),   // matches success
    terminalText = Color(0xFFE2E8F0),
    cardBorder = Color(0xFF1E293B),      // borderColor — navy border
    divider = Color(0xFF0F172A),         // neutralColor
    console = ConsoleColors(
        background = Color(0xFF020405),      // #020405 — no image
        backgroundImage = null,
        backgroundDimming = 0f,
        text = Color(0xFFC8D6E5),            // #C8D6E5
        inputSymbol = ">",                   // > (not ▶)
        inputColor = Color(0xFF5B7FA5),      // #5B7FA5
        commandColor = Color(0xFFE0F0FF),    // #E0F0FF
        agentMarkerColor = Color(0xFF20A0F0),// #20A0F0
        operatorColor = Color(0xFF5B7FA5),   // #5B7FA5
        taskColor = Color(0xFF334155),       // #334155
        successColor = Color(0xFF00F0FF),    // #00F0FF
        errorColor = Color(0xFFFF4D6A),      // #FF4D6A
        infoColor = Color(0xFF40B0FF),       // #40B0FF
        debugColor = Color(0xFF334155)       // #334155
    )
)

enum class AppTheme(val displayName: String, val palette: AdaptixColors) {
    ADAPTIX_DARK("Adaptix Dark", AdaptixDarkPalette),
    DARK_ICE("Dark Ice", DarkIcePalette)
}

val LocalAdaptixColors = staticCompositionLocalOf { AdaptixDarkPalette }

// Convenience accessors
val Crimson: Color @Composable get() = LocalAdaptixColors.current.primary
val CrimsonDark: Color @Composable get() = LocalAdaptixColors.current.primaryDark
val CrimsonLight: Color @Composable get() = LocalAdaptixColors.current.primaryLight
val SurfaceBlack: Color @Composable get() = LocalAdaptixColors.current.surfaceBlack
val SurfaceDark: Color @Composable get() = LocalAdaptixColors.current.surfaceDark
val SurfaceCard: Color @Composable get() = LocalAdaptixColors.current.surfaceCard
val SurfaceElevated: Color @Composable get() = LocalAdaptixColors.current.surfaceElevated
val TextPrimary: Color @Composable get() = LocalAdaptixColors.current.textPrimary
val TextSecondary: Color @Composable get() = LocalAdaptixColors.current.textSecondary
val TextMuted: Color @Composable get() = LocalAdaptixColors.current.textMuted
val GreenOnline: Color @Composable get() = LocalAdaptixColors.current.greenOnline
val YellowWarning: Color @Composable get() = LocalAdaptixColors.current.yellowWarning
val RedError: Color @Composable get() = LocalAdaptixColors.current.redError
val BlueInfo: Color @Composable get() = LocalAdaptixColors.current.blueInfo
val PurpleAccent: Color @Composable get() = LocalAdaptixColors.current.purpleAccent
val TerminalGreen: Color @Composable get() = LocalAdaptixColors.current.terminalGreen
val TerminalText: Color @Composable get() = LocalAdaptixColors.current.terminalText
val CardBorder: Color @Composable get() = LocalAdaptixColors.current.cardBorder
val DividerColor: Color @Composable get() = LocalAdaptixColors.current.divider

// Console theme accessors
val LocalConsoleColors: ConsoleColors @Composable get() = LocalAdaptixColors.current.console

@Composable
fun AdaptixTheme(appTheme: AppTheme = AppTheme.ADAPTIX_DARK, content: @Composable () -> Unit) {
    val colors = appTheme.palette
    val colorScheme = darkColorScheme(
        primary = colors.primary,
        onPrimary = Color.White,
        primaryContainer = colors.primaryDark,
        onPrimaryContainer = Color.White,
        secondary = colors.blueInfo,
        onSecondary = Color.Black,
        tertiary = colors.purpleAccent,
        background = colors.surfaceBlack,
        onBackground = colors.textPrimary,
        surface = colors.surfaceDark,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceCard,
        onSurfaceVariant = colors.textSecondary,
        error = colors.redError,
        onError = Color.White,
        outline = colors.cardBorder,
        outlineVariant = colors.divider
    )

    CompositionLocalProvider(LocalAdaptixColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
