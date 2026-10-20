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
        val vec = FloatArray(128) { (it % 5 - 2).toFloat() }
        val distance = faceNetEngine.calculateDistance(vec, vec)
        assertEquals(0.0f, distance, 0.001f)
    }

    @Test
    fun testCosineDistanceMatchingThreshold() {
        val vec1 = FloatArray(128) { if (it < 64) 0.1f else -0.1f }
        val similarVec = FloatArray(128) { if (it < 64) 0.09f else -0.09f }
        assertTrue(faceNetEngine.match(vec1, similarVec))

        val distinctVec = FloatArray(128) { if (it < 64) -0.1f else 0.1f }
        assertFalse(faceNetEngine.match(vec1, distinctVec))
    }
}
