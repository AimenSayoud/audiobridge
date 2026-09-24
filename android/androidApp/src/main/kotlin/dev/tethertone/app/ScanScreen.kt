package dev.tethertone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tethertone.shared.PairingInfo
import kotlin.math.min

@Composable
fun ScanScreen(
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onCancel: () -> Unit,
    onManual: () -> Unit,
    onPaired: (PairingInfo) -> Unit,
) {
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (cameraGranted) {
            QrScannerView(
                modifier = Modifier.fillMaxSize(),
                torchOn = torchOn,
                onTorchAvailable = { torchAvailable = it },
                onPaired = onPaired,
            )
            Reticle(Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (cameraGranted) "Point at the QR code printed by the Mac server"
                else "Tethertone needs the camera to read the pairing QR.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(top = 12.dp),
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!cameraGranted) {
                    Button(onClick = onRequestCamera, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow camera")
                    }
                } else if (torchAvailable) {
                    OutlinedButton(onClick = { torchOn = !torchOn }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (torchOn) "Torch off" else "Torch on")
                    }
                }
                OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter address manually")
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}

/**
 * Scrim with a clear window and corner brackets.
 *
 * Drawn as four rectangles around the window rather than one rectangle with a
 * cleared hole: punching a hole needs its own compositing layer, and this costs
 * nothing and behaves identically on every device.
 */
@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    val scrim = Color.Black.copy(alpha = 0.55f)
    val bracket = Color(0xFF38BDF8)

    Canvas(modifier) {
        val side = min(size.width, size.height) * 0.68f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f

        drawRect(scrim, Offset.Zero, Size(size.width, top))
        drawRect(scrim, Offset(0f, top + side), Size(size.width, size.height - top - side))
        drawRect(scrim, Offset(0f, top), Size(left, side))
        drawRect(scrim, Offset(left + side, top), Size(size.width - left - side, side))

        val arm = side * 0.14f
        val stroke = 5f
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(bracket, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
            drawLine(bracket, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
        }
        corner(left, top, 1f, 1f)
        corner(left + side, top, -1f, 1f)
        corner(left, top + side, 1f, -1f)
        corner(left + side, top + side, -1f, -1f)
    }
}
