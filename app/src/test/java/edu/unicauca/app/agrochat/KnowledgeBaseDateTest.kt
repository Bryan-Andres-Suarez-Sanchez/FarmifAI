package edu.unicauca.app.agrochat

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeBaseDateTest {
    @Test fun formatsAvailableMonthAndYear() = assertEquals("dic. 2024", formatKnowledgeBaseDate("12 2024"))
    @Test fun omitsUnknownMonth() = assertEquals("2024", formatKnowledgeBaseDate("00 2024"))
    @Test fun omitsUnknownYear() = assertEquals("dic.", formatKnowledgeBaseDate("12 0000"))
    @Test fun omitsCompletelyUnknownDate() = assertEquals("", formatKnowledgeBaseDate("00 0000"))
}
