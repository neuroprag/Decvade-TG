package com.decvade.tg

import kotlin.random.Random

class UsernameGenerator(
    private val config: GeneratorConfig,
    private val dictionary: List<String> = emptyList()
) {
    private val rnd = Random(System.currentTimeMillis())

    fun generateCandidates(
        prefix: String = "",
        maxCount: Int = 200,
        minLength: Int = 5,
        maxLength: Int = 32,
        lettersOnly: Boolean = false,
        allowDigits: Boolean = true,
        allowUnderscore: Boolean = true
    ): List<String> {
        val results = mutableListOf<String>()
        val pattern = config.patternOrder

        repeat(maxCount.coerceAtLeast(0) * 20) {
            val length = rnd.nextInt(minLength, maxLength + 1)
            val word = buildByPattern(pattern, length, prefix, lettersOnly, allowDigits, allowUnderscore)
            if (isCandidate(word, prefix, minLength, maxLength, lettersOnly, allowDigits, allowUnderscore)) {
                results += word
            }
        }

        if (dictionary.isNotEmpty()) {
            results += dictionary
                .filter {
                    isCandidate(it, prefix, minLength, maxLength, lettersOnly, allowDigits, allowUnderscore)
                }
                .shuffled(rnd)
        }

        return results.distinct().take(maxCount)
    }

    private fun isCandidate(
        word: String,
        prefix: String,
        minLength: Int,
        maxLength: Int,
        lettersOnly: Boolean,
        allowDigits: Boolean,
        allowUnderscore: Boolean
    ): Boolean {
        return word.length in minLength..maxLength &&
            word.firstOrNull()?.isLetter() == true &&
            word.lastOrNull()?.isLetterOrDigit() == true &&
            !word.contains("__") &&
            word.all {
                it.isLetter() ||
                    (!lettersOnly && allowDigits && it.isDigit()) ||
                    (!lettersOnly && allowUnderscore && it == '_')
            } &&
            (prefix.isBlank() || word.startsWith(prefix.lowercase()))
    }

    private fun buildByPattern(
        pattern: List<String>,
        length: Int,
        prefix: String,
        lettersOnly: Boolean,
        allowDigits: Boolean,
        allowUnderscore: Boolean
    ): String {
        val chars = StringBuilder(prefix.lowercase())
        while (chars.length < length) {
            val lastPosition = chars.length == length - 1
            val canAddUnderscore = chars.isNotEmpty() && !lettersOnly && allowUnderscore && !lastPosition &&
                chars.lastOrNull() != '_'
            when {
                canAddUnderscore && rnd.nextInt(30) == 0 -> chars.append('_')
                !lettersOnly && allowDigits && rnd.nextInt(12) == 0 -> chars.append(rnd.nextInt(10))
                else -> appendPatternCharacter(chars, pattern, prefix)
            }
        }
        return chars.toString()
    }

    private fun appendPatternCharacter(chars: StringBuilder, pattern: List<String>, prefix: String) {
        val patternIndex = if (pattern.isEmpty()) 0 else (chars.length - prefix.length) % pattern.size
        when (pattern.getOrNull(patternIndex)) {
            "v" -> chars.append(randomVowel())
            "c" -> chars.append(randomConsonant())
            else -> chars.append(randomConsonant())
        }
    }

    private fun randomVowel(): Char = config.vowels[rnd.nextInt(config.vowels.length)].lowercaseChar()
    private fun randomConsonant(): Char = config.consonants[rnd.nextInt(config.consonants.length)].lowercaseChar()
}
