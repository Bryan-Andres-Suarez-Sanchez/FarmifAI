package edu.unicauca.app.agrochat.rag

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RagPipelineSmokeTest {
    @Test
    fun executesEveryRetrievalStageForEveryQuery() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        QueryTelemetry.logSystemInfo(context)
        RagPipeline.load(context).use { pipeline ->
            val queries = listOf(
                "¿Cómo fertilizar un cafetal zoqueado?",
                "¿Cuántos chupones dejar después de zocar?"
            )
            queries.forEach { query ->
                val id = QueryTelemetry.begin(query)
                val result = pipeline.query(query, id)
                assertEquals(RagConfig.LEXICAL_TOP_K, result.bm25.size)
                assertEquals(RagConfig.SEMANTIC_TOP_K, result.semantic.size)
                assertTrue(result.fused.isNotEmpty())
                assertEquals(RagConfig.FINAL_TOP_K, result.reranked.size)
                assertTrue(result.context.isNotBlank())
                QueryTelemetry.finish(id, success = true)
            }
        }
    }
}
