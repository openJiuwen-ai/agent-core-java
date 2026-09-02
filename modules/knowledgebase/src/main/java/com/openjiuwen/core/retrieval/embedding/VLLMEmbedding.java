/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * vLLM-compatible multimodal embedding client.
 * 
 * @since 0.1.7
 */
public class VLLMEmbedding extends OpenAIEmbedding {
    /**
     * VLLMEmbedding.
     * 
     * @param config config
     * @since 0.1.7
     */
    public VLLMEmbedding(EmbeddingConfig config) {
        super(config);
    }

    /**
     * VLLMEmbedding.
     * 
     * @param config config
     * @param timeout timeout
     * @param maxRetries maxRetries
     * @param extraHeaders extraHeaders
     * @param maxBatchSize maxBatchSize
     * @param maxConcurrent maxConcurrent
     * @param dimension dimension
     * @param httpClient httpClient
     * @since 0.1.7
     */
    public VLLMEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders,
            int maxBatchSize, int maxConcurrent, Integer dimension, HttpClient httpClient) {
        super(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, dimension, httpClient);
    }

    /**
     * parseMultimodalInput.
     * 
     * @param document document
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> parseMultimodalInput(MultimodalDocument document, Map<String, Object> options) {
        boolean hasInstruction = options != null && options.containsKey("instruction");
        Map<String, Object> kwargs = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
        Object instruction = kwargs.remove("instruction");
        if (!hasInstruction) {
            instruction = "Represent the user's input.";
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        if (instruction instanceof String text && !text.isBlank()) {
            messages.add(Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", text))));
        }
        messages.add(Map.of("role", "user", "content", document.getContent()));
        kwargs.put("extra_body", Map.of("messages", messages));
        return kwargs;
    }

    /**
     * embedMultimodal.
     * 
     * @param document document
     * @return the result
     * @since 0.1.7
     */
    public List<Float> embedMultimodal(MultimodalDocument document) {
        return embedMultimodal(document, new LinkedHashMap<>());
    }

    /**
     * embedMultimodal.
     * 
     * @param input input
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public List<Float> embedMultimodal(Object input, Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "input provided for multimodal embedding is not a MultimodalDocument");
        }
        Map<String, Object> kwargs = parseMultimodalInput(document, options);
        List<List<Float>> embeddings = getEmbeddings(null, kwargs);
        return embeddings.get(0);
    }

    /**
     * embedMultimodal.
     * 
     * @param document document
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public List<Float> embedMultimodal(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodal((Object) document, options);
    }

    /**
     * embedMultimodalSync.
     * 
     * @param document document
     * @return the result
     * @since 0.1.7
     */
    public List<Float> embedMultimodalSync(MultimodalDocument document) {
        return embedMultimodalSync(document, new LinkedHashMap<>());
    }

    /**
     * embedMultimodalSync.
     * 
     * @param input input
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public List<Float> embedMultimodalSync(Object input, Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "input provided for multimodal embedding is not a MultimodalDocument");
        }
        Map<String, Object> kwargs = parseMultimodalInput(document, options == null ? new LinkedHashMap<>() : options);
        List<List<Float>> embeddings = getEmbeddings(null, kwargs);
        return embeddings.get(0);
    }

    /**
     * embedMultimodalSync.
     * 
     * @param document document
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public List<Float> embedMultimodalSync(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodalSync((Object) document, options);
    }
}
