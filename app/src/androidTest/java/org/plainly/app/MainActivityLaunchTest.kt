package org.plainly.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.Manifest
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun activityLaunchesToResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun internetPermissionIsDeclaredForModelAcquisition() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName)
        )
    }
}
