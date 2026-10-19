package org.plainly.app

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.plainly.app.ml.WarningModelLoader
import org.plainly.app.viewmodel.PlainlyViewModel
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlainlyViewModelIntegrationTest {
    @Test
    fun bundledWarningModelLoadsAndFlagsReviewSignal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNotNull(WarningModelLoader.load(context))
        org.junit.Assert.assertTrue(
            WarningModelLoader.load(context).predictsWarning("Your account is suspended. Click here to verify it.")
        )
    }

    @Test
    fun providerTextFlowsThroughViewModelIntoAnalysis() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val source = File(application.cacheDir, "viewmodel-integration.txt").apply {
            writeText("Please respond by October 20, 2026.")
        }
        val uri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", source)
        val viewModel = PlainlyViewModel(application)
        try {
            viewModel.process(uri)
            val state = viewModel.state.filter { it.analysis != null || it.error != null }.first()
            assertNull(state.error)
            assertNotNull(state.analysis)
            assertNotNull(state.analysis?.deadlines?.singleOrNull())
        } finally {
            source.delete()
            viewModel.clearCurrentDocument()
        }
    }
}
