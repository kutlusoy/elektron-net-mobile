package org.elektronnet.mobile.service

import android.content.Context
import android.os.Build
import org.elektronnet.mobile.node.CoreManifest

object CoreManifestLoader {
    fun load(context: Context): CoreManifest? {
        for (abi in Build.SUPPORTED_ABIS) {
            val text = runCatching { context.assets.open("core-manifest/$abi.env").bufferedReader().use { it.readText() } }.getOrNull()
            if (text != null) return CoreManifest.parse(text)
        }
        return null
    }
}
