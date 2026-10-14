package edu.unicauca.app.agrochat.rag

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NpyFloatMatrix private constructor(
    val rows: Int,
    val columns: Int,
    private val directBuffer: ByteBuffer
) {
    private val floatBuffer = directBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()

    fun dot(row: Int, vector: FloatArray): Float {
        require(vector.size == columns)
        var sum = 0f
        val offset = row * columns
        for (i in vector.indices) {
            sum += floatBuffer.get(offset + i) * vector[i]
        }
        return sum
    }

    fun dotAll(vector: FloatArray): FloatArray {
        require(vector.size == columns)
        val scores = FloatArray(rows)
        if (nativeLoaded) {
            try {
                nativeDotProductAll(directBuffer, 0, rows, columns, vector, scores)
                return scores
            } catch (_: Throwable) {
                // Fallback a JVM si el método nativo falla
            }
        }

        for (r in 0 until rows) {
            var sum = 0f
            val offset = r * columns
            for (c in 0 until columns) {
                sum += floatBuffer.get(offset + c) * vector[c]
            }
            scores[r] = sum
        }
        return scores
    }

    companion object {
        @Volatile
        private var nativeLoaded: Boolean = try {
            System.loadLibrary("llama-android")
            true
        } catch (_: Throwable) {
            false
        }

        @JvmStatic
        private external fun nativeDotProductAll(
            directBuffer: ByteBuffer,
            dataOffsetBytes: Int,
            rows: Int,
            cols: Int,
            queryVec: FloatArray,
            outScores: FloatArray
        )

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

            val totalBytes = rows * columns * 4
            val direct = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
            input.readFullyToBuffer(direct, totalBytes)
            return NpyFloatMatrix(rows, columns, direct)
        }

        private fun InputStream.readFully(target: ByteArray) {
            var offset = 0
            while (offset < target.size) {
                val count = read(target, offset, target.size - offset)
                if (count < 0) error("Unexpected end of NPY file")
                offset += count
            }
        }

        private fun InputStream.readFullyToBuffer(target: ByteBuffer, totalBytes: Int) {
            val chunk = ByteArray(minOf(totalBytes, 32768))
            var remaining = totalBytes
            while (remaining > 0) {
                val toRead = minOf(remaining, chunk.size)
                val readCount = read(chunk, 0, toRead)
                if (readCount < 0) error("Unexpected end of NPY file")
                target.put(chunk, 0, readCount)
                remaining -= readCount
            }
            target.position(0)
        }
    }
}
