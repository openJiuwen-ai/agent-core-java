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
 * <p>Mirrors Python's {@code VLLMEmbedding} in
 * {@code openjiuwen.core.retrieval.embedding.vllm_embedding}.</p>
 */
public class VLLMEmbedding extends OpenAIEmbedding {

    public VLLMEmbedding(EmbeddingConfig config) {
        super(config);
    }

    public VLLMEmbedding(EmbeddingConfig config,
                         int timeout,
                         int maxRetries,
                         Map<String, String> extraHeaders,
                         int maxBatchSize,
                         int maxConcurrent,
                         Integer dimension,
                         HttpClient httpClient) {
        super(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, dimension, httpClient);
    }

    public static Map<String, Object> parseMultimodalInput(MultimodalDocument document, Map<String, Object> options) {
        Map<String, Object> kwargs = options == null ? new LinkedHashMap<>() : options;
        Object instruction = "Represent the user's input.";
        if (kwargs.containsKey("instruction")) {
            try {
                instruction = kwargs.remove("instruction");
            } catch (UnsupportedOperationException ex) {
                kwargs = new LinkedHashMap<>(kwargs);
                instruction = kwargs.remove("instruction");
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        if (instruction instanceof String text && !text.isBlank()) {
            messages.add(Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", text))));
        }
        messages.add(Map.of("role", "user", "content", document.getContent()));
        try {
            kwargs.put("extra_body", Map.of("messages", messages));
        } catch (UnsupportedOperationException ex) {
            kwargs = new LinkedHashMap<>(kwargs);
            kwargs.put("extra_body", Map.of("messages", messages));
        }
        return kwargs;
    }

    public List<Float> embedMultimodal(MultimodalDocument document) {
        return embedMultimodal(document, new LinkedHashMap<>());
    }

    public List<Float> embedMultimodal(Object input, Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "input provided for multimodal embedding is not a MultimodalDocument");
        }
        Map<String, Object> kwargs = parseMultimodalInput(document, options);
        List<List<Float>> embeddings = getEmbeddings(null, kwargs);
        return embeddings.getFirst();
    }

    public List<Float> embedMultimodal(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodal((Object) document, options);
    }

    public List<Float> embedMultimodalSync(MultimodalDocument document) {
        return embedMultimodalSync(document, new LinkedHashMap<>());
    }

    public List<Float> embedMultimodalSync(Object input, Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "input provided for multimodal embedding is not a MultimodalDocument");
        }
        Map<String, Object> kwargs = parseMultimodalInput(document, options == null ? new LinkedHashMap<>() : options);
        List<List<Float>> embeddings = getEmbeddings(null, kwargs);
        return embeddings.getFirst();
    }

    public List<Float> embedMultimodalSync(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodalSync((Object) document, options);
    }
}
