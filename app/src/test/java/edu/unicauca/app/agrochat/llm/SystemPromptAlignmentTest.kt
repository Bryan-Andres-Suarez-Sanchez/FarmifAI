package edu.unicauca.app.agrochat.llm

import edu.unicauca.app.agrochat.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptAlignmentTest {

    @Test
    fun defaultTrainingSystemPromptContainsMandatoryStructure() {
        val prompt = MainActivity.DEFAULT_TRAINING_SYSTEM_PROMPT
        assertTrue(prompt.contains("Eres un asistente agrícola"))
        assertTrue(prompt.contains("<knowledge>"))
        assertTrue(prompt.contains("<reasoning>"))
        assertTrue(prompt.contains("</reasoning>"))
        assertTrue(prompt.contains("<answer>"))
        assertTrue(prompt.contains("</answer>"))
        assertTrue(prompt.contains("[Tu análisis aquí]"))
        assertTrue(prompt.contains("[Tu respuesta aquí]"))
    }

    @Test
    fun llamaServiceDefaultMatchesMainActivityDefault() {
        assertEquals(MainActivity.DEFAULT_TRAINING_SYSTEM_PROMPT, LlamaService.DEFAULT_TRAINING_SYSTEM_PROMPT)
    }

    @Test
    fun trainingPromptOutputIsValidatedByAnswerXmlValidator() {
        val simulatedOutput = "<reasoning>\n1. El cultivo de café requiere sombra en etapas iniciales.\n2. La pregunta pide cuidados de siembra.\n</reasoning>\n<answer>\nPara la siembra de café, proporcione sombra regulada y mantenga el suelo húmedo sin encharcamiento.\n</answer>"
        val extracted = AnswerXmlValidator.extract(simulatedOutput)
        val reasoning = AnswerXmlValidator.extractReasoning(simulatedOutput)

        assertEquals(
            "Para la siembra de café, proporcione sombra regulada y mantenga el suelo húmedo sin encharcamiento.",
            extracted?.answer
        )
        assertEquals(
            "1. El cultivo de café requiere sombra en etapas iniciales.\n2. La pregunta pide cuidados de siembra.",
            reasoning
        )
    }
}
