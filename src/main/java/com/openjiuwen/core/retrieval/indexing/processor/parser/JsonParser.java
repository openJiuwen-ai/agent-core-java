/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local file parser for JSON format.
 *
 * <p>Mirrors Python's {@code JSONParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/json_parser.py}.</p>
 */
public class JsonParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Mirrors Python's {@code JSONParser._parse} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/json_parser.py}.
     */
    @Override
    protected CompletableFuture<String> parseContent(
            String filePath,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String rawContent = readUtf8IgnoringErrors(Path.of(filePath));
                return formatJsonOrOriginal(rawContent, filePath);
            } catch (Exception exception) {
                LOGGER.error("Failed to parse JSON {}: {}", filePath, exception.getMessage());
                return null;
            }
        });
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null || doc.isBlank()) {
            return false;
        }
        String fileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".json");
    }

    private static String formatJsonOrOriginal(String rawContent, String filePath) {
        try {
            Object jsonData = OBJECT_MAPPER.readValue(rawContent, Object.class);
            if (jsonData instanceof Map<?, ?> map && map.isEmpty()) {
                return "{}";
            }
            if (jsonData instanceof Collection<?> collection && collection.isEmpty()) {
                return "[]";
            }
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonData);
        } catch (JsonProcessingException exception) {
            LOGGER.error("JSON format error: {}", filePath);
            return rawContent;
        }
    }

    private static String readUtf8IgnoringErrors(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
