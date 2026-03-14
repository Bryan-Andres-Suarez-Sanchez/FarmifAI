package edu.unicauca.app.agrochat.routing

import kotlin.math.max
import kotlin.math.min

/**
 * Builds a deterministic KB-grounded fallback when the LLM fails.
 * This avoids generic "could not answer" responses when there is KB signal.
 */
object KbFallbackComposer {
    data class Input(
        val userQuery: String,
        val topMatchQuestion: String?,
        val topMatchAnswer: String?,
        val combinedContext: String?,
        val unknownQueryTokens: Set<String> = emptySet(),
        val maxPoints: Int = 4
    )

    data class Result(
        val response: String,
        val keyPoints: Int,
        val source: String
    )

    fun compose(input: Input): Result? {
        val normalizedQuery = normalizeSpaces(input.userQuery)
        if (normalizedQuery.isBlank()) return null

        val topMatchAnswer = normalizeSpaces(input.topMatchAnswer.orEmpty())
        val normalizedCombinedContext = stripContextMarkers(input.combinedContext.orEmpty())
        val sourceText = when {
            topMatchAnswer.isNotBlank() -> topMatchAnswer
            normalizedCombinedContext.isNotBlank() -> normalizedCombinedContext
            else -> return null
        }
        val source = if (topMatchAnswer.isNotBlank()) "top_match_answer" else "combined_context"

        val queryTokens = informativeTokens(normalizedQuery)
        val candidates = extractCandidateSegments(sourceText)
        val maxPoints = input.maxPoints.coerceIn(2, 6)
        val ranked = candidates
            .map { segment -> segment to scoreSegment(segment, queryTokens) }
            .sortedByDescending { it.second }
            .map { normalizeSentence(it.first) }
            .filter { it.length >= 18 }
            .distinct()
            .take(maxPoints)

        val keyPoints = if (ranked.isNotEmpty()) {
            ranked
        } else {
            fallbackSegments(sourceText, maxPoints)
        }
        if (keyPoints.isEmpty()) return null

        val title = buildTitle(normalizedQuery, normalizeSpaces(input.topMatchQuestion.orEmpty()))
        val missingTokens = input.unknownQueryTokens
            .map { normalizeToken(it) }
            .filter { it.length >= 4 }
            .distinct()
            .take(3)

        val response = buildString {
            append(title)
            append('\n')
            keyPoints.forEach { point ->
                append("- ")
                append(point)
                append('\n')
            }
            if (missingTokens.isNotEmpty()) {
                append('\n')
                append("Para afinar mas la recomendacion faltan datos en la KB sobre: ")
                append(missingTokens.joinToString(", "))
                append('.')
            }
        }.trim()

        return Result(
            response = response,
            keyPoints = keyPoints.size,
            source = source
        )
    }

    private fun buildTitle(userQuery: String, topMatchQuestion: String): String {
        return if (topMatchQuestion.isNotBlank()) {
            "Con base en la KB sobre \"$topMatchQuestion\", para \"$userQuery\":"
        } else {
            "Con base en la KB, para \"$userQuery\":"
        }
    }

    private fun extractCandidateSegments(sourceText: String): List<String> {
        return sourceText
            .split(Regex("\\r?\\n+|(?<=[.!?])\\s+"))
            .map { normalizeSentence(it) }
            .filter { it.length >= 25 }
            .distinct()
    }

    private fun fallbackSegments(sourceText: String, maxPoints: Int): List<String> {
        return sourceText
            .split(Regex("\\r?\\n+"))
            .map { normalizeSentence(it) }
            .filter { it.length >= 25 }
            .distinct()
            .take(maxPoints)
    }

    private fun scoreSegment(segment: String, queryTokens: Set<String>): Float {
        val segmentTokens = informativeTokens(segment)
        val overlap = if (queryTokens.isEmpty()) 0 else segmentTokens.intersect(queryTokens).size

        val lengthScore = when {
            segment.length in 50..220 -> 1.4f
            segment.length in 30..260 -> 1.0f
            else -> 0.4f
        }
        val actionWords = setOf(
            "paso", "pasos", "recomendado", "recomendada", "instalar", "instala",
            "usar", "aplicar", "aplica", "riego", "fertilizacion", "control", "manejo"
        )
        val actionBonus = if (segmentTokens.any { it in actionWords }) 0.6f else 0f
        val tokenCountBonus = min(0.8f, max(0f, segmentTokens.size / 20f))

        return overlap * 2f + lengthScore + actionBonus + tokenCountBonus
    }

    private fun stripContextMarkers(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace("Informacion agricola relevante:", "", ignoreCase = true)
            .replace(Regex("\\[\\d+\\]\\s*[A-Z_\\- ]+"), " ")
            .replace("---", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeSentence(text: String): String {
        if (text.isBlank()) return ""
        return text
            .trim()
            .trim('-', '*', ' ')
            .replace(Regex("\\s+"), " ")
            .removeSuffix(",")
            .removeSuffix(";")
            .let { if (it.endsWith(".")) it else "$it." }
    }

    private fun normalizeToken(text: String): String {
        return text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun informativeTokens(text: String): Set<String> {
        val stopWords = setOf(
            "de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con", "por",
            "para", "al", "un", "una", "unos", "unas", "que", "como", "cuando", "donde",
            "sobre", "acerca", "porque", "pero", "esto", "esta", "este", "esas", "esos"
        )
        val normalized = text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return emptySet()
        return normalized
            .split(" ")
            .map { token ->
                when {
                    token.length > 4 && token.endsWith("es") -> token.dropLast(2)
                    token.length > 3 && token.endsWith("s") -> token.dropLast(1)
                    else -> token
                }
            }
            .filter { it.length >= 4 && it !in stopWords }
            .toSet()
    }

    private fun normalizeSpaces(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
