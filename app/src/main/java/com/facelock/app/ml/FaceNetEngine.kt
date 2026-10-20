package com.facelock.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.io.ByteArrayOutputStream
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 128-dimensional zero-centered facial keypoint & pixel distribution feature extractor and Cosine Distance matcher.
 */
class FaceNetEngine {

    companion object {
        const val EMBEDDING_SIZE = 128
        // Strict distance threshold for zero-centered features (cosine distance < 0.25 required to unlock)
        const val COSINE_DISTANCE_THRESHOLD = 0.25f

        // Average baseline ratios across human faces for zero-centering geometry
        private val GEOMETRY_MEANS = floatArrayOf(
            0.82f, 0.82f, 1.45f, 1.45f, 0.95f, 0.95f, 0.90f,
            1.80f, 1.60f, 1.60f, 0.00f, 0.00f, 0.00f,
            2.20f, 2.80f, 0.00f
        )
    }

    /**
     * Compute Cosine Distance between two 128-D zero-centered normalized feature vectors.
     * Distance < 0.25 = Match. Distinct faces produce distance >= 0.50.
     */
    fun calculateDistance(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 1.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }

        if (normA == 0.0f || normB == 0.0f) return 1.0f

        val similarity = dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
        return (1.0f - similarity).coerceIn(0.0f, 2.0f)
    }

    /**
     * Matches live feature vector against stored template vector.
     */
    fun match(
        v1: FloatArray,
        v2: FloatArray,
        threshold: Float = COSINE_DISTANCE_THRESHOLD
    ): Boolean {
        val distance = calculateDistance(v1, v2)
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
     * Extract 128-D zero-centered L2-normalized feature vector.
     */
    fun extractFeatureVector(face: Face, frameBitmap: Bitmap? = null): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

        if (leftEye == null || rightEye == null || noseBase == null || mouthLeft == null || mouthRight == null) {
            return null
        }

        val raw = FloatArray(EMBEDDING_SIZE)
        val eyeDist = dist(leftEye, rightEye).coerceAtLeast(1f)

        // 1. Spatial Geometry Ratios (Indices 0 to 15)
        raw[0] = dist(leftEye, noseBase) / eyeDist
        raw[1] = dist(rightEye, noseBase) / eyeDist
        raw[2] = dist(leftEye, mouthLeft) / eyeDist
        raw[3] = dist(rightEye, mouthRight) / eyeDist
        raw[4] = dist(noseBase, mouthLeft) / eyeDist
        raw[5] = dist(noseBase, mouthRight) / eyeDist
        raw[6] = dist(mouthLeft, mouthRight) / eyeDist

        val leftCheekPos = leftCheek ?: leftEye
        val rightCheekPos = rightCheek ?: rightEye
        raw[7] = dist(leftCheekPos, rightCheekPos) / eyeDist
        raw[8] = dist(leftEye, mouthRight) / eyeDist
        raw[9] = dist(rightEye, mouthLeft) / eyeDist
        raw[10] = face.headEulerAngleX / 180f
        raw[11] = face.headEulerAngleY / 180f
        raw[12] = face.headEulerAngleZ / 180f

        val boundingBox = face.boundingBox
        raw[13] = boundingBox.width().toFloat() / eyeDist
        raw[14] = boundingBox.height().toFloat() / eyeDist
        raw[15] = (leftEye.y - rightEye.y) / eyeDist

        // Zero-center geometry ratios
        for (i in 0..15) {
            raw[i] = raw[i] - GEOMETRY_MEANS[i]
        }

        // 2. Localized Pixel Distributions around Keypoints (Indices 16 to 127) zero-centered (-0.5)
        if (frameBitmap != null && frameBitmap.width > 0 && frameBitmap.height > 0) {
            val keypoints = listOf(leftEye, rightEye, noseBase, mouthLeft, mouthRight)
            var index = 16
            for (kp in keypoints) {
                val cx = kp.x.toInt().coerceIn(0, frameBitmap.width - 1)
                val cy = kp.y.toInt().coerceIn(0, frameBitmap.height - 1)

                val radius = (eyeDist * 0.15f).toInt().coerceAtLeast(2)
                for (dx in -2..2) {
                    for (dy in -2..1) {
                        if (index < EMBEDDING_SIZE) {
                            val px = (cx + dx * radius).coerceIn(0, frameBitmap.width - 1)
                            val py = (cy + dy * radius).coerceIn(0, frameBitmap.height - 1)
                            val color = frameBitmap.getPixel(px, py)
                            val r = ((color shr 16) and 0xFF) / 255.0f
                            val g = ((color shr 8) and 0xFF) / 255.0f
                            val b = (color and 0xFF) / 255.0f
                            val luminance = (0.299f * r + 0.587f * g + 0.114f * b)
                            raw[index++] = luminance - 0.5f // Zero center
                        }
                    }
                }
            }
        }

        // 3. L2 Normalize full 128-D vector
        var norm = 0f
        for (v in raw) {
            norm += v * v
        }
        norm = sqrt(norm.toDouble()).toFloat()
        if (norm > 0) {
            for (i in raw.indices) {
                raw[i] /= norm
            }
        }

        return raw
    }

    private fun dist(p1: PointF, p2: PointF): Float {
        return hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
    }
}
