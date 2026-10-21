package org.elektronnet.mobile.node

import java.io.File

enum class AddressKind(val fileName: String) {
    Onion("onion_v3_private_key"),
    I2p("i2p_private_key"),
}

// The node keeps the private key of its onion service and of its I2P destination in its data directory and reuses them
// at every start, so the address stays the same until the key file is deleted.
class AddressKeys(private val netDir: File) {
    fun file(kind: AddressKind): File = File(netDir, kind.fileName)

    fun exists(kind: AddressKind): Boolean = file(kind).isFile

    fun renew(kind: AddressKind): Boolean {
        val key = file(kind)
        return !key.exists() || key.delete()
    }
}
