package edu.unicauca.app.agrochat.rag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import edu.unicauca.app.agrochat.UniversalNativeTokenizer
import java.nio.LongBuffer

internal data class EncodedInput(val ids: LongArray, val mask: LongArray, val types: LongArray)

internal class OnnxTextSession(
    modelPath: String,
    private val tokenizer: UniversalNativeTokenizer,
    private val maxLength: Int
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val session = environment.createSession(modelPath, options)

    fun encodeSingle(text: String): FloatArray {
        val input = tokenizeSingle(text)
        val output = run(input)
        if (output.size == RagConfig.EMBEDDING_DIMENSION) return output
        require(output.size == maxLength * RagConfig.EMBEDDING_DIMENSION) {
            "Unexpected E5 output size: ${output.size}"
        }
        // SentenceTransformers' multilingual-e5-small pooling: masked mean of
        // last_hidden_state. Normalization is applied by SemanticRetriever.
        val pooled = FloatArray(RagConfig.EMBEDDING_DIMENSION)
        var tokens = 0
        for (token in 0 until maxLength) {
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
        val paddedIds = LongArray(maxLength) { tokenizer.padTokenId.toLong() }
        val mask = LongArray(maxLength)
        val paddedTypes = LongArray(maxLength)
        ids.take(maxLength).forEachIndexed { index, id ->
            paddedIds[index] = id.toLong()
            mask[index] = 1L
            paddedTypes[index] = types[index].toLong()
        }
        return EncodedInput(paddedIds, mask, paddedTypes)
    }

    private fun run(input: EncodedInput): FloatArray {
        val shape = longArrayOf(1, maxLength.toLong())
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
