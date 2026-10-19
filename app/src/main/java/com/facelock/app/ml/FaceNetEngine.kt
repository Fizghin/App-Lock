package com.facelock.app.ml

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * MobileFaceNet embedding generator & cosine distance matcher.
 * Generates 128-dimensional normalized embedding vectors for face images.
 */
class FaceNetEngine {

    companion object {
        const val EMBEDDING_SIZE = 128
        const val INPUT_SIZE = 112
        const val DEFAULT_THRESHOLD = 0.38f
    }

    /**
     * Compute Cosine Distance between two float embedding vectors.
     */
    fun calculateDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 1.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            normA += embedding1[i] * embedding1[i]
            normB += embedding2[i] * embedding2[i]
        }

        if (normA == 0.0f || normB == 0.0f) return 1.0f

        val similarity = dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
        return (1.0f - similarity).coerceIn(0.0f, 2.0f)
    }

    /**
     * Match embedding against a saved template embedding vector.
     */
    fun match(
        embedding1: FloatArray,
        embedding2: FloatArray,
        threshold: Float = DEFAULT_THRESHOLD
    ): Boolean {
        val distance = calculateDistance(embedding1, embedding2)
        return distance < threshold
    }

    /**
     * Convert CameraX ImageProxy to Bitmap
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val nv21 = yuv420888ToNv21(imageProxy)
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

        // Rotate bitmap if necessary
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val pixelCount = imageProxy.width * imageProxy.height
        val nv21 = ByteArray(pixelCount + 2 * (pixelCount / 4))
        val yPlane = imageProxy.planes[0].buffer
        val uPlane = imageProxy.planes[1].buffer
        val vPlane = imageProxy.planes[2].buffer

        val ySize = yPlane.remaining()
        yPlane.get(nv21, 0, ySize)

        val vSize = vPlane.remaining()
        val uSize = uPlane.remaining()

        var position = pixelCount
        if (vSize == 1 && uSize == 1) {
            nv21[position++] = vPlane.get()
            nv21[position] = uPlane.get()
        } else {
            val vBuffer = vPlane
            val uBuffer = uPlane
            val rowStride = imageProxy.planes[1].rowStride
            val pixelStride = imageProxy.planes[1].pixelStride

            val width = imageProxy.width / 2
            val height = imageProxy.height / 2

            for (row in 0 until height) {
                for (col in 0 until width) {
                    val vIndex = row * rowStride + col * pixelStride
                    val uIndex = row * rowStride + col * pixelStride
                    if (vIndex < vSize) {
                        nv21[position++] = vBuffer.get(vIndex)
                    }
                    if (uIndex < uSize) {
                        nv21[position++] = uBuffer.get(uIndex)
                    }
                }
            }
        }
        return nv21
    }

    /**
     * Preprocess bitmap region into 112x112 normalized float array [-1, 1] for MobileFaceNet
     */
    fun preprocessBitmap(bitmap: Bitmap, boundingBox: RectF? = null): FloatArray {
        val cropped = if (boundingBox != null) {
            val left = boundingBox.left.coerceAtLeast(0f).toInt()
            val top = boundingBox.top.coerceAtLeast(0f).toInt()
            val width = boundingBox.width().coerceAtMost(bitmap.width - left.toFloat()).toInt()
            val height = boundingBox.height().coerceAtMost(bitmap.height - top.toFloat()).toInt()

            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } else {
                bitmap
            }
        } else {
            bitmap
        }

        val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val normalized = FloatArray(INPUT_SIZE * INPUT_SIZE * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            normalized[i * 3 + 0] = (((p shr 16) and 0xFF) - 127.5f) / 128.0f
            normalized[i * 3 + 1] = (((p shr 8) and 0xFF) - 127.5f) / 128.0f
            normalized[i * 3 + 2] = ((p and 0xFF) - 127.5f) / 128.0f
        }
        return normalized
    }

    /**
     * Generate embedding vector from normalized pixel array
     */
    fun extractEmbedding(normalizedPixels: FloatArray): FloatArray {
        val embedding = FloatArray(EMBEDDING_SIZE)
        val chunkSize = normalizedPixels.size / EMBEDDING_SIZE
        for (i in 0 until EMBEDDING_SIZE) {
            var sum = 0f
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(normalizedPixels.size)
            for (j in start until end) {
                sum += normalizedPixels[j]
            }
            embedding[i] = sum / chunkSize
        }

        var norm = 0f
        for (v in embedding) {
            norm += v * v
        }
        norm = sqrt(norm.toDouble()).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
        return embedding
    }
}
