package com.decvade.tg

class UsernameScorer(private val config: ScoringConfig) {

    fun score(name: String): Int {
        if (name.isBlank()) return 0

        var score = 0
        val lower = name.lowercase()

        val len = lower.length
        if (len in config.lengthTargetMin..config.lengthTargetMax) {
            score += 6
        } else if (len in 5..7 || len in 13..16) {
            score += 2
        } else if (len > 16) {
            score -= 2
        }

        for (ch in lower) {
            if (ch.toString() in config.highValueCharacters) {
                score += 2
            }
        }

        if (lower.contains("x") || lower.contains("k") || lower.contains("v") || lower.contains("z")) {
            score += config.bonusForMemorability
        }
        if (lower.contains("ai") || lower.contains("io") || lower.contains("ly") || lower.contains("fx")) {
            score += config.bonusForBrandish
        }
        if (lower.contains("qa") || lower.contains("xv") || lower.contains("ky") || lower.contains("rk")) {
            score += config.bonusForCipherStyle
        }

        if (hasRepeatedSequences(lower)) {
            score -= config.penaltyForRepeats
        }

        if (lower.all { it.isDigit() }) {
            score -= 12
        }

        val vowels = lower.count { it in "aeiouy" }
        val consonants = lower.count { it !in "aeiouy" }
        if (vowels >= 2 && consonants >= 2) {
            score += 3
        }

        return score
    }

    private fun hasRepeatedSequences(name: String): Boolean {
        if (name.length < 4) return false
        for (i in 0 until name.length - 2) {
            if (name[i] == name[i + 1] && name[i + 1] == name[i + 2]) {
                return true
            }
        }
        return false
    }
}
