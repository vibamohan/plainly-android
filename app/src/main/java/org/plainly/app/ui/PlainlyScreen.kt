package org.plainly.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import org.plainly.app.PlainlyApp

/** UI boundary for the document assistant. Activity wiring stays outside this package. */
@Composable
fun PlainlyScreen(context: Context) {
    PlainlyApp(context)
}
