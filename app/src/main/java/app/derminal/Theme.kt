package app.derminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable data class UiColors(
    val canvas: Color, val surface: Color, val raised: Color, val ink: Color,
    val muted: Color, val line: Color, val wash: Color, val pressed: Color,
    val accent: Color, val onAccent: Color, val onInk: Color, val keyDeck: Color,
    val danger: Color, val dark: Boolean,
)

fun interfaceColors(theme: String): UiColors = when(theme) {
    "ink" -> UiColors(Color(0xFF14161A),Color(0xFF1B1E24),Color(0xFF252A33),Color(0xFFE2E4E9),Color(0xFFA2ACBA),Color(0xFF3B424F),Color(0xFF282D36),Color(0xFF343D49),Color(0xFFA9C2D7),Color(0xFF17232D),Color(0xFF171A20),Color(0xFF1D2128),Color(0xFFF19AA1),true)
    "nord" -> UiColors(Color(0xFF2E3440),Color(0xFF333B48),Color(0xFF414B5B),Color(0xFFD8DEE9),Color(0xFFACB7C8),Color(0xFF526075),Color(0xFF3D4655),Color(0xFF4C596C),Color(0xFF88C0D0),Color(0xFF22323C),Color(0xFF29313C),Color(0xFF343D4B),Color(0xFFF0A6AE),true)
    "solar" -> UiColors(Color(0xFFF7F3E8),Color(0xFFFBF8F0),Color(0xFFFFFCF6),Color(0xFF514B40),Color(0xFF7E7564),Color(0xFFDCD3C1),Color(0xFFEFE9DC),Color(0xFFE6DECC),Color(0xFF806D42),Color(0xFFFFFCF6),Color(0xFFFFFCF6),Color(0xFFF2EDE2),Color(0xFFA2383C),false)
    else -> UiColors(Color.White,Color.White,Color.White,Color(0xFF202327),Color(0xFF666D76),Color(0xFFE1E5EA),Color(0xFFF3F5F7),Color(0xFFE6EAF0),Color(0xFF365B75),Color.White,Color.White,Color(0xFFF6F7F9),Color(0xFFAD3239),false)
}

val LocalUiColors=staticCompositionLocalOf {interfaceColors("paper")}
val Ink: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.ink
val Muted: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.muted
val Line: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.line
val Wash: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.wash
val Accent: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.accent
val Danger: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.danger
val Surface: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.surface
val Raised: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.raised
val OnInk: Color @Composable @ReadOnlyComposable get()=LocalUiColors.current.onInk

