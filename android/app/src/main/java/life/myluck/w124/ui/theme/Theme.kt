package life.myluck.w124.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF10141C)
val Surface = Color(0xFF1A2030)
val Surface2 = Color(0xFF242C3F)
val Gold = Color(0xFFD4B06A)
val OnGold = Color(0xFF1A1408)
val Danger = Color(0xFFE06C75)
val Warn = Color(0xFFE5C07B)
val Ok = Color(0xFF7DC4A0)
val Muted = Color(0xFF8B93A7)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = OnGold,
    secondary = Ok,
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    onBackground = Color(0xFFE8ECF4),
    onSurface = Color(0xFFE8ECF4),
    onSurfaceVariant = Muted,
    error = Danger,
    outline = Color(0xFF3A4258),
)

@Composable
fun W124Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
