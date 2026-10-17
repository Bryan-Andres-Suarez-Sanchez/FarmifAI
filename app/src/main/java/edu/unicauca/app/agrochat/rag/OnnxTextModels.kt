package edu.unicauca.app.agrochat.rag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import edu.unicauca.app.agrochat.UniversalNativeTokenizer
import java.nio.LongBuffer
import java.util.EnumSet

internal data class EncodedInput(val ids: LongArray, val mask: LongArray, val types: LongArray)

internal class OnnxTextSession(
    modelPath: String,
    private val tokenizer: UniversalNativeTokenizer,
    private val maxLength: Int,
    private val enableHardwareAcceleration: Boolean = false
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(4)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        if (enableHardwareAcceleration) {
            try {
                addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
            } catch (e: Exception) {
                Log.w("OnnxTextSession", "NNAPI no disponible, usando CPU optimizada: ${e.message}")
            }
        }
    }
    private val session = environment.createSession(modelPath, options)
    private val supportsDynamicSequence: Boolean = try {
        val inputInfo = session.inputInfo["input_ids"]?.info
        if (inputInfo is TensorInfo) {
            val shape = inputInfo.shape
            shape.size >= 2 && shape[1] <= 0
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    fun encodeSingle(text: String): FloatArray {
        val input = tokenizeSingle(text)
        val output = run(input)
        if (output.size == RagConfig.EMBEDDING_DIMENSION) return output
        val seqLength = input.ids.size
        require(output.size == seqLength * RagConfig.EMBEDDING_DIMENSION) {
            "Unexpected E5 output size: ${output.size}, expected: ${seqLength * RagConfig.EMBEDDING_DIMENSION}"
        }
        // SentenceTransformers' multilingual-e5-small pooling: masked mean of
        // last_hidden_state. Normalization is applied by SemanticRetriever.
        val pooled = FloatArray(RagConfig.EMBEDDING_DIMENSION)
        var tokens = 0
        for (token in 0 until seqLength) {
            if (input.mask[token] == 0L) continue
            val offset = token * RagConfig.EMBEDDING_DIMENSION
            for (dimension in pooled.indices) pooled[dimension] += output[offset + dimension]
            tokens++
        }
        require(tokens > 0) { "E5 tokenizer produced an empty attention mask" }
        for (dimension in pooled.indices) pooled[dimension] /= tokens.toFloat()
        return pooled
    }

    fun scorePair(query: String, passage: String): Float = run(tokenizePair(query, passage)).first()

    private fun tokenizeSingle(text: String): EncodedInput {
        val raw = tokenizer.encode(text, addSpecialTokens = true).take(maxLength)
        return pad(raw.toIntArray(), IntArray(raw.size))
    }

    /** Reconstructs XLM-R pair layout: <s> A </s></s> B </s>, longest-first truncation. */
    private fun tokenizePair(query: String, passage: String): EncodedInput {
        val encodedFirst = tokenizer.encode(query, addSpecialTokens = true)
        val encodedSecond = tokenizer.encode(passage, addSpecialTokens = true)
        require(encodedFirst.size >= 2 && encodedSecond.size >= 2) { "Tokenizer omitted XLM-R special tokens" }
        val bos = encodedFirst.first()
        val first = encodedFirst.drop(1).dropLast(1).toMutableList()
        val second = encodedSecond.drop(1).dropLast(1).toMutableList()
        while (first.size + second.size + 4 > maxLength) {
            val target = if (first.size > second.size) first else second
            if (target.isNotEmpty()) target.removeAt(target.lastIndex) else break
        }
        val ids = (listOf(bos) + first + tokenizer.eosTokenId + tokenizer.eosTokenId + second + tokenizer.eosTokenId).toIntArray()
        // XLM-R does not distinguish sentence segments with token-type IDs.
        val types = IntArray(ids.size)
        return pad(ids, types)
    }

    private fun pad(ids: IntArray, types: IntArray): EncodedInput {
        val targetLength = if (supportsDynamicSequence && !optionsHardwareAccelerated()) {
            maxOf(1, minOf(ids.size, maxLength))
        } else {
            maxLength
        }
        val paddedIds = LongArray(targetLength) { tokenizer.padTokenId.toLong() }
        val mask = LongArray(targetLength)
        val paddedTypes = LongArray(targetLength)
        val limit = minOf(ids.size, targetLength)
        for (index in 0 until limit) {
            paddedIds[index] = ids[index].toLong()
            mask[index] = 1L
            paddedTypes[index] = types[index].toLong()
        }
        return EncodedInput(paddedIds, mask, paddedTypes)
    }

    private fun optionsHardwareAccelerated(): Boolean = enableHardwareAcceleration

    private fun run(input: EncodedInput): FloatArray {
        val shape = longArrayOf(1, input.ids.size.toLong())
        OnnxTensor.createTensor(environment, LongBuffer.wrap(input.ids), shape).use { ids ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(input.mask), shape).use { mask ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(input.types), shape).use { types ->
                    val tensors = mutableMapOf<String, OnnxTensor>()
                    session.inputNames.firstOrNull { it == "input_ids" }?.let { tensors[it] = ids }
                    session.inputNames.firstOrNull { it == "attention_mask" }?.let { tensors[it] = mask }
                    session.inputNames.firstOrNull { it == "token_type_ids" }?.let { tensors[it] = types }
                    session.run(tensors).use { result -> return flatten(result[0].value) }
                }
            }
        }
    }

    private fun flatten(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flatten(requireNotNull(it)).asIterable() }.toFloatArray()
        is Number -> floatArrayOf(value.toFloat())
        else -> error("Unsupported ONNX output: ${value.javaClass.name}")
    }

    override fun close() {
        session.close()
        options.close()
        tokenizer.release()
    }
}
