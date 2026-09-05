package edu.unicauca.app.agrochat.rag

object ContextBuilder {
    fun build(chunks: List<RetrievedChunk>): String = chunks.distinctBy { it.chunk.index }.joinToString("\n\n") {
        "Documento: ${it.chunk.documentId} (Fragmento ${it.chunk.chunkNumber})\nContenido: ${it.chunk.text}"
    }
}
