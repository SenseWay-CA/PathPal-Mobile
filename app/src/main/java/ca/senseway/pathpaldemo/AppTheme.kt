package ca.senseway.pathpaldemo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDeep       = Color(0xFF06060F)
val BgNavy       = Color(0xFF0D101D)
val PanelPurple  = Color(0xFF171523)
val PanelPurple2 = Color(0xFF1E1B2E)
val ElectricBlue = Color(0xFF4E8EF3)
val VioletAccent = Color(0xFFA555F4)
val TextWhite    = Color(0xFFF3F4F6)
val TextGray     = Color(0xFFCCCDD1)
val TextMuted    = Color(0xFF6B7280)
val GreenOk      = Color(0xFF22C55E)
val YellowWarn   = Color(0xFFF59E0B)
val RedAlert     = Color(0xFFEF4444)
val CardBorder   = Color(0xFF2A2640)

@Composable
fun SenseWayAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary            = ElectricBlue,
            onPrimary          = Color.White,
            primaryContainer   = Color(0xFF1A2744),
            secondary          = VioletAccent,
            onSecondary        = Color.White,
            secondaryContainer = Color(0xFF2A1644),
            background         = BgDeep,
            onBackground       = TextWhite,
            surface            = PanelPurple,
            onSurface          = TextWhite,
            surfaceVariant     = PanelPurple2,
            outline            = CardBorder,
            error              = RedAlert,
        ),
        content = content
    )
}
