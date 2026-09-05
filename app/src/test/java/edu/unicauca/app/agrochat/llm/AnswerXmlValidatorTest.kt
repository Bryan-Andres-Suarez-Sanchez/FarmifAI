package edu.unicauca.app.agrochat.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerXmlValidatorTest {
    @Test fun extractsStrictXmlWithoutReasoning() {
        assertEquals("Respuesta útil.", AnswerXmlValidator.extract("<reasoning>interno</reasoning><answer>Respuesta útil.</answer>")?.answer)
    }

    @Test fun acceptsMissingAnswerClosingTag() {
        assertEquals("Respuesta incompleta pero útil", AnswerXmlValidator.extract("<answer>Respuesta incompleta pero útil")?.answer)
    }

    @Test fun acceptsLooseFinalAnswerMarker() {
        assertEquals("Use riego por goteo.", AnswerXmlValidator.extract("Respuesta final: Use riego por goteo.")?.answer)
    }

    @Test fun removesThinkAndReasoningBlocks() {
        assertEquals("Aplique compost.", AnswerXmlValidator.extract("<think>secreto</think>Aplique compost.")?.answer)
        assertEquals("Aplique compost.", AnswerXmlValidator.extract("<reasoning>secreto</reasoning><answer>Aplique compost.</answer>")?.answer)
    }

    @Test fun refusesUnclosedReasoningWithoutAnswerBoundary() {
        assertNull(AnswerXmlValidator.extract("<reasoning>todavía estoy razonando internamente"))
    }

    @Test fun streamsOnlyContentAfterAnswerBoundary() {
        val partial = "<reasoning>contenido privado</reasoning><answer>Riegue temprano y revise la humedad del suelo."
        assertEquals(
            "Riegue temprano y revise la humedad del suelo.",
            AnswerXmlValidator.extractStreamingAnswer(partial)
        )
    }

    @Test fun doesNotStreamUnclosedReasoning() {
        assertNull(AnswerXmlValidator.extractStreamingAnswer("<reasoning>contenido privado todavía incompleto"))
    }
}
