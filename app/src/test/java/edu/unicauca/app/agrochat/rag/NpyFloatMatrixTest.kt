package edu.unicauca.app.agrochat.rag

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NpyFloatMatrixTest {

    @Test
    fun testNpyReadAndDotProduct() {
        val header = "{'descr': '<f4', 'fortran_order': False, 'shape': (2, 3), }\n"
        val headerBytes = header.toByteArray(Charsets.US_ASCII)
        val dataBytes = 2 * 3 * 4
        val totalLen = 6 + 2 + 2 + headerBytes.size + dataBytes

        val buffer = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        // Magic: \x93NUMPY
        buffer.put(byteArrayOf(0x93.toByte(), 0x4e, 0x55, 0x4d, 0x50, 0x59))
        // Version: 1.0
        buffer.put(1.toByte()).put(0.toByte())
        // Header length (uint16)
        buffer.putShort(headerBytes.size.toShort())
        // Header string
        buffer.put(headerBytes)
        // Matrix data (float32 little-endian):
        // row 0: [1.0, 2.0, 3.0]
        // row 1: [4.0, 5.0, 6.0]
        buffer.putFloat(1.0f).putFloat(2.0f).putFloat(3.0f)
        buffer.putFloat(4.0f).putFloat(5.0f).putFloat(6.0f)

        val stream = ByteArrayInputStream(buffer.array())
        val matrix = NpyFloatMatrix.read(stream)

        assertEquals(2, matrix.rows)
        assertEquals(3, matrix.columns)

        val query = floatArrayOf(0.5f, 1.0f, 2.0f)
        // Expected row 0: 1.0*0.5 + 2.0*1.0 + 3.0*2.0 = 0.5 + 2.0 + 6.0 = 8.5
        // Expected row 1: 4.0*0.5 + 5.0*1.0 + 6.0*2.0 = 2.0 + 5.0 + 12.0 = 19.0
        assertEquals(8.5f, matrix.dot(0, query), 1e-5f)
        assertEquals(19.0f, matrix.dot(1, query), 1e-5f)

        val allScores = matrix.dotAll(query)
        assertEquals(2, allScores.size)
        assertEquals(8.5f, allScores[0], 1e-5f)
        assertEquals(19.0f, allScores[1], 1e-5f)
    }
}
