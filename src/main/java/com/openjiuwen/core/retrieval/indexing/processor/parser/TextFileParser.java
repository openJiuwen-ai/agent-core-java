/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Simple UTF-8 text file parser.
 */
public class TextFileParser extends Parser {

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected CompletableFuture<String> parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        Path path = Path.of(doc);
        if (!Files.exists(path)) {
            return CompletableFuture.failedFuture(
                    RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND, "file not found: " + doc));
        }
        try {
            return CompletableFuture.completedFuture(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(
                    RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND, e.getMessage()));
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean supports(String doc) {
        String lower = doc == null ? "" : doc.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }
}
