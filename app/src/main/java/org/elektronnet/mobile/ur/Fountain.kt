package org.elektronnet.mobile.ur

import java.nio.ByteBuffer

class FountainPart(val seqNum: Long, val seqLength: Int, val messageLength: Int, val checksum: Long, val fragment: ByteArray) {
    fun cbor(): ByteArray = Cbor.encode(
        Cbor.Array(listOf(Cbor.Uint(seqNum), Cbor.Uint(seqLength.toLong()), Cbor.Uint(messageLength.toLong()), Cbor.Uint(checksum), Cbor.Bytes(fragment))),
    )

    companion object {
        fun fromCbor(data: ByteArray): FountainPart {
            val items = (Cbor.decode(data) as? Cbor.Array)?.items ?: throw IllegalArgumentException("Invalid part")
            require(items.size == 5) { "Invalid part" }
            fun num(i: Int) = (items[i] as? Cbor.Uint)?.value ?: throw IllegalArgumentException("Invalid part")
            val fragment = (items[4] as? Cbor.Bytes)?.value ?: throw IllegalArgumentException("Invalid part")
            require(fragment.isNotEmpty() && num(1) in 1..MAX_FRAGMENTS && num(2) in 1..MAX_MESSAGE) { "Invalid part" }
            return FountainPart(num(0), num(1).toInt(), num(2).toInt(), num(3), fragment)
        }

        const val MAX_FRAGMENTS = 10_000L
        const val MAX_MESSAGE = 4L * 1024 * 1024
    }
}

internal object FountainMath {
    private fun intBytes(value: Long): ByteArray = ByteBuffer.allocate(4).putInt(value.toInt()).array()

    // Vose alias sampling over the weights 1/1 ... 1/n, fed by the generator as in the reference code
    private fun chooseDegree(seqLength: Int, rng: Xoshiro): Int {
        val n = seqLength
        val sum = (1..n).sumOf { 1.0 / it }
        val scaled = DoubleArray(n) { (1.0 / (it + 1)) * n / sum }
        val prob = DoubleArray(n)
        val alias = IntArray(n)
        val small = ArrayDeque<Int>()
        val large = ArrayDeque<Int>()
        for (i in n - 1 downTo 0) if (scaled[i] < 1) small.addLast(i) else large.addLast(i)
        while (small.isNotEmpty() && large.isNotEmpty()) {
            val less = small.removeLast()
            val more = large.removeLast()
            prob[less] = scaled[less]
            alias[less] = more
            scaled[more] = (scaled[more] + scaled[less]) - 1
            if (scaled[more] < 1) small.addLast(more) else large.addLast(more)
        }
        while (large.isNotEmpty()) prob[large.removeLast()] = 1.0
        while (small.isNotEmpty()) prob[small.removeLast()] = 1.0
        val c = Math.floor(rng.nextDouble() * n).toInt()
        val pick = if (rng.nextDouble() < prob[c]) c else alias[c]
        return pick + 1
    }

    private fun shuffle(items: List<Int>, rng: Xoshiro): List<Int> {
        val remaining = items.toMutableList()
        val result = ArrayList<Int>(items.size)
        while (remaining.isNotEmpty()) result.add(remaining.removeAt(rng.nextInt(0, remaining.size - 1)))
        return result
    }

    // the first seqLength parts carry one fragment each, later parts mix several
    fun chooseFragments(seqNum: Long, seqLength: Int, checksum: Long): List<Int> {
        if (seqNum <= seqLength) return listOf((seqNum - 1).toInt())
        val rng = Xoshiro(intBytes(seqNum) + intBytes(checksum))
        val degree = chooseDegree(seqLength, rng)
        return shuffle((0 until seqLength).toList(), rng).take(degree)
    }
}

class FountainEncoder(message: ByteArray, maxFragmentLength: Int = 100, firstSeqNum: Long = 0, minFragmentLength: Int = 10) {
    private val fragments: List<ByteArray>
    private val fragmentLength: Int
    private val messageLength = message.size
    private val checksum = Bytewords.crc32(message)
    private var seqNum = firstSeqNum

