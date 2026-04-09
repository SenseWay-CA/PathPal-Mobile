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
    private val labels: List<String> = listOf("crosswalk")
) {
    private var interpreter: Interpreter? = null
    private var inputImageWidth  = 640
    private var inputImageHeight = 640
    private val confidenceThreshold = 0.45f  // safe threshold for production
    private val iouThreshold        = 0.50f

    init {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(model, options)

        val inputShape = interpreter!!.getInputTensor(0).shape()
        // shape is [1, H, W, C] — index 1 = height, 2 = width
        inputImageHeight = inputShape[1]
        inputImageWidth  = inputShape[2]
    }

    fun detect(bitmap: Bitmap): List<BoundingBox> {
        val interp = interpreter ?: return emptyList()

        // preprocess: resize + normalize 0-255 → 0-1
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

        val data          = outputBuffer.floatArray
        val numAnchors    = outputShape[2]   // 8400 for yolov8n
        val numFeatures   = outputShape[1]   // 4 bbox + num_classes
        val numClasses    = numFeatures - 4
        val boxes         = mutableListOf<BoundingBox>()

        for (i in 0 until numAnchors) {
            // find best class score across all class channels
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

            val x1 = (cx - w / 2f) * bitmap.width
            val y1 = (cy - h / 2f) * bitmap.height
            val x2 = (cx + w / 2f) * bitmap.width
            val y2 = (cy + h / 2f) * bitmap.height

            val label = labels.getOrElse(bestClassIdx) { "class_$bestClassIdx" }
            boxes.add(BoundingBox(x1, y1, x2, y2, bestScore, label, bestClassIdx))
        }
        return applyNms(boxes)
    }

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
