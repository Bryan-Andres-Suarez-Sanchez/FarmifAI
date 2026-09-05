package edu.unicauca.app.agrochat.llm

internal data class AnswerExtraction(val answer: String, val strategy: String)

/** Tolerant answer extractor that never intentionally returns reasoning blocks. */
internal object AnswerXmlValidator {
    private val answerOpen = Regex("(?is)(?:<\\s*(?:answer|respuesta|final_answer)\\s*:?\\s*>|\\[\\s*(?:answer|respuesta)\\s*]|(?:respuesta|answer)\\s*(?:final)?\\s*:)")
    private val answerClose = Regex("(?is)</\\s*(?:answer|respuesta|final_answer)\\s*>|\\[\\s*/\\s*(?:answer|respuesta)\\s*]")
    private val closedReasoning = Regex("(?is)<\\s*(?:reasoning|razonamiento|think|analysis)\\s*>.*?</\\s*(?:reasoning|razonamiento|think|analysis)\\s*>")
    private val reasoningOpen = Regex("(?is)<\\s*(?:reasoning|razonamiento|think|analysis)\\s*>")
    private val reasoningClose = Regex("(?is)</\\s*(?:reasoning|razonamiento|think|analysis)\\s*>")

    fun extract(response: String): AnswerExtraction? {
        val text = response.trim()
        if (text.isBlank()) return null

        answerOpen.find(text)?.let { opening ->
            val remainder = text.substring(opening.range.last + 1)
            val end = listOfNotNull(
                answerClose.find(remainder)?.range?.first,
                reasoningOpen.find(remainder)?.range?.first
            ).minOrNull() ?: remainder.length
            sanitize(remainder.substring(0, end))?.let {
                return AnswerExtraction(it, "answer_marker")
            }
        }

        val withoutClosedReasoning = text.replace(closedReasoning, " ").trim()
        if (withoutClosedReasoning != text) {
            sanitize(withoutClosedReasoning)?.let { return AnswerExtraction(it, "reasoning_removed") }
        }

        reasoningClose.find(text)?.let { closing ->
            sanitize(text.substring(closing.range.last + 1))?.let {
                return AnswerExtraction(it, "after_reasoning_close")
            }
        }
        if (reasoningOpen.containsMatchIn(text)) return null

        sanitize(text)?.let { return AnswerExtraction(it, "plain_answer") }
        return null
    }

    fun isValid(response: String): Boolean = extract(response) != null

    private fun sanitize(value: String): String? {
        val cleaned = value
            .replace(answerClose, " ")
            .replace(Regex("(?is)</?\\s*(?:answer|respuesta|final_answer|reasoning|razonamiento|think|analysis)\\s*:?\\s*>"), " ")
            .trim()
        return cleaned.takeIf { it.length >= 5 && it.any(Char::isLetterOrDigit) }
    }
}
