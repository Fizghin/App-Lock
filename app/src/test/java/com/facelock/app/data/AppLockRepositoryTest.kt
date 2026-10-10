package com.facelock.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLockRepositoryTest {

    private lateinit var repository: AppLockRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = AppLockRepository(context)
    }

    @Test
    fun testAppLocking() {
        val pkg = "com.example.targetapp"
        assertFalse(repository.isAppLocked(pkg))

        repository.setAppLocked(pkg, true)
        assertTrue(repository.isAppLocked(pkg))

        repository.setAppLocked(pkg, false)
        assertFalse(repository.isAppLocked(pkg))
    }

    @Test
    fun testFaceEmbeddingStorage() {
        assertFalse(repository.isFaceEnrolled())
        val dummyEmbedding = FloatArray(128) { it.toFloat() }

        repository.saveFaceEmbedding(dummyEmbedding)
        assertTrue(repository.isFaceEnrolled())

        val retrieved = repository.getFaceEmbedding()
        assertNotNull(retrieved)
        assertEquals(128, retrieved!!.size)
        assertEquals(0.0f, retrieved[0], 0.001f)

        repository.deleteFaceTemplate()
        assertFalse(repository.isFaceEnrolled())
    }
}
