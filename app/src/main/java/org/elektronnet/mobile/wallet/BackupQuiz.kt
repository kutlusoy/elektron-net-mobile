package org.elektronnet.mobile.wallet

import java.util.Random

class BackupQuiz(private val phrase: List<String>, random: Random, count: Int = 3) {
    val positions: List<Int>

    init {
        require(phrase.size >= count) { "phrase shorter than quiz" }
        positions = (phrase.indices).shuffled(random).take(count).sorted()
    }

    fun check(answers: List<String>): Boolean =
        answers.size == positions.size && positions.indices.all { phrase[positions[it]] == answers[it].trim().lowercase() }

    fun wrongPositions(answers: List<String>): List<Int> =
        positions.indices.filter { answers.getOrNull(it)?.trim()?.lowercase() != phrase[positions[it]] }.map { positions[it] }
}
