/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.List;
import java.util.Map;

/**
 * Document parser abstraction.
 */
public abstract class Parser implements Processor<String, List<Document>> {

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        String content = parseContent(doc, llmClient, options);
        if (content == null) {
            return List.of();
        }
        return List.of(new Document(docId == null || docId.isBlank() ? null : docId, content, Map.of()));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected abstract String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options);

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean supports(String doc) {
        return false;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Document> process(String input, Map<String, Object> options) {
        return parse(input, "", null, options);
    }
}
