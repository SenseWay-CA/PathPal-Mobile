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

class YoloDetector(
    private val context: Context,
    private val modelPath: String,
    private val label: String = "crosswalk"
) {
    private var interpreter: Interpreter? = null
    private var inputImageWidth = 0
    private var inputImageHeight = 0
    private val confidenceThreshold = 0.5f
    private val iouThreshold = 0.5f

    init {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options()
        interpreter = Interpreter(model, options)

        val inputShape = interpreter!!.getInputTensor(0).shape()
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]
    }

    fun detect(bitmap: Bitmap): List<BoundingBox> {
        if (interpreter == null) return emptyList()

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageWidth, inputImageHeight, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter!!.getOutputTensor(0).shape()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)

        interpreter!!.run(tensorImage.buffer, outputBuffer.buffer)

        val outputData = outputBuffer.floatArray
        val boxes = ArrayList<BoundingBox>()
        val numCandidates = outputShape[2]

        for (i in 0 until numCandidates) {
            val scoreIndex = (4 * numCandidates) + i
            val score = outputData[scoreIndex]

            if (score > confidenceThreshold) {
                val cx = outputData[(0 * numCandidates) + i]
                val cy = outputData[(1 * numCandidates) + i]
                val w = outputData[(2 * numCandidates) + i]
                val h = outputData[(3 * numCandidates) + i]

                val x1 = (cx - w / 2) * bitmap.width
                val y1 = (cy - h / 2) * bitmap.height
                val x2 = (cx + w / 2) * bitmap.width
                val y2 = (cy + h / 2) * bitmap.height

                boxes.add(BoundingBox(x1, y1, x2, y2, score, label))
            }
        }
        return applyNMS(boxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.score }.toMutableList()
        val selectedBoxes = ArrayList<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes[0]
            selectedBoxes.add(first)
            sortedBoxes.removeAt(0)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                if (calculateIoU(first, nextBox) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    data class BoundingBox(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val score: Float, val label: String
    )
}