package edu.unicauca.app.agrochat.rag

import android.content.Context
import org.json.JSONObject

class ChunkStore(private val chunks: List<RagChunk>) {
    fun all(): List<RagChunk> = chunks
    operator fun get(index: Int): RagChunk = chunks[index]

    companion object {
        fun fromAsset(context: Context, assetName: String = RagConfig.CHUNKS_ASSET): ChunkStore {
            val root = JSONObject(context.assets.open(assetName).bufferedReader().use { it.readText() })
            val array = root.getJSONArray("chunks")
            val chunks = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                RagChunk(
                    index = index,
                    documentId = item.get("document_id").toString(),
                    // The notebook KB does not require chunk_number. Preserve it when
                    // present and otherwise use the stable JSON-array position.
                    chunkNumber = item.opt("chunk_number")?.toString() ?: index.toString(),
                    text = item.getString("text"),
                    citation = item.optString("citation", item.optString("fuente", "Fuente no especificada")),
                    date = item.optString("date", "")
                )
            }
            return ChunkStore(chunks)
        }
    }
}
