package com.facelock.app.ml

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.hypot

/**
 * Facial landmark biometrics extractor & matcher.
 * Computes a scale-invariant, rotation-normalized 16-dimensional facial ratio vector.
 */
class FaceNetEngine {

    companion object {
        const val EMBEDDING_SIZE = 16
        const val STRICT_MATCH_THRESHOLD = 0.15f
    }

    /**
     * Extracts a 16-dimensional normalized facial feature vector from ML Kit Face Landmarks
     */
    fun extractLandmarkVector(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

        if (leftEye == null || rightEye == null || noseBase == null ||
            mouthLeft == null || mouthRight == null) {
            return null
        }

        // Inter-ocular distance as normalizing scale factor
        val eyeDist = dist(leftEye, rightEye).coerceAtLeast(1f)

        // Midpoint between eyes
        val eyeMid = PointF((leftEye.x + rightEye.x) / 2f, (leftEye.y + rightEye.y) / 2f)
        // Midpoint of mouth
        val mouthMid = PointF((mouthLeft.x + mouthRight.x) / 2f, (mouthLeft.y + mouthRight.y) / 2f)

        val vector = FloatArray(EMBEDDING_SIZE)
        vector[0] = dist(leftEye, noseBase) / eyeDist
        vector[1] = dist(rightEye, noseBase) / eyeDist
        vector[2] = dist(leftEye, mouthLeft) / eyeDist
        vector[3] = dist(rightEye, mouthRight) / eyeDist
        vector[4] = dist(noseBase, mouthMid) / eyeDist
        vector[5] = dist(mouthLeft, mouthRight) / eyeDist
        vector[6] = dist(eyeMid, noseBase) / eyeDist
        vector[7] = dist(eyeMid, mouthMid) / eyeDist

        val mouthBottomPos = mouthBottom ?: mouthMid
        vector[8] = dist(noseBase, mouthBottomPos) / eyeDist
        vector[9] = dist(leftEye, mouthRight) / eyeDist
        vector[10] = dist(rightEye, mouthLeft) / eyeDist

        val leftCheekPos = leftCheek ?: leftEye
        val rightCheekPos = rightCheek ?: rightEye
        vector[11] = dist(leftCheekPos, rightCheekPos) / eyeDist
        vector[12] = dist(leftCheekPos, noseBase) / eyeDist
        vector[13] = dist(rightCheekPos, noseBase) / eyeDist
        vector[14] = dist(leftEye, mouthMid) / eyeDist
        vector[15] = dist(rightEye, mouthMid) / eyeDist

        return vector
    }

    /**
     * Compute normalized Euclidean distance between two 16-D landmark vectors
     */
    fun calculateDistance(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 1.0f
        var sumSq = 0f
        for (i in v1.indices) {
            val diff = v1[i] - v2[i]
            sumSq += diff * diff
        }
        return kotlin.math.sqrt(sumSq.toDouble()).toFloat() / v1.size.toFloat()
    }

    /**
     * Match embedding against stored template with strict thresholding
     */
    fun match(
        v1: FloatArray,
        v2: FloatArray,
        threshold: Float = STRICT_MATCH_THRESHOLD
    ): Boolean {
        val distance = calculateDistance(v1, v2)
        return distance < threshold
    }

    private fun dist(p1: PointF, p2: PointF): Float {
        return hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
    }
}
