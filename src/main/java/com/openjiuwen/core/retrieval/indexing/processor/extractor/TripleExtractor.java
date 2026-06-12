/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;

/**
 * Mirrors Python's {@code TripleExtractor} in
 * {@code openjiuwen/core/retrieval/indexing/processor/extractor/triple_extractor.py}.
 */
public class TripleExtractor extends Extractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final String PROMPT_TEMPLATE = """
            # Instruction

            Your task is to construct an RDF-style graph from the given title and passage.
            Extract named entities and relationships, then return the result as exactly one valid JSON object.

            Each triple should represent a meaningful relationship in the graph.
            Each triple should contain at least one, and preferably two, named entities from the title or passage.
            Clearly resolve pronouns to specific names whenever possible.

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

            Output:
            {
              "named_entities": [
                "Michigan State",
                "national championship",
                "1979",
                "Magic Johnson",
                "National Basketball Association",
                "Los Angeles Lakers",
                "NBA Championship"
              ],
              "triples": [
                ["Magic Johnson", "member of sports team", "Michigan State"],
                ["Michigan State", "award", "national championship"],
                ["Michigan State", "award date", "1979"],
                ["Magic Johnson", "draft pick number", "1"],
                ["Magic Johnson", "drafted in", "1979"],
                ["Magic Johnson", "drafted by", "Los Angeles Lakers"],
                ["Magic Johnson", "member of sports team", "Los Angeles Lakers"],
                ["Magic Johnson", "league", "National Basketball Association"],
                ["Los Angeles Lakers", "league", "National Basketball Association"],
                ["Los Angeles Lakers", "award received", "NBA Championship"]
              ]
            }

            # Demonstration 2

            Title:
            Elden Ring

            Passage:
            Elden Ring is a 2022 action role-playing game developed by FromSoftware. It was directed by Hidetaka Miyazaki with worldbuilding provided by American fantasy writer George R. R. Martin.

            Output:
            {
              "named_entities": [
                "Elden Ring",
                "2022",
                "action role-playing game",
                "FromSoftware",
                "Hidetaka Miyazaki",
                "United States of America",
                "fantasy",
                "George R. R. Martin"
              ],
              "triples": [
                ["Elden Ring", "publication", "2022"],
                ["Elden Ring", "genre", "action role-playing game"],
                ["Elden Ring", "publisher", "FromSoftware"],
                ["Elden Ring", "director", "Hidetaka Miyazaki"],
                ["Elden Ring", "screenwriter", "George R. R. Martin"],
                ["George R. R. Martin", "country of citizenship", "United States of America"],
                ["George R. R. Martin", "genre", "fantasy"]
              ]
            }

            # Input

            Title:
            %s

            Passage:
            %s
            """;

    private final LlmInvoker llmClient;
    private final String modelName;
    private final double temperature;
    private final Semaphore limiter;

    public TripleExtractor(LlmInvoker llmClient, String modelName) {
        this(llmClient, modelName, 0.0d, 50);
    }

    public TripleExtractor(LlmInvoker llmClient, String modelName, double temperature, int maxConcurrent) {
        this.llmClient = llmClient;
        this.modelName = modelName;
        this.temperature = temperature;
        this.limiter = new Semaphore(Math.max(maxConcurrent, 1));
    }

    @Override
    public CompletableFuture<List<Triple>> extract(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<ChunkResult>> tasks = new ArrayList<>(chunks.size());
        for (TextChunk chunk : chunks) {
            tasks.add(CompletableFuture.supplyAsync(() -> extractChunk(chunk)));
        }

        CompletableFuture<?>[] futures = tasks.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> mergeResults(chunks, tasks));
    }

    String buildPrompt(String passage, String title) {
        String titleValue = title == null || title.isBlank() ? "Untitled" : title;
        return PROMPT_TEMPLATE.formatted(titleValue, passage == null ? "" : passage);
    }

    private ChunkResult extractChunk(TextChunk chunk) {
        acquirePermit();
        try {
            String title = String.valueOf(chunk.getMetadata().getOrDefault("title", ""));
            UserMessage message = new UserMessage(buildPrompt(chunk.getText(), title));
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("temperature", temperature);

            try {
                BaseMessage completion = llmClient.invoke(List.of(message), options).join();
                ParseResult parsed = parseTriples(completion == null ? "" : completion.getContentAsString(), chunk.getDocId(), chunk.getId_());
                if (!parsed.parseSuccess()) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                            null,
                            null,
                            null,
                            Map.of("error_msg", chunk.getId_() + ": LLM response could not be parsed as valid triple JSON")
                    );
                }
                return new ChunkResult(chunk.getId_(), parsed.triples(), null);
            } catch (CompletionException exception) {
                Throwable cause = unwrap(exception);
                if (cause instanceof BaseError baseError) {
                    return new ChunkResult(chunk.getId_(), List.of(), baseError);
                }
                LOGGER.error("Failed to extract triples from chunk {}: {}", chunk.getId_(), cause.getMessage());
                return new ChunkResult(
                        chunk.getId_(),
                        List.of(),
                        ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                                null,
                                null,
                                cause,
                                Map.of("error_msg", chunk.getId_() + ": " + cause.getMessage())
                        )
                );
            } catch (BaseError baseError) {
                return new ChunkResult(chunk.getId_(), List.of(), baseError);
            } catch (Exception exception) {
                LOGGER.error("Failed to extract triples from chunk {}: {}", chunk.getId_(), exception.getMessage());
                return new ChunkResult(
                        chunk.getId_(),
                        List.of(),
                        ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                                null,
                                null,
                                exception,
                                Map.of("error_msg", chunk.getId_() + ": " + exception.getMessage())
                        )
                );
            }
        } finally {
            limiter.release();
        }
    }

    private List<Triple> mergeResults(List<TextChunk> chunks, List<CompletableFuture<ChunkResult>> tasks) {
        List<Triple> allTriples = new ArrayList<>();
        BaseError firstError = null;
        for (int index = 0; index < tasks.size(); index++) {
            ChunkResult result = tasks.get(index).join();
            if (result.error() != null) {
                LOGGER.error("Task failed for chunk {}: {}", chunks.get(index).getId_(), result.error().getMessage());
                if (firstError == null) {
                    firstError = result.error();
                }
                continue;
            }
            allTriples.addAll(result.triples());
        }
        if (firstError != null) {
            throw firstError;
        }
        return allTriples;
    }

    ParseResult parseTriples(String content, String docId, String chunkId) {
        List<Triple> triples = new ArrayList<>();
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(normalizeJsonContent(content));
            JsonNode tripleList;
            if (parsed.isObject()) {
                tripleList = parsed.get("triples");
                if (tripleList == null || !tripleList.isArray()) {
                    return new ParseResult(List.of(), false);
                }
            } else if (parsed.isArray()) {
                tripleList = parsed;
            } else {
                return new ParseResult(List.of(), false);
            }

            if (tripleList.isEmpty()) {
                return new ParseResult(List.of(), true);
            }

            int invalidCount = 0;
            for (JsonNode tripleNode : tripleList) {
                if (!tripleNode.isArray() || tripleNode.size() < 3) {
                    invalidCount++;
                    continue;
                }
                JsonNode subject = tripleNode.get(0);
                JsonNode predicate = tripleNode.get(1);
                JsonNode object = tripleNode.get(2);
                if (isInvalidTripleValue(subject) || isInvalidTripleValue(predicate) || isInvalidTripleValue(object)) {
                    invalidCount++;
                    continue;
                }
                triples.add(
                        new Triple(
                                subject.asText().trim(),
                                predicate.asText().trim(),
                                object.asText().trim(),
                                Map.of("doc_id", docId, "chunk_id", chunkId)
                        )
                );
            }

            if (invalidCount > 0) {
                LOGGER.warning("Ignored {} invalid triples for chunk {} during parsing", invalidCount, chunkId);
            }
            return new ParseResult(triples, !triples.isEmpty());
        } catch (IOException exception) {
            LOGGER.error("Failed to parse triples from content: {}. Content: {}", exception.getMessage(), preview(content));
            return new ParseResult(List.of(), false);
        } catch (Exception exception) {
            LOGGER.error("Failed to parse triples: {}", exception.getMessage());
            return new ParseResult(List.of(), false);
        }
    }

    private static String normalizeJsonContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            String[] lines = normalized.split("\\R");
            if (lines.length > 2) {
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i < lines.length - 1; i++) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(lines[i]);
                }
                normalized = builder.toString();
            }
        }
        return normalized.replaceAll(",\\s*([}\\]])", "$1");
    }

    private static boolean isInvalidTripleValue(JsonNode value) {
        return value == null || value.isNull() || value.isContainerNode();
    }

    private void acquirePermit() {
        try {
            limiter.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    @FunctionalInterface
    public interface LlmInvoker {
        CompletableFuture<? extends BaseMessage> invoke(List<BaseMessage> messages, Map<String, Object> options);
    }

    record ParseResult(List<Triple> triples, boolean parseSuccess) {
    }

    private record ChunkResult(String chunkId, List<Triple> triples, BaseError error) {
    }
}
