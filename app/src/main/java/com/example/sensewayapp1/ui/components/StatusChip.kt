package com.example.sensewayapp1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class Status { Safe, Warn, Danger, Neutral }

@Composable
fun StatusChip(text: String, status: Status, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        Status.Safe -> Color(0x1A22C55E) to Color(0xFF22C55E)
        Status.Warn -> Color(0x1AFFA800.toInt()) to Color(0xFFFFA800)
        Status.Danger -> Color(0x1AEF4444) to Color(0xFFEF4444)
        Status.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier.background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}
