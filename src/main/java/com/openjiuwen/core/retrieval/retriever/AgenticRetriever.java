/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.TripleMemory;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import com.openjiuwen.core.retrieval.utils.FusionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retriever that adds iterative query rewriting and triple reading on top of a base retriever.
 *
 * <p>Mirrors Python's {@code AgenticRetriever} in
 * {@code openjiuwen.core.retrieval.retriever.agentic_retriever}.
 */
public class AgenticRetriever extends AbstractRetriever {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String READ_PROMPT = """
            Your task is to find facts that help answer an input question.
            You should present these facts as knowledge triples, which are structured as ["subject", "predicate", "object"].
            Documents:
            %s

            Question: %s
            Facts: %s

            Output JSON only.
            """;
    private static final String REWRITE_PROMPT = """
            Given a question and its associated retrieved knowledge triples, decide whether the triples are sufficient.
            Respond with JSON:
            {"sufficient": true/false, "next_question": "string or null"}

            Original Question: %s
            Question History:
            %s
            Knowledge triples:
            %s
            """;

    private final Retriever retriever;
    private final BaseModelClient llm;
    private final int maxIter;
    private final boolean graphRetriever;
    private final String defaultMode;

    public AgenticRetriever(Retriever retriever, BaseModelClient llmClient) {
        this(retriever, llmClient, 2);
    }

