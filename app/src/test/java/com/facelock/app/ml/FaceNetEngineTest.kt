package com.facelock.app.ml

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceNetEngineTest {

    private lateinit var faceNetEngine: FaceNetEngine

    @Before
    fun setUp() {
        faceNetEngine = FaceNetEngine()
    }

    @Test
    fun testIdenticalVectorsDistanceIsZero() {
        val vec = FloatArray(128) { i -> (i % 10).toFloat() }
        val distance = faceNetEngine.calculateDistance(vec, vec)
        assertEquals(0.0f, distance, 0.001f)
    }

    @Test
    fun testMatchingThreshold() {
        val vec1 = FloatArray(128) { 1.0f }
        val vec2 = FloatArray(128) { 0.95f }
        assertTrue(faceNetEngine.match(vec1, vec2))

        val orthogonalVec = FloatArray(128) { if (it % 2 == 0) 1.0f else -1.0f }
        assertFalse(faceNetEngine.match(vec1, orthogonalVec))
    }

    @Test
    fun testExtractEmbeddingOutputSize() {
        val dummyPixels = FloatArray(112 * 112 * 3) { 0.5f }
        val embedding = faceNetEngine.extractEmbedding(dummyPixels)
        assertEquals(128, embedding.size)
    }
}
