/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * vLLM-compatible multimodal embedding client.
 * <p>
 * Mirrors Python's {@code VLLMEmbedding} in
 * {@code openjiuwen/core/retrieval/embedding/vllm_embedding.py}.
 * </p>
 */
public class VLLMEmbedding extends OpenAIEmbedding {

    private static final String DEFAULT_INSTRUCTION = "Represent the user's input.";

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

    public static Map<String, Object> parseMultimodalInput(MultimodalDocument document,
                                                           Map<String, Object> options) {
        Map<String, Object> kwargs = options == null ? new LinkedHashMap<>() : options;
        Object instruction = DEFAULT_INSTRUCTION;
        try {
            if (kwargs.containsKey("instruction")) {
                instruction = kwargs.remove("instruction");
            }
        } catch (RuntimeException exception) {
            if (!exception.getClass().getSimpleName().startsWith("UnsupportedOperation")) {
                throw exception;
            }
            kwargs = new LinkedHashMap<>(kwargs);
            instruction = kwargs.remove("instruction");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        if (instruction != null) {
            messages.add(Map.of(
                    "role", "system",
                    "content", List.of(Map.of("type", "text", "text", instruction))
            ));
        }
        messages.add(Map.of("role", "user", "content", document.getContent()));

        try {
            kwargs.put("extra_body", Map.of("messages", messages));
        } catch (RuntimeException exception) {
            if (!exception.getClass().getSimpleName().startsWith("UnsupportedOperation")) {
                throw exception;
            }
            kwargs = new LinkedHashMap<>(kwargs);
            kwargs.put("extra_body", Map.of("messages", messages));
        }
        return kwargs;
    }

    public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document) {
        return embedMultimodal(document, new LinkedHashMap<>());
    }

    public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document,
                                                           Map<String, Object> options) {
        return embedMultimodal((Object) document, options);
    }

    public CompletableFuture<List<Double>> embedMultimodal(Object input,
                                                           Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> embedMultimodalSync(input, options), executor);
    }

    public List<Double> embedMultimodalSync(MultimodalDocument document) {
        return embedMultimodalSync(document, new LinkedHashMap<>());
    }

    public List<Double> embedMultimodalSync(MultimodalDocument document,
                                            Map<String, Object> options) {
        return embedMultimodalSync((Object) document, options);
    }

    public List<Double> embedMultimodalSync(Object input,
                                            Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "error_msg",
                    "input provided for multimodal embedding is not a MultimodalDocument"
            );
        }
        Map<String, Object> kwargs = parseMultimodalInput(document, options);
        return getEmbeddingsSync(null, kwargs).get(0);
    }
}
