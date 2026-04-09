/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * JSON file parser that returns formatted JSON text when possible.
 */
public class JsonParser extends Parser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        try {
            String content = Files.readString(Path.of(doc), StandardCharsets.UTF_8);
            return List.of(new Document(docId, formatJson(content), Map.of()));
        } catch (IOException ex) {
            return List.of();
        }
    }

    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
    }

    @Override
    public boolean supports(String doc) {
        return doc != null && doc.toLowerCase().endsWith(".json");
    }

    private static String formatJson(String rawJson) {
        try {
            Object value = MAPPER.readValue(rawJson, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return rawJson;
        }
    }
}