    public AgenticRetriever(Retriever retriever, BaseModelClient llmClient, int maxIter) {
        if (retriever == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_GRAPH_RETRIEVER_NOT_FOUND,
                    "retriever is required for AgenticRetriever");
        }
        if (llmClient == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_LLM_CLIENT_NOT_FOUND,
                    "llm_client is required for AgenticRetriever");
        }
        this.retriever = retriever;
        this.llm = llmClient;
        this.maxIter = maxIter > 0 ? maxIter : 2;
        this.graphRetriever = retriever instanceof GraphRetriever;
        this.defaultMode = switch (retriever.getIndexType()) {
            case "vector" -> "vector";
            case "bm25" -> "sparse";
            default -> "hybrid";
        };
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
    public List<RetrievalResult> retrieve(String query,
                                          int topK,
                                          Double scoreThreshold,
                                          String mode,
                                          Map<String, Object> options) {
        if (topK <= 0) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND,
                    "top_k is invalid, must be a positive integer");
        }
        String resolvedMode = mode == null ? defaultMode : mode;
        return graphRetriever
                ? retrieveWithGraph(query, topK, scoreThreshold, resolvedMode, options)
                : retrieveGeneric(query, topK, scoreThreshold, resolvedMode, options);
    }

    @Override
    public List<List<RetrievalResult>> batchRetrieve(List<String> queries,
                                                     int topK,
                                                     String mode,
                                                     Map<String, Object> options) {
        List<List<RetrievalResult>> results = new ArrayList<>();
        for (String query : queries) {
            results.add(retrieve(query, topK, null, mode, options));
        }
        return results;
    }

    @Override
    public void close() {
        try {
            retriever.close();
        } catch (Exception ignored) {
        }
    }

    private List<RetrievalResult> retrieveWithGraph(String query,
                                                    int topK,
                                                    Double scoreThreshold,
                                                    String mode,
                                                    Map<String, Object> options) {
        GraphRetriever graph = (GraphRetriever) retriever;
        boolean graphExpansion = options == null || !Boolean.FALSE.equals(options.get("graph_expansion"));
        List<String> queries = new ArrayList<>();
        queries.add(query);
        List<List<RetrievalResult>> historyResults = new ArrayList<>();
        TripleMemory memory = new TripleMemory();
        for (int turn = 1; turn <= maxIter; turn++) {
            String currentQuery = queries.get(queries.size() - 1);
            Retriever chunkRetriever = graph.getRetrieverForMode(mode, true);
            List<RetrievalResult> chunkResults = chunkRetriever.retrieve(currentQuery, topK, scoreThreshold, mode, options);
            if (graphExpansion) {
                List<List<String>> proximal = read(currentQuery, chunkResults, null);
                List<RetrievalResult> linkedTriples = linkTriples(graph, proximal, mode, options);
                chunkResults = graph.graphExpansion(currentQuery, chunkResults, linkedTriples, topK, mode, options);
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
        List<List<RetrievalResult>> linkedPassages = linkPassages(graph, memory.getMemory(), mode, options);
        List<List<RetrievalResult>> fusionInputs = new ArrayList<>(linkedPassages);
        fusionInputs.addAll(historyResults);
        return trim(FusionUtils.rrfFusionRetrieval(fusionInputs, 60), topK);
    }

    private List<RetrievalResult> retrieveGeneric(String query,
                                                  int topK,
                                                  Double scoreThreshold,
                                                  String mode,
                                                  Map<String, Object> options) {
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
        return trim(FusionUtils.rrfFusionRetrieval(historyResults, 60), topK);
    }

    private String rewrite(String query, String triples, List<String> questionHistory) {
        String history = "(No rewriting steps yet)";
        if (questionHistory.size() > 1) {
            List<String> lines = new ArrayList<>();
            for (int i = 1; i < questionHistory.size(); i++) {
                lines.add("Rewritten Question " + i + ": " + questionHistory.get(i));
            }
            history = String.join("\n", lines);
        }
        String response = llmCall(String.format(REWRITE_PROMPT, query, history, triples));
        if (response == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(response);
            boolean sufficient = node.path("sufficient").asBoolean(false);
            String nextQuestion = node.path("next_question").isNull() ? null : node.path("next_question").asText(null);
            return sufficient || nextQuestion == null || nextQuestion.isBlank() ? null : nextQuestion;
        } catch (Exception e) {
            return null;
        }
    }

    private List<List<String>> read(String query, List<RetrievalResult> passages, List<List<String>> existingFacts) {
        String docs = passages == null ? "" : passages.stream().limit(5).map(RetrievalResult::getText).reduce("", (a, b) -> a + "\n\n" + b).trim();
        String facts = existingFacts == null || existingFacts.isEmpty() ? "None" : existingFacts.toString();
        String response = llmCall(String.format(READ_PROMPT, docs, query, facts));
        if (response == null) {
            return List.of();
        }
        try {
            List<List<String>> triples = MAPPER.readValue(response, new TypeReference<>() {});
            List<List<String>> filtered = new ArrayList<>();
            for (List<String> triple : triples) {
                if (triple != null && triple.size() == 3) {
                    filtered.add(List.of(String.valueOf(triple.get(0)), String.valueOf(triple.get(1)), String.valueOf(triple.get(2))));
                }
            }
            return filtered;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<RetrievalResult> linkTriples(GraphRetriever graph,
                                              List<List<String>> triples,
                                              String mode,
                                              Map<String, Object> options) {
        Retriever tripleRetriever = graph.getRetrieverForMode(mode, false);
        List<SearchResult> searchResults = new ArrayList<>();
        for (List<String> triple : triples) {
            List<SearchResult> results = tripleRetriever.retrieveSearchResults(String.join(" ", triple), 1, mode, options);
            if (!results.isEmpty()) {
                searchResults.add(results.get(0));
            }
        }
        List<SearchResult> deduped = CommonUtils.deduplicate(searchResults, SearchResult::getId);
        List<RetrievalResult> retrievalResults = new ArrayList<>();
        for (SearchResult result : deduped) {
            Map<String, Object> metadata = result.getMetadata() == null ? new LinkedHashMap<>() : result.getMetadata();
            retrievalResults.add(new RetrievalResult(
                    result.getText(),
                    result.getScore(),
                    metadata,
                    VectorRetriever.stringValue(metadata.get("doc_id")),
                    VectorRetriever.stringValue(metadata.get("chunk_id"))));
        }
        return retrievalResults;
    }

    private List<List<RetrievalResult>> linkPassages(GraphRetriever graph,
                                                     List<List<String>> triples,
                                                     String mode,
                                                     Map<String, Object> options) {
        Retriever chunkRetriever = graph.getRetrieverForMode(mode, true);
        List<List<RetrievalResult>> results = new ArrayList<>();
        for (List<String> triple : triples) {
            List<RetrievalResult> passages = chunkRetriever.retrieve(String.join(" ", triple), 5, null, mode, options);
            if (!passages.isEmpty()) {
                results.add(passages);
            }
        }
        return results;
    }

    private String llmCall(String prompt) {
        try {
            AssistantMessage response = llm.invoke(
                    List.of(Map.of("role", "user", "content", prompt)),
                    null,
                    0.0f,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of());
            return response == null ? null : response.getContentAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<RetrievalResult> trim(List<RetrievalResult> results, int topK) {
        if (results.size() <= topK) {
            return results;
        }
        return new ArrayList<>(results.subList(0, topK));
    }
}
