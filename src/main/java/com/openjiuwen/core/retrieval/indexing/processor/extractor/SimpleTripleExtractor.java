/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lightweight local triple extractor based on sentence tokenization.
 */
public class SimpleTripleExtractor extends Extractor {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?。！？])\\s+");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options) {
        List<Triple> triples = new ArrayList<>();
        if (chunks == null) {
            return triples;
        }
        for (TextChunk chunk : chunks) {
            String[] sentences = SENTENCE_SPLIT.split(chunk.getText());
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                String subject = parts[0];
                String predicate = parts[1];
                String object = String.join(" ", List.of(parts).subList(2, parts.length));
                Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
                metadata.put("doc_id", chunk.getDocId());
                metadata.put("chunk_id", chunk.getId());
                metadata.put("triple", serializeTriple(subject, predicate, object));
                triples.add(new Triple(subject, predicate, object, null, metadata));
            }
        }
        return triples;
    }

    private static String serializeTriple(String subject, String predicate, String object) {
        try {
            return MAPPER.writeValueAsString(List.of(subject, predicate, object));
        } catch (JsonProcessingException e) {
            return "[\"" + subject + "\",\"" + predicate + "\",\"" + object + "\"]";
        }
    }
}
