package org.elektronnet.mobile.service

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

object SigningInfo {
    fun fingerprint(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
