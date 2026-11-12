package org.elektronnet.mobile.wallet

// Helps while a recovery phrase is typed: the word being typed is completed from the word list, finished words are checked
object PhraseInput {
    // the word that is still being typed, empty when the text ends with a space
    fun partial(text: String): String =
        if (text.isEmpty() || text.last().isWhitespace()) "" else Bip39.parse(text).lastOrNull().orEmpty()

    fun complete(text: String, word: String): String {
        val cut = text.indexOfLast { it.isWhitespace() } + 1
        return text.substring(0, cut) + word + " "
    }

    // finished words (followed by a space) that are not in the word list
    fun unknown(text: String, bip39: Bip39): List<String> {
        val words = Bip39.parse(text)
        val finished = if (partial(text).isEmpty()) words else words.dropLast(1)
        return finished.filter { !bip39.isWord(it) }
    }
}
