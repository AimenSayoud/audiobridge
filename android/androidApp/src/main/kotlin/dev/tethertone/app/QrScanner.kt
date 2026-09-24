package dev.tethertone.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.tethertone.shared.Pairing
import dev.tethertone.shared.PairingInfo
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "TethertoneScanner"

/**
 * Camera preview that reports the first frame containing a payload
 * [Pairing.parse] accepts. Anything else — a wifi QR, a URL, a product barcode —
 * decodes fine and is ignored, which is why parsing happens here and not after.
 */
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    torchOn: Boolean = false,
    onTorchAvailable: (Boolean) -> Unit = {},
    onPaired: (PairingInfo) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPaired by rememberUpdatedState(onPaired)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val cameraRef = remember { mutableStateOf<Camera?>(null) }

    // Torch state is applied outside the binding block so toggling it does not
    // rebind the camera, which would blank the preview every tap.
    LaunchedEffect(torchOn, cameraRef.value) {
        cameraRef.value?.cameraControl?.enableTorch(torchOn)
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor, QrAnalyzer { text ->
                            val info = Pairing.parse(text) ?: return@QrAnalyzer
                            if (delivered.compareAndSet(false, true)) {
                                previewView.post { currentOnPaired(info) }
                            }
                        })

                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                        )
                        cameraRef.value = camera
                        onTorchAvailable(camera.cameraInfo.hasFlashUnit())
                    }.onFailure { Log.e(TAG, "camera bind failed", it) }
                }, mainExecutor(ctx))
                previewView
            },
        )
    }
}

private fun mainExecutor(context: Context) = androidx.core.content.ContextCompat.getMainExecutor(context)

private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        try {
            val luminance = image.yPlane() ?: return
            val source = PlanarYUVLuminanceSource(
                luminance, image.width, image.height, 0, 0, image.width, image.height, false
            )
            val result = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: NotFoundException) {
                null
            } finally {
                reader.reset()
            }
            result?.text?.let(onText)
        } catch (e: Exception) {
            Log.w(TAG, "frame decode failed", e)
        } finally {
            image.close()
        }
    }
}

/**
 * The Y plane is already a luminance bitmap, which is exactly what ZXing wants —
 * but `rowStride` is frequently wider than the image, so it cannot be handed
 * over as-is without shearing the picture and failing every decode.
 */
private fun ImageProxy.yPlane(): ByteArray? {
    if (format != android.graphics.ImageFormat.YUV_420_888 && planes.isEmpty()) return null
    val plane = planes.getOrNull(0) ?: return null
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val out = ByteArray(width * height)

    if (rowStride == width && pixelStride == 1) {
        buffer.get(out, 0, minOf(buffer.remaining(), out.size))
        return out
    }

    val row = ByteArray(rowStride)
    var offset = 0
    for (y in 0 until height) {
        val toRead = minOf(rowStride, buffer.remaining())
        if (toRead <= 0) break
        buffer.get(row, 0, toRead)
        if (pixelStride == 1) {
            row.copyInto(out, offset, 0, minOf(width, toRead))
        } else {
            var x = 0
            var i = 0
            while (x < width && i < toRead) {
                out[offset + x] = row[i]
                x++
                i += pixelStride
            }
        }
        offset += width
    }
    return out
}
