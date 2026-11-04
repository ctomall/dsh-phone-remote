package com.ila3.dshnative.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class DshThemeMode(val label: String) {
    PAPER("云白"),
    AURORA("深海"),
}

data class DshPalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val border: Color,
    val danger: Color,
    val userBubble: Color,
    val selected: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val light: Boolean,
)

private val paper = DshPalette(
    bg = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F4FB),
    text = Color(0xFF0B1220),
    muted = Color(0xFF64748B),
    accent = Color(0xFF4D6BFE),
    accentSoft = Color(0xFF6B8AFD),
    onAccent = Color(0xFFFFFFFF),
    border = Color(0xFFE9EEF8),
    danger = Color(0xFFEF4444),
    userBubble = Color(0xFFEEF2FF),
    selected = Color(0xFFDCE4FF),
    errorBg = Color(0xFFFEF2F2),
    errorBorder = Color(0xFFFECACA),
    light = true,
)

private val aurora = DshPalette(
    bg = Color(0xFF070D18),
    surface = Color(0xFF0F1C2E),
    surface2 = Color(0xFF0A1626),
    text = Color(0xFFE8F1FF),
    muted = Color(0xFF8EA6C4),
    accent = Color(0xFF2DD4BF),
    accentSoft = Color(0xFF5EEAD4),
    onAccent = Color(0xFF04212A),
    border = Color(0xFF1E3A52),
    danger = Color(0xFFFCA5A5),
    userBubble = Color(0xFF143A4A),
    selected = Color(0xFF143A4A),
    errorBg = Color(0xFF2A1518),
    errorBorder = Color(0xFF7F1D1D),
    light = false,
)

/** App-wide, observable theme selection. Preferences are written by ThemeToggle. */
object DshTheme {
    var mode by mutableStateOf(DshThemeMode.PAPER)
    val colors: DshPalette
        get() = if (mode == DshThemeMode.PAPER) paper else aurora
}

val DshBg: Color get() = DshTheme.colors.bg
val DshSurface: Color get() = DshTheme.colors.surface
val DshSurface2: Color get() = DshTheme.colors.surface2
val DshText: Color get() = DshTheme.colors.text
val DshMuted: Color get() = DshTheme.colors.muted
val DshTeal: Color get() = DshTheme.colors.accent
val DshTealSoft: Color get() = DshTheme.colors.accentSoft
val DshOnAccent: Color get() = DshTheme.colors.onAccent
val DshBorder: Color get() = DshTheme.colors.border
val DshDanger: Color get() = DshTheme.colors.danger
val DshUserBubble: Color get() = DshTheme.colors.userBubble
val DshSelected: Color get() = DshTheme.colors.selected
val DshErrorBg: Color get() = DshTheme.colors.errorBg
val DshErrorBorder: Color get() = DshTheme.colors.errorBorder
val DshBackgroundBrush: Brush get() = if (DshTheme.colors.light) {
    Brush.verticalGradient(listOf(Color(0xFFF8FAFF), Color(0xFFEEF2FF)))
} else {
    Brush.verticalGradient(listOf(Color(0xFF070D18), Color(0xFF050A14)))
}

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.SansSerif),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.SansSerif),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
)

@Composable
fun DshNativeTheme(content: @Composable () -> Unit) {
    val palette = DshTheme.colors
    val scheme = if (palette.light) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentSoft,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surface2,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = palette.danger,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentSoft,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surface2,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = palette.danger,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = palette.bg.toArgb()
            window.navigationBarColor = palette.bg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.light
                isAppearanceLightNavigationBars = palette.light
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}


private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
