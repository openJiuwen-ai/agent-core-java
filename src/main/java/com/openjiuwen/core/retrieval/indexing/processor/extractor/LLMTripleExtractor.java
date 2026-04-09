  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * LLM-backed triple extractor aligned with the Python implementation.
 */
public class LLMTripleExtractor extends Extractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BaseModelClient llmClient;
    private final String modelName;
    private final float temperature;
    private final int maxConcurrent;

    public LLMTripleExtractor(BaseModelClient llmClient, String modelName) {
        this(llmClient, modelName, 0.0f, 50);
    }

    public LLMTripleExtractor(BaseModelClient llmClient, String modelName, float temperature, int maxConcurrent) {
        if (llmClient == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RETRIEVER_LLM_CLIENT_NOT_FOUND, "llm_client is required");
        }
        this.llmClient = llmClient;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxConcurrent = maxConcurrent <= 0 ? 1 : maxConcurrent;
    }

    @Override
    public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<Triple> triples = new CopyOnWriteArrayList<>();
        List<String> failedChunks = new CopyOnWriteArrayList<>();
        Semaphore limiter = new Semaphore(maxConcurrent);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (TextChunk chunk : chunks) {
                tasks.add(executor.submit(() -> {
                    limiter.acquireUninterruptibly();
                    try {
                        triples.addAll(extractChunk(chunk));
                    } catch (Exception ex) {
                        failedChunks.add(chunk.getId());
                    } finally {
                        limiter.release();
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } catch (Exception ex) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                    "triple extraction execution failed: " + ex.getMessage());
        }
        if (!failedChunks.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                    "triple extraction failed for chunks: " + String.join(", ", failedChunks));
        }
        return List.copyOf(triples);
    }

    private List<Triple> extractChunk(TextChunk chunk) throws Exception {
        String title = String.valueOf(chunk.getMetadata().getOrDefault("title", ""));
        String prompt = """
                Extract entities and relationships from the following passage.
                Return JSON only. Use either an array of triples or an object with a "triples" field.
                Each triple should be ["subject", "predicate", "object"] or ["subject", "predicate", "object", confidence].

                Passage:
                %s

                Title: %s
                """.formatted(chunk.getText(), title.isBlank() ? "Untitled" : title);
        AssistantMessage response = llmClient.invoke(
                List.of(Map.of("role", "user", "content", prompt)),
                null,
                temperature,
                null,
                modelName,
                null,
                null,
                null,
                null,
                Collections.emptyMap());
        return parseTriples(response == null ? "" : response.getContentAsString(), chunk);
    }

    private List<Triple> parseTriples(String content, TextChunk chunk) throws Exception {
        JsonNode root = MAPPER.readTree(repairJson(extractJson(content)));
        JsonNode triplesNode = root.isObject() ? root.path("triples") : root;
        if (!triplesNode.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                    "LLM triple response is not a JSON array");
        }
        List<Triple> triples = new ArrayList<>();
        for (JsonNode node : triplesNode) {
            if (!node.isArray() || node.size() < 3) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
            metadata.put("doc_id", chunk.getDocId());
            metadata.put("chunk_id", chunk.getId());
            triples.add(new Triple(
                    node.get(0).asText(),
                    node.get(1).asText(),
                    node.get(2).asText(),
                    node.size() > 3 && node.get(3).isNumber() ? node.get(3).doubleValue() : null,
                    metadata));
        }
        return triples;
    }

    private static String extractJson(String content) {
        if (content == null) {
            return "[]";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            String[] lines = trimmed.split("\\R");
            if (lines.length >= 3) {
                trimmed = String.join("\n", List.of(lines).subList(1, lines.length - 1));
            }
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart && (objectStart < 0 || arrayStart < objectStart)) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private static String repairJson(String json) {
        return json == null ? "" : json.replaceAll(",\\s*([}\\]])", "$1");
    }
}
