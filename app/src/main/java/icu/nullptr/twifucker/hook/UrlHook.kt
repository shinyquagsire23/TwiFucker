package icu.nullptr.twifucker.hook

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.github.kyuubiran.ezxhelper.utils.*

private fun String.isTwitterUrl(): Boolean {
    return this.startsWith("https://twitter.com/")
}

private fun String.hasExtraParam(): Boolean {
    return this.startsWith("t") || this.startsWith("s")
}

private fun clearExtraParams(url: String): String {
    val oldUri = Uri.parse(url)
    val newUri = oldUri.buildUpon().clearQuery()
    oldUri.queryParameterNames.forEach {
        if (it.hasExtraParam()) {
            return@forEach
        }
        newUri.appendQueryParameter(it, oldUri.getQueryParameter(it))
    }
    return newUri.build().toString()
}

fun urlHook() {
    findAllMethods(Intent::class.java) { name == "replaceExtras" }.hookBefore { param ->
        if (param != null && param.args[0] != null) {
            val bundle = param.args[0] as Bundle
            val extraText = bundle.getString(Intent.EXTRA_TEXT)
            if (extraText != null && extraText.isTwitterUrl()) {
                val newExtraText = clearExtraParams(extraText)
                bundle.putString(Intent.EXTRA_TEXT, newExtraText)
            }
        }
    }
    findAllMethods(Intent::class.java) { name == "createChooser" }.hookBefore { param ->
        if (param != null && param.args[0] != null) {
            val intent = param.args[0] as Intent
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return@hookBefore
            if (!extraText.isTwitterUrl()) {
                return@hookBefore
            }
            Log.i("Handle Url")
            intent.putExtra(Intent.EXTRA_TEXT, clearExtraParams(extraText))
        }
    }
    findAllMethods(ClipData::class.java) { name == "newPlainText" }.hookBefore { param ->
        if (param != null && param.args[1] != null) {
            val text = (param.args[1] as CharSequence).toString()
            if (!text.isTwitterUrl()) {
                return@hookBefore
            }
            Log.i("Handle Url")
            param.args[1] = clearExtraParams(text)
        }
    }
}
