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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ila3.dshnative.R

enum class DshThemeMode(val label: String) {
    PAPER("纸白"),
    AURORA("夜墨"),
    GRAPHITE("墨石"),
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
    bg = Color(0xFFF7F4EE),
    surface = Color(0xFFFFFDF8),
    surface2 = Color(0xFFEFE9DE),
    text = Color(0xFF1F1B16),
    muted = Color(0xFF6E6458),
    accent = Color(0xFF1F4E79),
    accentSoft = Color(0xFF4A7BA6),
    onAccent = Color(0xFFFFFDF8),
    border = Color(0xFFE4DDCF),
    danger = Color(0xFFA63D2C),
    userBubble = Color(0xFFEFEAE0),
    selected = Color(0xFFE3E9EF),
    errorBg = Color(0xFFF7E9E4),
    errorBorder = Color(0xFFE5C3B8),
    light = true,
)

private val aurora = DshPalette(
    bg = Color(0xFF12110F),
    surface = Color(0xFF1B1916),
    surface2 = Color(0xFF262320),
    text = Color(0xFFEFE9DE),
    muted = Color(0xFF938B7E),
    accent = Color(0xFF8FB4D9),
    accentSoft = Color(0xFFB3CDE6),
    onAccent = Color(0xFF12110F),
    border = Color(0xFF3A342C),
    danger = Color(0xFFE08B7D),
    userBubble = Color(0xFF262320),
    selected = Color(0xFF2E3A46),
    errorBg = Color(0xFF2A1714),
    errorBorder = Color(0xFF5C2E26),
    light = false,
)

/** Graphite：墨石。层级靠明度而不是边框，状态靠绿/琥珀/红三个点。 */
private val graphite = DshPalette(
    bg = Color(0xFF101210),
    surface = Color(0xFF191E1A),
    surface2 = Color(0xFF1F2521),
    text = Color(0xFFE7EBE4),
    muted = Color(0xFF9AA49A),
    accent = Color(0xFFE7EBE4),
    accentSoft = Color(0xFF6FD68C),
    onAccent = Color(0xFF101210),
    border = Color(0xFF2A302A),
    danger = Color(0xFFC27469),
    userBubble = Color(0xFF191E1A),
    selected = Color(0xFF232A25),
    errorBg = Color(0xFF2A1A17),
    errorBorder = Color(0xFF5C302A),
    light = false,
)

/** 持久化用的短键。 */
val DshThemeMode.key: String
    get() = when (this) {
        DshThemeMode.PAPER -> "paper"
        DshThemeMode.AURORA -> "aurora"
        DshThemeMode.GRAPHITE -> "graphite"
    }

/** 切换顺序：纸白 → 夜墨 → 墨石 → 纸白。 */
val DshThemeMode.next: DshThemeMode
    get() = when (this) {
        DshThemeMode.PAPER -> DshThemeMode.AURORA
        DshThemeMode.AURORA -> DshThemeMode.GRAPHITE
        DshThemeMode.GRAPHITE -> DshThemeMode.PAPER
    }

/** 首次启动落在墨石；已有偏好照旧。 */
fun dshThemeFrom(key: String?): DshThemeMode = when (key) {
    "paper" -> DshThemeMode.PAPER
    "aurora" -> DshThemeMode.AURORA
    else -> DshThemeMode.GRAPHITE
}

/** App-wide, observable theme selection. Preferences are written by ThemeToggle. */
object DshTheme {
    var mode by mutableStateOf(DshThemeMode.GRAPHITE)
    val colors: DshPalette
        get() = when (mode) {
            DshThemeMode.PAPER -> paper
            DshThemeMode.AURORA -> aurora
            DshThemeMode.GRAPHITE -> graphite
        }
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
val DshBackgroundBrush: Brush get() = when (DshTheme.mode) {
    DshThemeMode.PAPER -> Brush.verticalGradient(listOf(Color(0xFFF9F6F0), Color(0xFFF2EDE3)))
    DshThemeMode.AURORA -> Brush.verticalGradient(listOf(Color(0xFF14120F), Color(0xFF0E0D0B)))
    // 墨石：平底色，层级交给 surface
    DshThemeMode.GRAPHITE -> Brush.verticalGradient(listOf(Color(0xFF101210), Color(0xFF101210)))
}

/** IBM Plex Sans：正文。 */
val DshSans: FontFamily = FontFamily(
    Font(R.font.plex_sans, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
)

/** IBM Plex Mono：时间戳、命令、计数。 */
val DshMono: FontFamily = FontFamily(Font(R.font.plex_mono, FontWeight.Normal))

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = DshSans),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = DshSans),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFamily = DshSans),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 0.sp, fontFamily = FontFamily.Monospace),
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
