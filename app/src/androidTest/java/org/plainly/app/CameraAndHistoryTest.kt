package org.plainly.app

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.plainly.app.history.HistoryEntry
import org.plainly.app.history.PlainlyDatabase
import org.plainly.app.ocr.CameraImageProcessor
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CameraAndHistoryTest {
    @Test
    fun cameraNormalizationAppliesRotationAndBoundedCrop() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "instrumentation-camera.jpg")
        try {
            FileOutputStream(source).use { output ->
                Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.WHITE)
                    compress(Bitmap.CompressFormat.JPEG, 95, output)
                    recycle()
                }
            }
            val normalized = CameraImageProcessor.normalize(context, source, additionalRotationDegrees = 90, cropPercent = 20)
            try {
                val decoded = android.graphics.BitmapFactory.decodeFile(normalized.absolutePath)
                assertEquals(30, decoded.width)
                assertEquals(60, decoded.height)
                decoded.recycle()
            } finally {
                normalized.delete()
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun historyStoresOnlyDeclaredMetadataAndCanBeDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, PlainlyDatabase::class.java).build()
        try {
            val entry = HistoryEntry(displayName = "notice.pdf", sourceUri = "content://provider/1", summary = "Review the deadline")
            database.historyDao().insert(entry)
            val saved = database.historyDao().observeAll().first()
            assertEquals(1, saved.size)
            assertEquals("content://provider/1", saved.single().sourceUri)
            assertEquals("Review the deadline", saved.single().summary)
            database.historyDao().delete(saved.single().id)
            assertTrue(database.historyDao().observeAll().first().isEmpty())
        } finally {
            database.close()
        }
    }
}
