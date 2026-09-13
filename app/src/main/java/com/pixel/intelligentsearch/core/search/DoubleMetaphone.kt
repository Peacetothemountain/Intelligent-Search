package com.pixel.intelligentsearch.core.search

/**
 * Pure Kotlin implementation of the Double Metaphone phonetic encoding algorithm.
 * Generates primary and alternate phonetic representations for contacts, names,
 * and conversational vocabulary, enabling accurate cross-spelling retrieval
 * (e.g., "Stephen" == "Steven", "Smith" == "Smyth", "Catherine" == "Katherine").
 */
object DoubleMetaphone {

    private const val MAX_CODE_LENGTH = 4

    data class MetaphoneResult(
        val primary: String,
        val alternate: String
    ) {
        fun matches(other: MetaphoneResult): Boolean {
            if (primary.isNotEmpty() && other.primary.isNotEmpty() && primary == other.primary) return true
            if (alternate.isNotEmpty() && other.primary.isNotEmpty() && alternate == other.primary) return true
            if (primary.isNotEmpty() && other.alternate.isNotEmpty() && primary == other.alternate) return true
            if (alternate.isNotEmpty() && other.alternate.isNotEmpty() && alternate == other.alternate) return true
            return false
        }
    }

    fun encode(rawInput: String?): MetaphoneResult {
        if (rawInput.isNullOrBlank()) {
            return MetaphoneResult("", "")
        }

        val input = rawInput.trim().uppercase()
        val length = input.length
        if (length == 0) return MetaphoneResult("", "")

        val primary = StringBuilder(MAX_CODE_LENGTH)
        val alternate = StringBuilder(MAX_CODE_LENGTH)

        var current = 0

        // Handle initial silent combinations
        if (stringAt(input, 0, 2, "GN", "KN", "PN", "WR", "PS")) {
            current++
        }

        // Initial 'X' sounds like 'S'
        if (input[0] == 'X') {
            primary.append('S')
            alternate.append('S')
            current++
        }

        while ((primary.length < MAX_CODE_LENGTH || alternate.length < MAX_CODE_LENGTH) && current < length) {
            val ch = input[current]

            when (ch) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> {
                    if (current == 0) {
                        // All initial vowels map to 'A'
                        primary.append('A')
                        alternate.append('A')
                    }
                    current++
                }

                'B' -> {
                    primary.append('P')
                    alternate.append('P')
                    if (current + 1 < length && input[current + 1] == 'B') current += 2 else current++
                }

                'C' -> {
                    // Various 'C' combinations
                    if (current > 1 && !isVowel(input, current - 2) && stringAt(input, current - 1, 3, "ACH") &&
                        (input.getOrNull(current + 2) != 'I' && (input.getOrNull(current + 2) != 'E' || stringAt(input, current - 2, 6, "BACHER", "MACHER")))
                    ) {
                        primary.append('K')
                        alternate.append('K')
                        current += 2
                    } else if (current == 0 && stringAt(input, current, 6, "CAESAR")) {
                        primary.append('S')
                        alternate.append('S')
                        current += 2
                    } else if (stringAt(input, current, 2, "CH")) {
                        // Italian or German CH
                        if (current > 0 && stringAt(input, current, 4, "CHAE")) {
                            primary.append('K')
                            alternate.append('X')
                            current += 2
                        } else if (current == 0 && (stringAt(input, current + 1, 5, "HARAC", "HARIS") ||
                                    stringAt(input, current + 1, 3, "HOR", "HYM", "HIA", "HEM")) &&
                            !stringAt(input, 0, 5, "CHORE")
                        ) {
                            primary.append('K')
                            alternate.append('K')
                            current += 2
                        } else {
                            primary.append('X')
                            alternate.append('K')
                            current += 2
                        }
                    } else if (stringAt(input, current, 2, "CZ") && !stringAt(input, current - 2, 4, "WICZ")) {
                        primary.append('S')
                        alternate.append('X')
                        current += 2
                    } else if (stringAt(input, current + 1, 3, "CIA")) {
                        primary.append('X')
                        alternate.append('X')
                        current += 3
                    } else if (stringAt(input, current, 2, "CC") && !(current == 1 && input[0] == 'M')) {
                        if (stringAt(input, current + 2, 1, "I", "E", "H") && !stringAt(input, current + 2, 2, "HU")) {
                            primary.append('X')
                            alternate.append('X')
                            current += 3
                        } else {
                            primary.append('K')
                            alternate.append('K')
                            current += 2
                        }
                    } else if (stringAt(input, current, 2, "CK", "CG", "CQ")) {
                        primary.append('K')
                        alternate.append('K')
                        current += 2
                    } else if (stringAt(input, current, 2, "CI", "CE", "CY")) {
                        primary.append('S')
                        alternate.append('S')
                        current += 2
                    } else {
                        primary.append('K')
                        alternate.append('K')
                        if (stringAt(input, current + 1, 2, " C", " Q", " G")) {
                            current += 3
                        } else if (stringAt(input, current + 1, 1, "C", "K", "Q") && !stringAt(input, current + 1, 2, "CE", "CI")) {
                            current += 2
                        } else {
                            current++
                        }
                    }
                }

                'D' -> {
                    if (stringAt(input, current, 2, "DG")) {
                        if (stringAt(input, current + 2, 1, "I", "E", "Y")) {
                            primary.append('J')
                            alternate.append('J')
                            current += 3
                        } else {
                            primary.append("TK")
                            alternate.append("TK")
                            current += 2
                        }
                    } else if (stringAt(input, current, 2, "DT", "DD")) {
                        primary.append('T')
                        alternate.append('T')
                        current += 2
                    } else {
                        primary.append('T')
                        alternate.append('T')
                        current++
                    }
                }

                'F' -> {
                    primary.append('F')
                    alternate.append('F')
                    if (current + 1 < length && input[current + 1] == 'F') current += 2 else current++
                }

                'G' -> {
                    if (input.getOrNull(current + 1) == 'H') {
                        if (current > 0 && !isVowel(input, current - 1)) {
                            primary.append('K')
                            alternate.append('K')
                            current += 2
                        } else if (current == 0) {
                            if (input.getOrNull(current + 2) == 'I') {
                                primary.append('J')
                                alternate.append('J')
                            } else {
                                primary.append('K')
                                alternate.append('K')
                            }
                            current += 2
                        } else if ((current > 1 && stringAt(input, current - 2, 1, "B", "H", "D")) ||
                            (current > 2 && stringAt(input, current - 3, 1, "B", "H", "D")) ||
                            (current > 3 && stringAt(input, current - 4, 1, "B", "H"))
                        ) {
                            current += 2
                        } else {
                            if (current > 2 && input[current - 1] == 'U' && stringAt(input, current - 3, 1, "C", "G", "L", "R", "T")) {
                                primary.append('F')
                                alternate.append('F')
                            } else if (current > 0 && input[current - 1] != 'I') {
                                primary.append('K')
                                alternate.append('K')
                            }
                            current += 2
                        }
                    } else if (input.getOrNull(current + 1) == 'N') {
                        if (current == 1 && isVowel(input, 0) && !isSlavoGermanic(input)) {
                            primary.append("KN")
                            alternate.append('N')
                        } else if (!stringAt(input, current + 2, 2, "EY") && input.getOrNull(current + 1) != 'Y' && !isSlavoGermanic(input)) {
                            primary.append('N')
                            alternate.append("KN")
                        } else {
                            primary.append("KN")
                            alternate.append("KN")
                        }
                        current += 2
                    } else if (stringAt(input, current + 1, 2, "LI") && !isSlavoGermanic(input)) {
                        primary.append("KL")
                        alternate.append('L')
                        current += 2
                    } else if (current == 0 && (input.getOrNull(current + 1) == 'Y' ||
                                stringAt(input, current + 1, 2, "ES", "EP", "EB", "EL", "EY", "IB", "IL", "IN", "IE", "EI", "ER"))
                    ) {
                        primary.append('K')
                        alternate.append('J')
                        current += 2
                    } else if (stringAt(input, current + 1, 1, "E", "I", "Y")) {
                        primary.append('J')
                        alternate.append('K')
                        current += 2
                    } else {
                        primary.append('K')
                        alternate.append('K')
                        if (input.getOrNull(current + 1) == 'G') current += 2 else current++
                    }
                }

                'H' -> {
                    // Keep H if before vowel and not after vowel
                    if ((current == 0 || isVowel(input, current - 1)) && isVowel(input, current + 1)) {
                        primary.append('H')
                        alternate.append('H')
                        current += 2
                    } else {
                        current++
                    }
                }

                'J' -> {
                    if (stringAt(input, current, 4, "JOSE") || stringAt(input, 0, 4, "SAN ")) {
                        if ((current == 0 && input.getOrNull(current + 4) == ' ') || stringAt(input, 0, 4, "SAN ")) {
                            primary.append('H')
                            alternate.append('H')
                        } else {
                            primary.append('J')
                            alternate.append('H')
                        }
                        current++
                    } else {
                        if (current == 0 && !stringAt(input, current, 4, "JOSE")) {
                            primary.append('J')
                            alternate.append('A')
                        } else if (isVowel(input, current - 1) && !isSlavoGermanic(input) && (input.getOrNull(current + 1) == 'A' || input.getOrNull(current + 1) == 'O')) {
                            primary.append('J')
                            alternate.append('H')
                        } else if (current == length - 1) {
                            primary.append('J')
                        } else if (!stringAt(input, current + 1, 1, "L", "T", "K", "S", "N", "M", "B", "Z") && !stringAt(input, current - 1, 1, "S", "K", "L")) {
                            primary.append('J')
                            alternate.append('J')
                        }
                        if (input.getOrNull(current + 1) == 'J') current += 2 else current++
                    }
                }

                'K' -> {
                    primary.append('K')
                    alternate.append('K')
                    if (input.getOrNull(current + 1) == 'K') current += 2 else current++
                }

                'L' -> {
                    if (input.getOrNull(current + 1) == 'L') {
                        if (conditionSpanishLl(input, current, length)) {
                            primary.append('L')
                            current += 2
                        } else {
                            primary.append('L')
                            alternate.append('L')
                            current += 2
                        }
                    } else {
                        primary.append('L')
                        alternate.append('L')
                        current++
                    }
                }

                'M' -> {
                    primary.append('M')
                    alternate.append('M')
                    if (stringAt(input, current - 1, 3, "UMB") && (current + 1 == length - 1 || stringAt(input, current + 2, 2, "ER"))) {
                        current += 2
                    } else if (input.getOrNull(current + 1) == 'M') {
                        current += 2
                    } else {
                        current++
                    }
                }

                'N' -> {
                    primary.append('N')
                    alternate.append('N')
                    if (input.getOrNull(current + 1) == 'N') current += 2 else current++
                }

                'P' -> {
                    if (input.getOrNull(current + 1) == 'H') {
                        primary.append('F')
                        alternate.append('F')
                        current += 2
                    } else {
                        primary.append('P')
                        alternate.append('P')
                        if (stringAt(input, current + 1, 1, "P", "B")) current += 2 else current++
                    }
                }

                'Q' -> {
                    primary.append('K')
                    alternate.append('K')
                    if (input.getOrNull(current + 1) == 'Q') current += 2 else current++
                }

                'R' -> {
                    if (current == length - 1 && !isSlavoGermanic(input) && stringAt(input, current - 2, 2, "IE") &&
                        !stringAt(input, current - 4, 2, "ME", "MA")
                    ) {
                        alternate.append('R')
                    } else {
                        primary.append('R')
                        alternate.append('R')
                    }
                    if (input.getOrNull(current + 1) == 'R') current += 2 else current++
                }

                'S' -> {
                    if (stringAt(input, current - 1, 3, "ISL", "YSL")) {
                        current++
                    } else if (current == 0 && stringAt(input, current, 5, "SUGAR")) {
                        primary.append('X')
                        alternate.append('S')
                        current++
                    } else if (stringAt(input, current, 2, "SH")) {
                        primary.append('X')
                        alternate.append('X')
                        current += 2
                    } else if (stringAt(input, current, 3, "SIO", "SIA") || stringAt(input, current, 4, "SIAN")) {
                        primary.append('S')
                        alternate.append('X')
                        current += 3
                    } else if ((current == 0 && stringAt(input, current + 1, 1, "M", "N", "L", "W")) || stringAt(input, current + 1, 1, "Z")) {
                        primary.append('S')
                        alternate.append('X')
                        if (stringAt(input, current + 1, 1, "Z")) current += 2 else current++
                    } else if (stringAt(input, current, 2, "SC")) {
                        if (input.getOrNull(current + 2) == 'H') {
                            if (stringAt(input, current + 3, 2, "OO", "ER", "EN", "UY", "ED", "EM")) {
                                if (stringAt(input, current + 3, 2, "ER", "EN")) {
                                    primary.append('X')
                                    alternate.append("SK")
                                } else {
                                    primary.append("SK")
                                    alternate.append("SK")
                                }
                            } else {
                                if (current == 0 && !isVowel(input, 3) && input.getOrNull(3) != 'W') {
                                    primary.append('X')
                                    alternate.append('S')
                                } else {
                                    primary.append('X')
                                    alternate.append('X')
                                }
                            }
                        } else if (stringAt(input, current + 2, 1, "I", "E", "Y")) {
                            primary.append('S')
                            alternate.append('S')
                        } else {
                            primary.append("SK")
                            alternate.append("SK")
                        }
                        current += 3
                    } else {
                        primary.append('S')
                        alternate.append('S')
                        if (stringAt(input, current + 1, 1, "S", "Z")) current += 2 else current++
                    }
                }

                'T' -> {
                    if (stringAt(input, current, 4, "TION")) {
                        primary.append('X')
                        alternate.append('X')
                        current += 3
                    } else if (stringAt(input, current, 3, "TIA", "TCH")) {
                        primary.append('X')
                        alternate.append('X')
                        current += 3
                    } else if (stringAt(input, current, 2, "TH") || stringAt(input, current, 3, "TTH")) {
                        if (stringAt(input, current + 2, 2, "OM", "AM") || stringAt(input, 0, 4, "VAN ", "VON ") || stringAt(input, 0, 3, "SCH")) {
                            primary.append('T')
                            alternate.append('T')
                        } else {
                            primary.append('0') // Metaphone 'theta' representation
                            alternate.append('T')
                        }
                        current += 2
                    } else {
                        primary.append('T')
                        alternate.append('T')
                        if (stringAt(input, current + 1, 1, "T", "D")) current += 2 else current++
                    }
                }

                'V' -> {
                    primary.append('F')
                    alternate.append('F')
                    if (input.getOrNull(current + 1) == 'V') current += 2 else current++
                }

                'W' -> {
                    if (stringAt(input, current, 2, "WR")) {
                        primary.append('R')
                        alternate.append('R')
                        current += 2
                    } else if (current == 0 && (isVowel(input, current + 1) || stringAt(input, current, 2, "WH"))) {
                        if (isVowel(input, current + 1)) {
                            primary.append('A')
                            alternate.append('F')
                        } else {
                            primary.append('A')
                            alternate.append('A')
                        }
                        current++
                    } else if ((current == length - 1 && isVowel(input, current - 1)) ||
                        stringAt(input, current - 1, 5, "EWSKI", "EWSKY", "OWSKI", "OWSKY") ||
                        stringAt(input, 0, 3, "SCH")
                    ) {
                        alternate.append('F')
                        current++
                    } else {
                        current++
                    }
                }

                'X' -> {
                    if (!(current == length - 1 && (stringAt(input, current - 3, 3, "IAU", "EAU") || stringAt(input, current - 2, 2, "AU", "OU")))) {
                        primary.append("KS")
                        alternate.append("KS")
                    }
                    if (stringAt(input, current + 1, 1, "C", "X")) current += 2 else current++
                }

                'Z' -> {
                    if (input.getOrNull(current + 1) == 'H') {
                        primary.append('J')
                        alternate.append('J')
                        current += 2
                    } else {
                        primary.append('S')
                        alternate.append('S')
                        if (input.getOrNull(current + 1) == 'Z') current += 2 else current++
                    }
                }

                else -> current++
            }
        }

