package com.example.data.country

import java.util.Comparator

/**
 * Natural Order / Alphanumeric Comparator.
 * Accurately sorts strings containing numbers naturally, so that:
 * "Sky Sport 2" comes before "Sky Sport 10",
 * "ORF 1" comes before "ORF 2",
 * "Episode 9" comes before "Episode 10".
 */
object NaturalOrderComparator : Comparator<String> {

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 === s2) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1
        if (s1 == s2) return 0

        var i1 = 0
        var i2 = 0
        val len1 = s1.length
        val len2 = s2.length

        while (i1 < len1 && i2 < len2) {
            val c1 = s1[i1]
            val c2 = s2[i2]

            if (c1 in '0'..'9' && c2 in '0'..'9') {
                // Find full digit sequence in s1
                val num1Start = i1
                while (i1 < len1 && s1[i1] in '0'..'9') {
                    i1++
                }
                // Find full digit sequence in s2
                val num2Start = i2
                while (i2 < len2 && s2[i2] in '0'..'9') {
                    i2++
                }

                val numStr1 = s1.substring(num1Start, i1)
                val numStr2 = s2.substring(num2Start, i2)

                // Trim leading zeros for value comparison
                val trim1 = numStr1.trimStart('0')
                val trim2 = numStr2.trimStart('0')

                val diffLen = trim1.length - trim2.length
                if (diffLen != 0) {
                    return diffLen
                }

                val cmp = trim1.compareTo(trim2)
                if (cmp != 0) {
                    return cmp
                }

                // If identical numerical value, shorter string (fewer leading zeros) wins
                val zeroDiff = numStr1.length - numStr2.length
                if (zeroDiff != 0) {
                    return zeroDiff
                }
            } else {
                val lc1 = if (c1 in 'A'..'Z') (c1.code + 32).toChar() else c1.lowercaseChar()
                val lc2 = if (c2 in 'A'..'Z') (c2.code + 32).toChar() else c2.lowercaseChar()
                if (lc1 != lc2) {
                    return lc1.compareTo(lc2)
                }
                i1++
                i2++
            }
        }
        return len1 - len2
    }
}
