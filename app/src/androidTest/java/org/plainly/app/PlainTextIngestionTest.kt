package org.plainly.app

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.plainly.app.ingestion.OnDeviceDocumentIngestion
import org.plainly.app.ingestion.PlainTextExtractor

@RunWith(AndroidJUnit4::class)
class PlainTextIngestionTest {
    @Test
    fun providerUriIsReadLocallyAndMappedToPageOne() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "instrumentation-plainly.txt").apply {
            writeText("Notice\n\nRespond by October 20, 2026")
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
            val evidence = PlainTextExtractor.extract(context, uri)
            assertEquals(listOf("Notice", "Respond by October 20, 2026"), evidence.map { it.text })
            assertTrue(evidence.all { it.page == 1 })
        } finally {
            source.delete()
        }
    }

    @Test
    fun jsonExtensionUsesLocalTextRoute() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "instrumentation-plainly.json").apply {
            writeText("{\"notice\":\"Respond by October 20\"}")
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
            val evidence = OnDeviceDocumentIngestion.extract(context, uri)
            assertEquals(listOf("{\"notice\":\"Respond by October 20\"}"), evidence.map { it.text })
        } finally {
            source.delete()
        }
    }
}
