package noryn.launcher.launcher.presentation

import java.text.Normalizer
import java.util.Locale

internal object AppSearchRanking {
    fun rank(originalName: String, customName: String, normalizedQuery: String): Int {
        val original = normalize(originalName)
        val custom = customName.takeIf(String::isNotBlank)?.let(::normalize)
        if (original == normalizedQuery || custom == normalizedQuery) return EXACT_MATCH
        if (original.startsWith(normalizedQuery) || custom?.startsWith(normalizedQuery) == true) return PREFIX_MATCH
        if (startsAtWordBoundary(original, normalizedQuery) || custom?.let { startsAtWordBoundary(it, normalizedQuery) } == true) {
            return WORD_MATCH
        }
        if (original.contains(normalizedQuery) || custom?.contains(normalizedQuery) == true) return CONTAINS_MATCH
        return NO_MATCH
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { character ->
            val type = Character.getType(character)
            type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
        }
        .trim()
        .lowercase(Locale.ROOT)

    private fun startsAtWordBoundary(value: String, query: String): Boolean {
        var match = value.indexOf(query)
        while (match > 0) {
            val previousCodePoint = value.codePointBefore(match)
            if (!Character.isLetterOrDigit(previousCodePoint)) return true
            match = value.indexOf(query, match + 1)
        }
        return false
    }

    private const val EXACT_MATCH = 0
    private const val PREFIX_MATCH = 1
    private const val WORD_MATCH = 2
    private const val CONTAINS_MATCH = 3
    private const val NO_MATCH = -1
}
