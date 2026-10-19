package org.elektronnet.mobile.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.elektronnet.mobile.console.ConsoleHistory
import org.elektronnet.mobile.console.ConsoleRunner
import org.elektronnet.mobile.console.ConsoleSession
import org.elektronnet.mobile.console.ExportsHousekeeping
import org.elektronnet.mobile.console.FileGuard
import org.elektronnet.mobile.console.Sealer
import org.elektronnet.mobile.console.Tiers
import org.elektronnet.mobile.wallet.asWalletRpc

class KeystoreSealer(private val alias: String = "elektron_console_history") : Sealer {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        cipher.doFinal(sealed, 12, sealed.size - 12)
    }.getOrNull()
}

object ConsoleState {
    val session = ConsoleSession()
    var expertActive: Boolean by mutableStateOf(false)
        private set
    var dangerActive: Boolean by mutableStateOf(false)
        private set

    fun unlock() {
        session.unlock()
        refresh()
    }

    fun end() {
        session.end()
        refresh()
    }

    fun setDanger(on: Boolean) {
        if (on) session.enableDanger() else session.disableDanger()
        refresh()
    }

    fun touch() {
        session.touch()
        refresh()
    }

    fun touchCheck() {
        if (session.expired()) session.end()
        refresh()
    }

    private fun refresh() {
        expertActive = session.active
        dangerActive = session.active && session.dangerEnabled
    }
}

object ConsoleStore {
    private var tiers: Tiers? = null
    private var history: ConsoleHistory? = null

    fun exportsDir(context: Context): File = File(context.filesDir, "exports").apply { mkdirs() }

    @Synchronized
    fun tiers(context: Context): Tiers =
        tiers ?: Tiers.parse(context.applicationContext.assets.open("console-tiers.json").bufferedReader().readText()).also { tiers = it }

    @Synchronized
    fun history(context: Context): ConsoleHistory =
        history ?: ConsoleHistory(File(context.filesDir, "console/history.bin"), KeystoreSealer()).also { history = it }

    fun housekeeping(context: Context, retentionDays: Int) {
        history(context).prune(retentionDays)
        ExportsHousekeeping.cleanup(exportsDir(context), System.currentTimeMillis())
    }

    fun runner(context: Context, supervisorRpc: org.elektronnet.mobile.node.RpcClient, floorBytes: Long): ConsoleRunner {
        val exports = exportsDir(context)
        return ConsoleRunner(
            rpc = supervisorRpc.asWalletRpc(timeoutMs = 120_000),
            tiers = tiers(context),
            files = FileGuard(exports),
            freeBytes = { exports.usableSpace },
            storageFloorBytes = floorBytes,
        )
    }
}
