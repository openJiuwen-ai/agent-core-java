/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code Parser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/base.py}.
 */
public abstract class Parser implements Processor<List<Document>> {

    public CompletableFuture<List<Document>> parse(String doc) {
        return parse(doc, "", null, Map.of());
    }

    public CompletableFuture<List<Document>> parse(String doc, String docId) {
        return parse(doc, docId, null, Map.of());
    }

    public CompletableFuture<List<Document>> parse(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return parseContent(doc, llmClient, options == null ? Map.of() : options)
                .thenApply(content -> {
                    if (content == null || content.isEmpty()) {
                        return List.of();
                    }
                    return List.of(new Document(docId, content, Map.of()));
                });
    }

    protected CompletableFuture<String> parseContent(String filePath, BaseModelClient llmClient) {
        return parseContent(filePath, llmClient, Map.of());
    }

    protected CompletableFuture<String> parseContent(
            String filePath,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<Document>> lazyParse(String doc, String docId, Map<String, Object> options) {
        return parse(doc, docId, null, options);
    }

    @Override
    public CompletableFuture<List<Document>> process(Object... args) {
        String doc = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
        String docId = args != null && args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
        BaseModelClient llmClient = args != null && args.length > 2 && args[2] instanceof BaseModelClient client
                ? client
                : null;
        Map<String, Object> options = args != null && args.length > 3 && args[3] instanceof Map<?, ?> map
                ? copyStringMap(map)
                : Map.of();
        return parse(doc, docId, llmClient, options);
    }

    public boolean supports(String doc) {
        return false;
    }

    private static Map<String, Object> copyStringMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> copied = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
    }
}