        val priStr = if (primary.length > MAX_CODE_LENGTH) primary.substring(0, MAX_CODE_LENGTH) else primary.toString()
        val altStr = if (alternate.length > MAX_CODE_LENGTH) alternate.substring(0, MAX_CODE_LENGTH) else alternate.toString()

        return MetaphoneResult(priStr, altStr)
    }

    private fun isVowel(input: String, index: Int): Boolean {
        if (index < 0 || index >= input.length) return false
        return input[index] in "AEIOUY"
    }

    private fun isSlavoGermanic(input: String): Boolean {
        return input.contains("W") || input.contains("K") || input.contains("CZ") || input.contains("WITZ")
    }

    private fun conditionSpanishLl(input: String, current: Int, length: Int): Boolean {
        if (current == length - 2 && stringAt(input, current - 1, 4, "ILLO", "ILLA", "ALLE")) return true
        if ((stringAt(input, length - 2, 2, "AS", "OS") || stringAt(input, length - 1, 1, "A", "O")) &&
            stringAt(input, current - 1, 4, "ALLE")
        ) return true
        return false
    }

    private fun stringAt(input: String, start: Int, length: Int, vararg targets: String): Boolean {
        if (start < 0 || start + length > input.length) return false
        for (target in targets) {
            if (target.length == length && input.regionMatches(start, target, 0, length, ignoreCase = false)) {
                return true
            }
        }
        return false
    }
}
