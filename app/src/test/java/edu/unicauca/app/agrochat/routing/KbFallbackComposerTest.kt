package edu.unicauca.app.agrochat.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KbFallbackComposerTest {

    @Test
    fun `compose builds actionable kb response when top match exists`() {
        val result = KbFallbackComposer.compose(
            KbFallbackComposer.Input(
                userQuery = "como implementar riego por goteo",
                topMatchQuestion = "Riego por goteo",
                topMatchAnswer = "Instala una linea principal y cintas de goteo por surco. " +
                    "Mantiene presion estable con regulador. Programa riegos cortos y frecuentes. " +
                    "Revisa filtros y emisores cada semana para evitar taponamientos.",
                combinedContext = null
            )
        )

        assertNotNull(result)
        assertTrue(result!!.response.contains("Con lo que me cuentas"))
        assertFalse(result.response.contains("KB"))
        assertFalse(result.response.contains("- "))
        assertTrue(result.response.contains("riego"))
        assertFalse(result.response.contains("no pude generar una respuesta confiable"))
    }

    @Test
    fun `compose includes unknown tokens hint when available`() {
        val result = KbFallbackComposer.compose(
            KbFallbackComposer.Input(
                userQuery = "plagas del tomate con control biologico",
                topMatchQuestion = "Plagas del tomate",
                topMatchAnswer = "Monitorea mosca blanca y pulgon. Retira hojas afectadas. " +
                    "Usa trampas cromaticas para vigilancia y reduce malezas hospederas.",
                combinedContext = null,
                unknownQueryTokens = setOf("biologico", "nematodos")
            )
        )

        assertNotNull(result)
        assertTrue(result!!.response.contains("te ajusto mejor la recomendacion"))
        assertTrue(result.response.contains("biologico"))
    }

    @Test
    fun `compose returns null when no kb text is available`() {
        val result = KbFallbackComposer.compose(
            KbFallbackComposer.Input(
                userQuery = "riego por goteo",
                topMatchQuestion = null,
                topMatchAnswer = "",
                combinedContext = "   "
            )
        )

        assertTrue(result == null)
    }
}
