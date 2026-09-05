package edu.unicauca.app.agrochat.rag

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NpyFloatMatrix private constructor(
    val rows: Int,
    val columns: Int,
    private val values: FloatArray
) {
    fun dot(row: Int, vector: FloatArray): Float {
        require(vector.size == columns)
        var sum = 0f
        var offset = row * columns
        for (i in vector.indices) sum += values[offset++] * vector[i]
        return sum
    }

    companion object {
        fun read(source: InputStream): NpyFloatMatrix {
            val input = BufferedInputStream(source)
            val magic = ByteArray(6).also { input.readFully(it) }
            require(magic.contentEquals(byteArrayOf(0x93.toByte(), 0x4e, 0x55, 0x4d, 0x50, 0x59))) { "Invalid NPY file" }
            val major = input.read()
            input.read() // minor
            val lengthBytes = if (major >= 2) 4 else 2
            val lengthBuffer = ByteArray(lengthBytes).also { input.readFully(it) }
            val headerLength = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.LITTLE_ENDIAN).let {
                if (lengthBytes == 2) it.short.toInt() and 0xffff else it.int
            }
            val header = ByteArray(headerLength).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            require("<f4" in header || "'f4" in header) { "Expected little-endian float32 NPY" }
            require("True" !in Regex("fortran_order['\"]?\\s*:\\s*(True|False)").find(header)?.value.orEmpty()) {
                "Fortran-order matrices are unsupported"
            }
            val shape = Regex("\\((\\d+)\\s*,\\s*(\\d+)\\s*\\)").find(header)
                ?: error("Expected a two-dimensional NPY shape")
            val rows = shape.groupValues[1].toInt()
            val columns = shape.groupValues[2].toInt()
            val raw = ByteArray(rows * columns * 4).also { input.readFully(it) }
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return NpyFloatMatrix(rows, columns, FloatArray(rows * columns).also(buffer::get))
        }

        private fun InputStream.readFully(target: ByteArray) {
            var offset = 0
            while (offset < target.size) {
                val count = read(target, offset, target.size - offset)
                if (count < 0) error("Unexpected end of NPY file")
                offset += count
            }
        }
    }
}
