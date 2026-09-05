package edu.unicauca.app.agrochat.rag

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextBuilderTest {
    @Test
    fun formatsNotebookContextAndRemovesDuplicateChunks() {
        val chunk = RagChunk(3, "doc.pdf", "7", "Texto literal")
        val context = ContextBuilder.build(listOf(RetrievedChunk(chunk, 2f), RetrievedChunk(chunk, 1f)))
        assertEquals("Documento: doc.pdf (Fragmento 7)\nContenido: Texto literal", context)
    }
}
