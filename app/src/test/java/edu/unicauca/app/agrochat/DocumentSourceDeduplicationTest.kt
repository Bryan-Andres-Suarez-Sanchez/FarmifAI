package edu.unicauca.app.agrochat

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSourceDeduplicationTest {
    @Test
    fun keepsOnlyOneSourceWhenTwoChunksHaveTheSameCitation() {
        val sources = listOf(
            ChunkSource("Autor. Titulo del documento.", "dic. 2024"),
            ChunkSource("Autor. Titulo del documento.", "dic. 2024"),
            ChunkSource("Otro autor. Otro documento.", "2023")
        )

        val result = deduplicateDocumentSources(sources)

        assertEquals(2, result.size)
        assertEquals("Autor. Titulo del documento.", result[0].citation)
        assertEquals("Otro autor. Otro documento.", result[1].citation)
    }

    @Test
    fun ignoresWhitespaceAndCapitalizationDifferencesInTheSameCitation() {
        val sources = listOf(
            ChunkSource("Autor. Titulo del documento.", "2024"),
            ChunkSource("  AUTOR.   TITULO DEL DOCUMENTO. ", "2024")
        )

        assertEquals(1, deduplicateDocumentSources(sources).size)
    }
}
