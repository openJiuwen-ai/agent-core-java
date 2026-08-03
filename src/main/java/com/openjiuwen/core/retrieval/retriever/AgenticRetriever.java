/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.TripleMemory;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code AgenticRetriever} in
 * {@code openjiuwen/core/retrieval/retriever/agentic_retriever.py}.
 */
public class AgenticRetriever implements Retriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticRetriever.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String READ_PROMPT = """
            Your task is to find facts that help answer an input question.

            You should present these facts as knowledge triples, which are structured as ("subject", "predicate", "object").

            Now you are given some documents:
            %s

            Based on these documents and preliminary facts that may be provided below, find additional supporting fact(s) that may help answer the following question.
            Note: if the information you are given is insufficient, output only the relevant facts you can find.

            Question: %s
            Facts: %s

            Output the facts as a JSON array of triples, where each triple is an array of [subject, predicate, object].
            """;
    private static final String REWRITE_PROMPT = """
            Given a question and its associated retrieved knowledge triples, you
            are asked to evaluate if the triples by themselves are sufficient
            to formulate an answer to the original question.

            You must respond with a JSON object in the following format:

            {
              "sufficient": true/false,
              "next_question": "string or null"
            }

            Question History:
            Original Question: %s
            %s

            Knowledge triples:
            %s

            Response (JSON only, no additional text):
            """;

    private final Retriever retriever;
    private final BaseModelClient llmClient;
    private final int maxIter;
    private final boolean graphRetriever;
    private final String defaultMode;

    public AgenticRetriever(Retriever retriever, BaseModelClient llmClient) {
        this(retriever, llmClient, 2);
    }

    public AgenticRetriever(Retriever retriever, BaseModelClient llmClient, int maxIter) {
        if (retriever == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_GRAPH_RETRIEVER_NOT_FOUND,
                    "error_msg",
                    "retriever is required for AgenticRetriever"
            );
        }
        if (llmClient == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_LLM_CLIENT_NOT_FOUND,
                    "error_msg",
                    "llm_client is required for AgenticRetriever"
            );
        }
        this.retriever = retriever;
        this.llmClient = llmClient;
        this.maxIter = maxIter > 0 ? maxIter : 2;
        this.graphRetriever = retriever instanceof GraphRetriever;
        this.defaultMode = resolveDefaultMode(retriever.getIndexType());
    }

    public boolean isGraphRetriever() {
        return graphRetriever;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    @Override
    public String getIndexType() {
        return retriever.getIndexType();
    }

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK,
            Double scoreThreshold,
            String mode,
            Map<String, Object> options
    ) {
        if (topK <= 0) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND,
                    "error_msg",
                    "top_k is invalid, must be a positive integer"
            );
        }
        String resolvedMode = mode == null ? defaultMode : mode;
        Map<String, Object> safeOptions = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
        return graphRetriever
                ? retrieveWithGraph(query, topK, scoreThreshold, resolvedMode, safeOptions)
                : retrieveGeneric(query, topK, scoreThreshold, resolvedMode, safeOptions);
    }

    @Override
    public List<List<RetrievalResult>> batchRetrieve(
            List<String> queries,
            int topK,
            String mode,
            Map<String, Object> options
    ) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<List<RetrievalResult>> results = new ArrayList<>(queries.size());
        for (String query : queries) {
            results.add(retrieve(query, topK, null, mode, options));
        }
        return List.copyOf(results);
    }

    @Override
    public void close() {
        retriever.close();
    }

    private List<RetrievalResult> retrieveWithGraph(
            String query,
            int topK,
            Double scoreThreshold,
            String mode,
            Map<String, Object> options
    ) {
        GraphRetriever graph = (GraphRetriever) retriever;
        Map<String, Object> downstreamOptions = new LinkedHashMap<>(options);
        boolean graphExpansion = !Boolean.FALSE.equals(downstreamOptions.get("graph_expansion"));
        List<String> queries = new ArrayList<>();
        queries.add(query);
        List<List<RetrievalResult>> historyResults = new ArrayList<>();
        TripleMemory memory = new TripleMemory();

        for (int turn = 1; turn <= maxIter; turn++) {
            String currentQuery = queries.get(queries.size() - 1);
            Retriever chunkRetriever = graph.getRetrieverForMode(mode, true);
            List<RetrievalResult> chunkResults = chunkRetriever.retrieve(
                    currentQuery,
                    topK,
                    scoreThreshold,
                    mode,
                    downstreamOptions
            );

            if (graphExpansion) {
                List<List<String>> proximalTriples = read(currentQuery, chunkResults, null);
                List<RetrievalResult> linkedTriples = linkTriples(graph, proximalTriples, mode, downstreamOptions);
                chunkResults = graph.graphExpansion(
                        currentQuery,
                        chunkResults,
                        linkedTriples,
                        topK,
                        mode,
                        downstreamOptions
                );
            }

            memory.batchExtendMemory(read(query, chunkResults, memory.getMemory()));
            historyResults.add(chunkResults);

            if (turn >= maxIter) {
                break;
            }
            String rewritten = rewrite(query, memory.getTriplesStr(), queries);
            if (rewritten == null) {
                break;
            }
            queries.add(rewritten);
        }

        List<List<RetrievalResult>> fusionInputs = new ArrayList<>(
                linkPassages(graph, memory.getMemory(), mode, downstreamOptions)
        );
        fusionInputs.addAll(historyResults);
        return limit(FusionUtils.rrfFusionRetrieval(fusionInputs, 60), topK);
    }

    private List<RetrievalResult> retrieveGeneric(
            String query,
            int topK,
            Double scoreThreshold,
            String mode,
            Map<String, Object> options
    ) {
        List<String> queries = new ArrayList<>();
        queries.add(query);
        List<List<RetrievalResult>> historyResults = new ArrayList<>();
        TripleMemory memory = new TripleMemory();

        for (int turn = 1; turn <= maxIter; turn++) {
            String currentQuery = queries.get(queries.size() - 1);
            List<RetrievalResult> chunkResults = retriever.retrieve(currentQuery, topK, scoreThreshold, mode, options);
            memory.batchExtendMemory(read(currentQuery, chunkResults, memory.getMemory()));
            historyResults.add(chunkResults);

            if (turn >= maxIter) {
                break;
            }
            String rewritten = rewrite(query, memory.getTriplesStr(), queries);
            if (rewritten == null) {
                break;
            }
            queries.add(rewritten);
        }

        return limit(FusionUtils.rrfFusionRetrieval(historyResults, 60), topK);
    }

    private String rewrite(String query, String triples, List<String> questionHistory) {
        String history = "(No rewriting steps yet)";
        if (questionHistory.size() > 1) {
            List<String> lines = new ArrayList<>();
            for (int index = 1; index < questionHistory.size(); index++) {
                lines.add("Rewritten Question " + index + ": " + questionHistory.get(index));
            }
            history = String.join("\n", lines);
        }
        String response = llmCall(REWRITE_PROMPT.formatted(query, history, triples));
        if (response == null) {
            return null;
        }
        try {
            JsonNode node = parseJson(response);
            boolean sufficient = node.path("sufficient").asBoolean(false);
            JsonNode nextQuestion = node.path("next_question");
            if (sufficient || nextQuestion == null || nextQuestion.isNull()) {
                return null;
            }
            String value = nextQuestion.asText();
            return value == null || value.isBlank() ? null : value;
        } catch (Exception exception) {
            LOGGER.warn("[Agentic] Failed to parse rewrite response as JSON: {}", exception.getMessage());
            return null;
        }
    }

    private List<List<String>> read(String query, List<RetrievalResult> passages, List<List<String>> existingFacts) {
        String docs = "";
        if (passages != null) {
            List<String> texts = new ArrayList<>();
            for (int index = 0; index < Math.min(5, passages.size()); index++) {
                RetrievalResult result = passages.get(index);
                if (result.getText() != null && !result.getText().isBlank()) {
                    texts.add(result.getText());
                }
            }
            docs = String.join("\n\n", texts);
        }
        String facts = existingFacts == null || existingFacts.isEmpty() ? "None" : existingFacts.toString();
        String response = llmCall(READ_PROMPT.formatted(docs, query, facts));
        if (response == null) {
            return List.of();
        }
        try {
            JsonNode parsed = parseJson(response);
            if (!parsed.isArray()) {
                LOGGER.warn("[Agentic] Read response JSON is not a list: {}", parsed);
                return List.of();
            }
            List<List<String>> triples = new ArrayList<>();
            for (JsonNode triple : parsed) {
                if (triple != null && triple.isArray() && triple.size() == 3) {
                    triples.add(List.of(
                            jsonNodeToPythonString(triple.get(0)),
                            jsonNodeToPythonString(triple.get(1)),
                            jsonNodeToPythonString(triple.get(2))
                    ));
                }
            }
            return List.copyOf(triples);
        } catch (Exception exception) {
            LOGGER.warn("[Agentic] Failed to parse read response as JSON: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<RetrievalResult> linkTriples(
            GraphRetriever graph,
            List<List<String>> triples,
            String mode,
            Map<String, Object> options
    ) {
        Retriever tripleRetriever = graph.getRetrieverForMode(mode, false);
        List<SearchResult> candidates = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (List<String> triple : triples) {
            try {
                List<SearchResult> results = tripleRetriever.retrieveSearchResults(
                        String.join(" ", triple),
                        1,
                        mode,
                        options
                );
                if (results != null && !results.isEmpty()) {
                    candidates.add(results.get(0));
                }
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            LOGGER.warn("[Agentic] Triple linking failed for {}/{} triples.", failures.size(), triples.size());
        }

        List<SearchResult> deduplicated = CommonUtils.deduplicate(candidates, SearchResult::getId);
        List<RetrievalResult> retrievalResults = new ArrayList<>();
        for (SearchResult result : deduplicated) {
            Map<String, Object> metadata = result.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(result.getMetadata());
            retrievalResults.add(new RetrievalResult(
                    result.getText(),
                    result.getScore(),
                    metadata,
                    stringValue(metadata.get("doc_id")),
                    stringValue(metadata.get("chunk_id"))
            ));
        }
        return List.copyOf(retrievalResults);
    }

    private List<List<RetrievalResult>> linkPassages(
            GraphRetriever graph,
            List<List<String>> triples,
            String mode,
            Map<String, Object> options
    ) {
        Retriever chunkRetriever = graph.getRetrieverForMode(mode, true);
        List<List<RetrievalResult>> results = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (List<String> triple : triples) {
            try {
                List<RetrievalResult> passages = chunkRetriever.retrieve(
                        String.join(" ", triple),
                        5,
                        null,
                        mode,
                        options
                );
                if (passages != null && !passages.isEmpty()) {
                    results.add(passages);
                }
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            LOGGER.warn("[Agentic] Link passages failed for {}/{} triples.", failures.size(), triples.size());
        }
        return List.copyOf(results);
    }

    private String llmCall(String prompt) {
        try {
            AssistantMessage response = llmClient.invoke(
                    List.of(Map.of("role", "user", "content", prompt)),
                    null,
                    0.0f,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()
            );
            return response == null ? null : response.getContentAsString();
        } catch (Exception exception) {
            LOGGER.warn("[Agentic] LLM invocation failed: {}", exception.getMessage());
            return null;
        }
    }

    private static String resolveDefaultMode(String indexType) {
        return switch (indexType == null ? "hybrid" : indexType) {
            case "vector" -> "vector";
            case "bm25" -> "sparse";
            default -> "hybrid";
        };
    }

    private static List<RetrievalResult> limit(List<RetrievalResult> results, int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        if (results.size() <= topK) {
            return List.copyOf(results);
        }
        return List.copyOf(results.subList(0, topK));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static JsonNode parseJson(String response) throws java.io.IOException {
        try {
            return MAPPER.readTree(response);
        } catch (java.io.IOException firstFailure) {
            String candidate = extractJsonCandidate(response);
            if (!candidate.equals(response)) {
                return MAPPER.readTree(candidate);
            }
            throw firstFailure;
        }
    }

    private static String extractJsonCandidate(String response) {
        if (response == null) {
            return "";
        }
        String text = response.trim();
        if (text.startsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            if (firstLineBreak >= 0) {
                text = text.substring(firstLineBreak + 1).trim();
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).trim();
            }
        }

        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        char closing;
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            start = objectStart;
            closing = '}';
        } else if (arrayStart >= 0) {
            start = arrayStart;
            closing = ']';
        } else {
            return text;
        }

        int end = text.lastIndexOf(closing);
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static String jsonNodeToPythonString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "None";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }
}
