package com.personal.flowreader.data

/**
 * English sentence boundary heuristic ported from mediacloud/sentence-splitter
 * (Koehn / Schroeder Europarl algorithm; LGPL-3).
 *
 * Upstream: https://github.com/mediacloud/sentence-splitter
 */
internal object KoehnSentenceBreak {
    private enum class PrefixType { DEFAULT, NUMERIC_ONLY }

    private val prefixes: Map<String, PrefixType> by lazy { loadEnglishPrefixes() }

    // Approximate Python regex Unicode props with JVM-friendly classes.
    private val reQuestExcl = Regex(
        """([?!]) +(['"(\[¿¡\p{Pi}]*[\p{Lu}\p{Lo}])""",
    )
    private val reMultiDot = Regex(
        """(\.[\.]+) +(['"(\[¿¡\p{Pi}]*[\p{Lu}\p{Lo}])""",
    )
    private val rePunctInCloser = Regex(
        """([?!.]\s*['")\]\p{Pf}]+) +(['"(\[¿¡\p{Pi}]*\s*[\p{Lu}\p{Lo}])""",
    )
    private val rePunctThenOpener = Regex(
        """([?!.]) +(['"\[¿¡\p{Pi}]+\s*[\p{Lu}\p{Lo}])""",
    )
    private val reWordDots = Regex(
        """([\w.\-]*)(['\")\]%\p{Pf}]*)(\.+)$""",
    )
    private val reAcronym = Regex(
        """(\.)[\p{Lu}\p{Lo}\-]+(\.+)$""",
    )
    private val reSentenceStart = Regex(
        """^(\s*['"(\[¿¡\p{Pi}]*\s*[\p{Lu}\p{Lo}0-9])""",
    )

    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        var work = text
        work = reQuestExcl.replace(work, "$1\n$2")
        work = reMultiDot.replace(work, "$1\n$2")
        work = rePunctInCloser.replace(work, "$1\n$2")
        work = rePunctThenOpener.replace(work, "$1\n$2")

        val words = work.split(Regex(" +"))
        if (words.isEmpty()) return emptyList()
        if (words.size == 1) {
            return listOf(words[0].trim()).filter { it.isNotEmpty() }
        }

        val out = StringBuilder()
        for (i in 0 until words.lastIndex) {
            var word = words[i]
            val match = reWordDots.find(word)
            if (match != null) {
                val prefix = match.groupValues[1]
                val startingPunct = match.groupValues[2]
                val honorific = prefix.isNotEmpty() &&
                    prefixes[prefix] == PrefixType.DEFAULT &&
                    startingPunct.isEmpty()
                val acronym = reAcronym.containsMatchIn(word)
                val nextStarts = reSentenceStart.containsMatchIn(words[i + 1])
                val numericOnly = prefix.isNotEmpty() &&
                    prefixes[prefix] == PrefixType.NUMERIC_ONLY &&
                    startingPunct.isEmpty() &&
                    words[i + 1].trimStart().firstOrNull()?.isDigit() == true

                if (!honorific && !acronym && nextStarts && !numericOnly) {
                    word = "$word\n"
                }
            }
            out.append(word).append(' ')
        }
        out.append(words.last())

        return out.toString()
            .replace(Regex(" +"), " ")
            .replace(Regex("\n "), "\n")
            .replace(Regex(" \n"), "\n")
            .trim()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun loadEnglishPrefixes(): Map<String, PrefixType> {
        val stream = KoehnSentenceBreak::class.java
            .getResourceAsStream("/sentence_splitter/en.txt")
            ?: error("Missing resource /sentence_splitter/en.txt")
        val map = LinkedHashMap<String, PrefixType>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                var line = raw
                val numericOnly = line.contains("#NUMERIC_ONLY#")
                val hash = line.indexOf('#')
                if (hash >= 0) line = line.substring(0, hash)
                line = line.trim()
                if (line.isEmpty()) continue
                map[line] = if (numericOnly) PrefixType.NUMERIC_ONLY else PrefixType.DEFAULT
            }
        }
        return map
    }
}
