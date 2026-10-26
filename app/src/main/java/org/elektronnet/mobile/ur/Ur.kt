package org.elektronnet.mobile.ur

class Ur(val type: String, val cbor: ByteArray) {
    init {
        require(isType(type)) { "Invalid UR type" }
    }

    // the payload of types that wrap one byte string, such as crypto-psbt
    fun bytes(): ByteArray = (Cbor.decode(cbor) as? Cbor.Bytes)?.value ?: throw IllegalArgumentException("Not a byte string")

    companion object {
        const val PSBT = "crypto-psbt"

        fun isType(type: String) = type.isNotEmpty() && type.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

        fun ofBytes(type: String, data: ByteArray) = Ur(type, Cbor.bytes(data))
    }
}

// turns a UR into the strings of an animated QR code, a short one is a single string and a long one a never ending stream
class UrEncoder(private val ur: Ur, maxFragmentLength: Int = 100) {
    private val fountain = FountainEncoder(ur.cbor, maxFragmentLength)

    val isSinglePart: Boolean get() = fountain.isSinglePart
    val partCount: Int get() = fountain.seqLength

    fun nextPart(): String {
        val part = fountain.nextPart()
        val type = ur.type
        return if (fountain.isSinglePart) "ur:$type/${Bytewords.encodeMinimal(ur.cbor)}"
        else "ur:$type/${part.seqNum}-${part.seqLength}/${Bytewords.encodeMinimal(part.cbor())}"
    }
}

class UrDecoder {
    private val fountain = FountainDecoder()
    private var type: String? = null
    var result: Ur? = null
        private set
    var error: String? = null
        private set

    val isComplete: Boolean get() = result != null
    val progress: Double get() = if (isComplete) 1.0 else fountain.progress
    val expectedParts: Int get() = fountain.expectedParts

    // one string read from a QR code, returns false when it was not accepted, never throws
    fun receive(text: String): Boolean {
        if (isComplete) return false
        return try {
            val lower = text.trim().lowercase()
            require(lower.startsWith("ur:")) { "Not a UR" }
            val parts = lower.substring(3).split('/')
            require(parts.size in 2..3) { "Invalid UR" }
            val kind = parts[0]
            require(Ur.isType(kind)) { "Invalid UR type" }
            if (type != null && type != kind) return false
            type = kind
            if (parts.size == 2) {
                result = Ur(kind, Bytewords.decodeMinimal(parts[1]))
                true
            } else {
                val seq = parts[1].split('-')
                require(seq.size == 2) { "Invalid sequence" }
                val seqNum = seq[0].toLong()
                val seqLength = seq[1].toInt()
                require(seqNum >= 1 && seqLength >= 1) { "Invalid sequence" }
                val part = FountainPart.fromCbor(Bytewords.decodeMinimal(parts[2]))
                if (part.seqNum != seqNum || part.seqLength != seqLength) return false
                val accepted = fountain.receive(part)
                fountain.result?.let { result = Ur(kind, it) }
                if (fountain.failed) error = "Checksum"
                accepted
            }
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: NumberFormatException) {
            false
        }
    }
}
