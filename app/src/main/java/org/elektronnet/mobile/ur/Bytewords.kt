package org.elektronnet.mobile.ur

import java.util.zip.CRC32

// the 256 words of BCR-2020-012, every word is four letters and the first and last letter are unique
object Bytewords {
    private const val WORDS = "ableacidalsoapexaquaarchatomauntawayaxisbackbaldbarnbeltbetabiasbluebodybragbrewbulbbuzzcalmcashcatschefcityclawcodecolacookcostcruxcurlcuspcyandarkdatadaysdelidicedietdoordowndrawdropdrumdulldutyeacheasyechoedgeepicevenexamexiteyesfactfairfernfigsfilmfishfizzflapflewfluxfoxyfreefrogfuelfundgalagamegeargemsgiftgirlglowgoodgraygrimgurugushgyrohalfhanghardhawkheathelphighhillholyhopehornhutsicedideaidleinchinkyintoirisironitemjadejazzjoinjoltjowljudojugsjumpjunkjurykeepkenokeptkeyskickkilnkingkitekiwiknoblamblavalazyleaflegsliarlimplionlistlogoloudloveluaulucklungmainmanymathmazememomenumeowmildmintmissmonknailnavyneednewsnextnoonnotenumbobeyoboeomitonyxopenovalowlspaidpartpeckplaypluspoempoolposepuffpumapurrquadquizraceramprealredorichroadrockroofrubyruinrunsrustsafesagascarsetssilkskewslotsoapsolosongstubsurfswantacotasktaxitenttiedtimetinytoiltombtoystriptunatwinuglyundouniturgeuservastveryvetovialvibeviewvisavoidvowswallwandwarmwaspwavewaxywebswhatwhenwhizwolfworkyankyawnyellyogayurtzapszerozestzinczonezoom"

    private val minimal: Map<String, Int> = (0 until 256).associate { index ->
        val word = WORDS.substring(index * 4, index * 4 + 4)
        "${word[0]}${word[3]}" to index
    }

    fun crc32(data: ByteArray): Long = CRC32().also { it.update(data) }.value

    // the data is followed by its CRC-32 and every byte becomes the first and last letter of its word
    fun encodeMinimal(data: ByteArray): String {
        val checksum = crc32(data)
        val all = data + ByteArray(4) { (checksum shr (24 - 8 * it)).toByte() }
        return buildString {
            all.forEach { byte ->
                val word = (byte.toInt() and 0xFF) * 4
                append(WORDS[word]).append(WORDS[word + 3])
            }
        }
    }

    fun decodeMinimal(text: String): ByteArray {
        val lower = text.lowercase()
        require(lower.length % 2 == 0 && lower.length >= 10) { "Invalid Bytewords" }
        val all = ByteArray(lower.length / 2) { i ->
            (minimal[lower.substring(i * 2, i * 2 + 2)] ?: throw IllegalArgumentException("Invalid Bytewords")).toByte()
        }
        val body = all.copyOfRange(0, all.size - 4)
        val checksum = crc32(body)
        for (i in 0 until 4) require(all[body.size + i] == (checksum shr (24 - 8 * i)).toByte()) { "Invalid checksum" }
        return body
    }
}
