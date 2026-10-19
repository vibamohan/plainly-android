package org.plainly.app.ml

import android.content.Context
import org.plainly.app.analysis.LearnedWarningModel

/** Android-only asset boundary for the optional offline learned warning model. */
object WarningModelLoader {
    fun load(context: Context): LearnedWarningModel =
        context.assets.open("models/warning_model.tsv").bufferedReader().use(LearnedWarningModel::fromTsv)
}
