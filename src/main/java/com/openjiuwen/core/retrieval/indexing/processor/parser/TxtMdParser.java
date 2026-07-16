/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TXT/MD file parser aligned with the Python TxtMdParser behavior.
 * 
 * @since 0.1.7
 */
public class TxtMdParser extends Parser {
    /**
     * parse.
     * 
     * @param doc doc
     * @param docId docId
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        try {
            String content = parseContent(doc, llmClient, options);
            if (content == null) {
                return List.of();
            }
            return List.of(new Document(docId, content, Map.of()));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /**
     * parseContent.
     * 
     * @param doc doc
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        try {
            return Files.readString(Path.of(doc), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * supports.
     * 
     * @param doc doc
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean supports(String doc) {
        String lower = doc == null ? "" : doc.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }
}