    init {
        require(message.isNotEmpty() && minFragmentLength > 0 && maxFragmentLength >= minFragmentLength)
        val maxCount = (message.size + minFragmentLength - 1) / minFragmentLength
        var length = 0
        for (count in 1..maxCount) {
            length = (message.size + count - 1) / count
            if (length <= maxFragmentLength) break
        }
        fragmentLength = length
        val count = (message.size + length - 1) / length
        fragments = List(count) { i -> ByteArray(length).also { out -> System.arraycopy(message, i * length, out, 0, minOf(length, message.size - i * length)) } }
    }

    val seqLength: Int get() = fragments.size
    val isSinglePart: Boolean get() = fragments.size == 1

    fun nextPart(): FountainPart {
        seqNum = (seqNum + 1) and 0xFFFFFFFFL
        val mixed = ByteArray(fragmentLength)
        FountainMath.chooseFragments(seqNum, fragments.size, checksum).forEach { index ->
            val fragment = fragments[index]
            for (i in mixed.indices) mixed[i] = (mixed[i].toInt() xor fragment[i].toInt()).toByte()
        }
        return FountainPart(seqNum, fragments.size, messageLength, checksum, mixed)
    }
}

class FountainDecoder {
    private class Part(val indexes: List<Int>, val fragment: ByteArray)

    var result: ByteArray? = null
        private set
    var failed = false
        private set
    private var seqLength = 0
    private var messageLength = 0
    private var checksum = 0L
    private var fragmentLength = 0
    private var processed = 0
    private val received = HashSet<Int>()
    private val simple = HashMap<Int, ByteArray>()
    private var mixed = ArrayList<Part>()
    private val queue = ArrayDeque<Part>()

    val expectedParts: Int get() = seqLength
    val isComplete: Boolean get() = result != null

    // share of the fragments that are known, only a hint for a progress bar
    val progress: Double
        get() = when {
            isComplete -> 1.0
            seqLength == 0 -> 0.0
            else -> received.size.toDouble() / seqLength
        }

    private fun valid(part: FountainPart): Boolean {
        if (seqLength == 0) {
            seqLength = part.seqLength
            messageLength = part.messageLength
            checksum = part.checksum
            fragmentLength = part.fragment.size
            return true
        }
        return seqLength == part.seqLength && messageLength == part.messageLength && checksum == part.checksum && fragmentLength == part.fragment.size
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun reduce(a: Part, b: Part): Part =
        if (a.indexes.containsAll(b.indexes) && a.indexes.size > b.indexes.size) Part(a.indexes - b.indexes.toSet(), xor(a.fragment, b.fragment)) else a

    private fun reduceMixedBy(part: Part) {
        val next = ArrayList<Part>()
        mixed.map { reduce(it, part) }.forEach { if (it.indexes.size == 1) queue.addLast(it) else next.add(it) }
        mixed = next
    }

    private fun processSimple(part: Part) {
        val index = part.indexes[0]
        if (index in received) return
        simple[index] = part.fragment
        received.add(index)
        if (received.size == seqLength) {
            val joined = ByteArray(seqLength * fragmentLength)
            for (i in 0 until seqLength) System.arraycopy(simple.getValue(i), 0, joined, i * fragmentLength, fragmentLength)
            val message = joined.copyOf(messageLength)
            if (Bytewords.crc32(message) == checksum) result = message else failed = true
        } else {
            reduceMixedBy(part)
        }
    }

    private fun processMixed(part: Part) {
        if (mixed.any { it.indexes.toSet() == part.indexes.toSet() }) return
        var p = part
        simple.forEach { (index, fragment) -> p = reduce(p, Part(listOf(index), fragment)) }
        mixed.forEach { p = reduce(p, it) }
        if (p.indexes.size == 1) {
            queue.addLast(p)
        } else {
            reduceMixedBy(p)
            mixed.add(p)
        }
    }

    // false when the part is a duplicate of an earlier kind or does not belong to this message
    fun receive(part: FountainPart): Boolean {
        if (isComplete || failed || !valid(part)) return false
        queue.addLast(Part(FountainMath.chooseFragments(part.seqNum, part.seqLength, part.checksum), part.fragment))
        while (!isComplete && !failed && queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next.indexes.size == 1) processSimple(next) else processMixed(next)
        }
        processed++
        return true
    }
}
