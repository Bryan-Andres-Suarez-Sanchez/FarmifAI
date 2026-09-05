package edu.unicauca.app.agrochat.rag

import org.tartarus.snowball.ext.spanishStemmer
import java.text.Normalizer

/** Mirrors preprocess_spanish() in rag_slm_colab.ipynb. */
class SpanishTextPreprocessor(private val stopWords: Set<String>) {
    fun tokenize(text: String): List<String> {
        val normalized = removeAccents(text.lowercase()).replace(Regex("[^a-z0-9\\s]"), " ")
        return normalized.split(Regex("\\s+"))
            .asSequence()
            .filter { it.length > 1 && it !in stopWords }
            .map { token: String ->
                val stemmer = spanishStemmer()
                stemmer.setCurrent(token)
                stemmer.stem()
                stemmer.getCurrent()
            }
            .filter { stem: String -> stem.length > 1 }
            .toList()
    }

    companion object {
        fun removeAccents(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

        fun fromLines(lines: Sequence<String>): SpanishTextPreprocessor = SpanishTextPreprocessor(
            lines.map { removeAccents(it.trim().lowercase()) }.filter { it.isNotBlank() }.toSet()
        )
    }
}
