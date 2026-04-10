package ca.senseway.pathpaldemo

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

// wraps a tflite yolov8 model — handles transposed output [1, features, anchors]
class YoloDetector(
    private val context: Context,
    private val modelPath: String,
    private val labels: List<String> = listOf("crosswalk"),
    private val confidenceThreshold: Float = 0.45f
) {
    private var interpreter: Interpreter? = null
    private var inputImageWidth  = 640
    private var inputImageHeight = 640
    private val iouThreshold = 0.45f   // tighter NMS — removes more overlapping duplicates

    init {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            // GPU delegate would help but requires extra dep — CPU with 4 threads is solid
        }
        interpreter = Interpreter(model, options)

        val inputShape = interpreter!!.getInputTensor(0).shape()
        // shape is [1, H, W, C] — index 1 = height, 2 = width
        inputImageHeight = inputShape[1]
        inputImageWidth  = inputShape[2]
    }

    fun detect(bitmap: Bitmap): List<BoundingBox> {
        val interp = interpreter ?: return emptyList()

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()
        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputShape  = interp.getOutputTensor(0).shape()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)
        interp.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        val data       = outputBuffer.floatArray
        val numAnchors = outputShape[2]
        val numFeatures= outputShape[1]
        val numClasses = numFeatures - 4
        val boxes      = mutableListOf<BoundingBox>()

        for (i in 0 until numAnchors) {
            var bestScore    = 0f
            var bestClassIdx = 0
            for (c in 0 until numClasses) {
                val score = data[((4 + c) * numAnchors) + i]
                if (score > bestScore) { bestScore = score; bestClassIdx = c }
            }
            if (bestScore < confidenceThreshold) continue

            val cx = data[(0 * numAnchors) + i]
            val cy = data[(1 * numAnchors) + i]
            val w  = data[(2 * numAnchors) + i]
            val h  = data[(3 * numAnchors) + i]

            // clamp to bitmap bounds
            val x1 = ((cx - w / 2f) * bitmap.width).coerceIn(0f, bitmap.width.toFloat())
            val y1 = ((cy - h / 2f) * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
            val x2 = ((cx + w / 2f) * bitmap.width).coerceIn(0f, bitmap.width.toFloat())
            val y2 = ((cy + h / 2f) * bitmap.height).coerceIn(0f, bitmap.height.toFloat())

            // reject degenerate boxes
            if (x2 - x1 < 4f || y2 - y1 < 4f) continue

            val label = labels.getOrElse(bestClassIdx) { "class_$bestClassIdx" }
            boxes.add(BoundingBox(x1, y1, x2, y2, bestScore, label, bestClassIdx))
        }

        val nmsResults = applyNms(boxes)

        // zebra stripe filter — only for crosswalk detections, validates stripe pattern
        return nmsResults.filter { box ->
            if (box.label != "crosswalk") true
            else hasZebraStripes(bitmap, box)
        }
    }

    /**
     * Validates that a detected box contains a real zebra crossing.
     * Samples both horizontal AND vertical/diagonal scanlines so angled
     * crosswalks are also accepted. A real crosswalk must show alternating
     * bright/dark bands AND maintain a meaningful white-to-total ratio.
     */
    private fun hasZebraStripes(bitmap: Bitmap, box: BoundingBox): Boolean {
        val x1 = box.x1.toInt().coerceIn(0, bitmap.width  - 1)
        val y1 = box.y1.toInt().coerceIn(0, bitmap.height - 1)
        val x2 = box.x2.toInt().coerceIn(0, bitmap.width  - 1)
        val y2 = box.y2.toInt().coerceIn(0, bitmap.height - 1)
        val boxW = x2 - x1
        val boxH = y2 - y1
        if (boxW < 12 || boxH < 12) return false

        val hStep = (boxW / 20).coerceAtLeast(1)
        val vStep = (boxH / 20).coerceAtLeast(1)
        var totalTransitions = 0
        var brightPixels     = 0
        var totalPixels      = 0

        // ── horizontal scanlines (7 lines through the box height) ────────────
        for (si in 1..7) {
            val py = y1 + (si * boxH / 8)
            var prevBright = false
            var lineT = 0
            var first = true
            for (px in x1 until x2 step hStep) {
                val lum = luminance(bitmap.getPixel(px, py))
                val bright = lum > 128
                if (bright) brightPixels++
                totalPixels++
                if (!first && bright != prevBright) lineT++
                prevBright = bright
                first = false
            }
            totalTransitions += lineT
        }

        // ── vertical scanlines (5 lines through the box width) ────────────
        // catches stripes that run top-to-bottom (standard crosswalk orientation)
        for (si in 1..5) {
            val px = x1 + (si * boxW / 6)
            var prevBright = false
            var lineT = 0
            var first = true
            for (py in y1 until y2 step vStep) {
                val lum = luminance(bitmap.getPixel(px, py))
                val bright = lum > 128
                if (bright) brightPixels++
                totalPixels++
                if (!first && bright != prevBright) lineT++
                prevBright = bright
                first = false
            }
            totalTransitions += lineT
        }

        // ── diagonal scanline (top-left → bottom-right) ──────────────────
        // catches 45-degree crosswalk shots from a perspective angle
        val diagSteps = minOf(boxW, boxH) / hStep
        if (diagSteps > 2) {
            var prevBright = false
            var lineT = 0
            var first = true
            for (step in 0 until diagSteps) {
                val px = (x1 + step * boxW / diagSteps).coerceIn(x1, x2)
                val py = (y1 + step * boxH / diagSteps).coerceIn(y1, y2)
                val lum = luminance(bitmap.getPixel(px, py))
                val bright = lum > 128
                if (bright) brightPixels++
                totalPixels++
                if (!first && bright != prevBright) lineT++
                prevBright = bright
                first = false
            }
            totalTransitions += lineT
        }

        // normalise: transitions per scanline, across all 13+ lines sampled
        val numLines    = 13.0
        val avgT        = totalTransitions.toDouble() / numLines
        val brightRatio = brightPixels.toDouble() / totalPixels.coerceAtLeast(1)

        // needs at least 2 stripe alternations avg AND a plausible white/dark mix
        return avgT >= 2.0 && brightRatio in 0.10..0.85
    }

    private fun luminance(pixel: Int): Int =
        (0.299 * ((pixel shr 16) and 0xFF) +
         0.587 * ((pixel shr 8)  and 0xFF) +
         0.114 * (pixel          and 0xFF)).toInt()

    private fun applyNms(boxes: List<BoundingBox>): List<BoundingBox> {
        val sorted   = boxes.sortedByDescending { it.score }.toMutableList()
        val selected = mutableListOf<BoundingBox>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)
            sorted.removeAll { calculateIou(best, it) >= iouThreshold }
        }
        return selected
    }

    private fun calculateIou(a: BoundingBox, b: BoundingBox): Float {
        val ix1    = maxOf(a.x1, b.x1);  val iy1 = maxOf(a.y1, b.y1)
        val ix2    = minOf(a.x2, b.x2);  val iy2 = minOf(a.y2, b.y2)
        val inter  = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
        val union  = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    data class BoundingBox(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val score: Float, val label: String, val classId: Int = 0
    )
}
