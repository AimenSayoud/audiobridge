package dev.audiobridge.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.log10
import kotlin.math.roundToInt

private const val FLOOR_DB = -60f

/**
 * Peak meter, scaled in decibels rather than raw amplitude.
 *
 * A linear meter reads as dead for anything but loud material — music at a
 * comfortable level peaks around -18 dBFS, which is 12% of full scale and looks
 * like nothing. Mapping [FLOOR_DB]..0 dB across the width is what makes it
 * behave like a meter instead of a decoration.
 */
@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier, segments: Int = 32) {
    val db = if (level <= 0.0001f) FLOOR_DB else (20f * log10(level)).coerceAtLeast(FLOOR_DB)
    val target = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)

    // Fast attack, slower release: a peak that vanishes instantly cannot be read.
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (target > 0.02f) 60 else 260),
        label = "level",
    )

    val dim = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val gap = size.width * 0.006f
            val segmentWidth = (size.width - gap * (segments - 1)) / segments
            val lit = (animated * segments).roundToInt()
            for (i in 0 until segments) {
                val fraction = i / (segments - 1f)
                val on = i < lit
                drawRoundRect(
                    color = if (on) segmentColor(fraction) else dim,
                    topLeft = Offset(i * (segmentWidth + gap), 0f),
                    size = Size(segmentWidth, size.height),
                    cornerRadius = CornerRadius(segmentWidth * 0.35f),
                )
            }
        }
        Text(
            if (level <= 0.0001f) "silent" else "${db.roundToInt()} dBFS peak",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Green through most of the range, amber approaching full scale, red at the top. */
private fun segmentColor(fraction: Float): Color = when {
    fraction < 0.72f -> Color(0xFF4ADE80)
    fraction < 0.90f -> Color(0xFFFBBF24)
    else -> Color(0xFFF87171)
}
