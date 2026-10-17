package com.ngdesigns.intelligentsearch.engine.text

/**
 * Edit distances with adjacent transpositions (optimal string alignment): "teh" is one edit from "the".
 * Both functions stop early and return [limit] + 1 once the distance must exceed [limit], so typo checks stay
 * cheap across thousands of names.
 */
object EditDistance {

    /** Edits to turn [a] into [b], or [limit] + 1 if more than [limit]. */
    fun between(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        return table(a, b, limit) { rows, _ -> rows[a.length][b.length] }
    }

    /**
     * The fewest edits to turn [query] into some prefix of [target]: how far a partly typed word is from the
     * start of a name ("yuotu" is one transposition from "youtu", the start of "youtube").
     */
    fun toPrefix(query: String, target: String, limit: Int): Int {
        if (query.isEmpty()) return 0
        return table(query, target, limit) { rows, cols ->
            var best = limit + 1
            for (j in 0..cols) best = minOf(best, rows[query.length][j])
            best
        }
    }

    private inline fun table(a: String, b: String, limit: Int, result: (Array<IntArray>, Int) -> Int): Int {
        // Prefix matching never needs more of the target than the query length plus the allowed edits.
        val cols = minOf(b.length, a.length + limit)
        val rows = Array(a.length + 1) { IntArray(cols + 1) }
        for (j in 0..cols) rows[0][j] = j
        for (i in 1..a.length) {
            rows[i][0] = i
            var rowMin = i
            for (j in 1..cols) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(rows[i - 1][j] + 1, rows[i][j - 1] + 1, rows[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, rows[i - 2][j - 2] + 1)
                rows[i][j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
        }
        return minOf(result(rows, cols), limit + 1)
    }
}
