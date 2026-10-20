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
        val vec = FloatArray(16) { (it + 1).toFloat() }
        val distance = faceNetEngine.calculateDistance(vec, vec)
        assertEquals(0.0f, distance, 0.001f)
    }

    @Test
    fun testMatchingThresholdStrictness() {
        val vec1 = FloatArray(16) { 1.0f }
        val similarVec = FloatArray(16) { 1.02f }
        assertTrue(faceNetEngine.match(vec1, similarVec))

        val distinctVec = FloatArray(16) { 2.5f }
        assertFalse(faceNetEngine.match(vec1, distinctVec))
    }
}
