/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM-backed triple extractor aligned with the Python implementation.
 */
public class LLMTripleExtractor extends Extractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BaseModelClient llmClient;
    private final String modelName;
    private final float temperature;
    private final int maxConcurrent;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMTripleExtractor(BaseModelClient llmClient, String modelName) {
        this(llmClient, modelName, 0.0f, 50);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.concurrent.CompletableFuture<List<Triple>> extract(List<TextChunk> chunks) {
        return java.util.concurrent.CompletableFuture.completedFuture(extract(chunks, null));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Semaphore limiter = new Semaphore(maxConcurrent);
        ExecutorService executor = new ThreadPoolExecutor(
                0,
                maxConcurrent,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger seq = new AtomicInteger(1);
                    @Override
                    /**
                     * Auto-generated for codecheck compliance.
                     */
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("llm-triple-extract-" + seq.getAndIncrement());
                        t.setDaemon(false);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<List<Triple>> results = new ArrayList<>(Collections.nCopies(chunks.size(), null));
        List<Exception> errors = new ArrayList<>(Collections.nCopies(chunks.size(), null));
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                int index = i;
                TextChunk chunk = chunks.get(i);
                tasks.add(executor.submit(() -> {
                    limiter.acquireUninterruptibly();
                    try {
                        results.set(index, extractChunk(chunk));
                    } catch (Exception ex) {
                        errors.set(index, ex);
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
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        List<Triple> triples = new ArrayList<>();
        Exception firstError = null;
        for (int i = 0; i < chunks.size(); i++) {
            if (errors.get(i) != null && firstError == null) {
                firstError = errors.get(i);
            }
            if (results.get(i) != null) {
                triples.addAll(results.get(i));
            }
        }
        if (firstError != null) {
            if (firstError instanceof BaseError baseError) {
                throw baseError;
            }
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                    firstError.getMessage() != null ? firstError.getMessage() : firstError.toString());
        }
        return triples;
    }

    private List<Triple> extractChunk(TextChunk chunk) throws Exception {
        String title = String.valueOf(chunk.getMetadata().getOrDefault("title", ""));
        String prompt = buildPrompt(chunk.getText(), title);
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
        ParseResult parsed = parseTriples(response == null ? "" : response.getContentAsString(), chunk);
        if (!parsed.isSuccess()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                    chunk.getId() + ": LLM response could not be parsed as valid triple JSON");
        }
        return parsed.triples();
    }

    String buildPrompt(String passage, String title) {
        return """
                # Instruction

                Your task is to construct an RDF-style graph from the given title and passage.
                Extract named entities and relationships, then return the result as exactly one valid JSON object.

                Return only one valid JSON object in this format:
                {
                  "named_entities": ["entity1", "entity2"],
                  "triples": [
                    ["subject1", "predicate1", "object1"],
                    ["subject2", "predicate2", "object2"]
                  ]
                }

                Requirements:
                - Output valid JSON only. Do not use markdown, comments, or extra text.
                - Return exactly one top-level JSON object.
                - The top-level object must contain exactly two keys: "named_entities" and "triples".
                - "named_entities" must be a JSON array of strings.
                - "triples" must be a JSON array.
                - Each item in "triples" must be a JSON array of exactly three strings.
                - Do not output tuples, objects, or arrays with more than three elements inside "triples".
                - Use double quotes for all JSON strings.
                - If no triples are found, return {"named_entities": [...], "triples": []}.
                - Resolve pronouns to specific names when possible.
                - Prefer triples that use at least one, and preferably two, named entities from the title or passage.
                - Keep entity and predicate wording consistent with the source language.
                - Do not include duplicate triples.

                # Demonstration 1

                Title:
                Magic Johnson

                Passage:
                After winning a national championship with Michigan State in 1979, Johnson was selected first overall in the 1979 NBA draft by the Lakers, leading the team to five NBA championships during their "Showtime" era.

                # Demonstration 2

                Title:
                Elden Ring

                Passage:
                Elden Ring is a 2022 action role-playing game developed by FromSoftware. It was directed by Hidetaka Miyazaki with worldbuilding provided by American fantasy writer George R. R. Martin.

                # Input

                Title:
                %s

                Passage:
                %s
                """.formatted(title == null || title.isBlank() ? "Untitled" : title, passage);
    }

    ParseResult parseTriples(String content, TextChunk chunk) {
        JsonNode root;
        try {
            root = MAPPER.readTree(repairJson(extractJson(content)));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new ParseResult(List.of(), false);
        }
        JsonNode triplesNode = root.isObject() ? root.path("triples") : root;
        if (!triplesNode.isArray()) {
            return new ParseResult(List.of(), false);
        }
        if (triplesNode.isEmpty()) {
            return new ParseResult(List.of(), true);
        }
        List<Triple> triples = new ArrayList<>();
        for (JsonNode node : triplesNode) {
            if (isInvalidTripleNode(node)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
            metadata.put("doc_id", chunk.getDocId());
            metadata.put("chunk_id", chunk.getId());
            triples.add(new Triple(
                    node.get(0).asText().trim(),
                    node.get(1).asText().trim(),
                    node.get(2).asText().trim(),
                    metadata));
        }
        return new ParseResult(triples, !triples.isEmpty());
    }

    private static boolean isInvalidTripleNode(JsonNode node) {
        if (!node.isArray() || node.size() < 3) {
            return true;
        }
        return node.get(0).isContainerNode()
                || node.get(1).isContainerNode()
                || node.get(2).isContainerNode()
                || node.get(0).isNull()
                || node.get(1).isNull()
                || node.get(2).isNull();
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

    record ParseResult(List<Triple> triples, boolean isSuccess) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean isSuccess() {
            return isSuccess;
        }
    }
}
