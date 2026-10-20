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
        val vec = FloatArray(128) { (it + 1).toFloat() }
        val distance = faceNetEngine.calculateDistance(vec, vec)
        assertEquals(0.0f, distance, 0.001f)
    }

    @Test
    fun testCosineDistanceMatchingThreshold() {
        val vec1 = FloatArray(128) { 1.0f }
        val similarVec = FloatArray(128) { 1.01f }
        assertTrue(faceNetEngine.match(vec1, similarVec))

        val distinctVec = FloatArray(128) { if (it % 2 == 0) 1.0f else -1.0f }
        assertFalse(faceNetEngine.match(vec1, distinctVec))
    }
}
