/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Hybrid retriever implementation combining vector and sparse retrieval.
 * <p>
 * Mirrors Python's {@code HybridRetriever} in
 * {@code openjiuwen/core/retrieval/retriever/hybrid_retriever.py}.
 * </p>
 */
public class HybridRetriever implements Retriever {

    private static final String MODE_HYBRID = "hybrid";
    private static final String MODE_VECTOR = "vector";
    private static final String MODE_SPARSE = "sparse";

    private final VectorStore vectorStore;
    private final Embedding embedModel;
    private final double alpha;

    public HybridRetriever(VectorStore vectorStore) {
        this(vectorStore, null, 0.5d);
    }

    public HybridRetriever(VectorStore vectorStore, Embedding embedModel) {
        this(vectorStore, embedModel, 0.5d);
    }

    public HybridRetriever(VectorStore vectorStore, Embedding embedModel, double alpha) {
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
        this.alpha = alpha;
    }

    public Embedding getEmbedModel() {
        return embedModel;
    }

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK,
            Double scoreThreshold,
            String mode,
            Map<String, Object> options
    ) {
        String actualMode = normalizeMode(mode);
        if (scoreThreshold != null && !MODE_VECTOR.equals(actualMode)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID,
                    "error_msg",
                    "score_threshold is only supported when mode='vector'"
            );
        }

        List<RetrievalResult> searchResults = fetchResults(query, topK, actualMode, optionsOrEmpty(options));
        List<RetrievalResult> retrievalResults = new ArrayList<>();
        for (RetrievalResult result : searchResults) {
            if (MODE_VECTOR.equals(actualMode) && scoreThreshold != null && result.getScore() < scoreThreshold) {
                continue;
            }
            retrievalResults.add(toRetrievalResult(result));
        }
        return List.copyOf(retrievalResults);
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
    public List<SearchResult> retrieveSearchResults(
            String query,
            int topK,
            String mode,
            Map<String, Object> options
    ) {
        List<RetrievalResult> rawResults = fetchResults(query, topK, normalizeMode(mode), optionsOrEmpty(options));
        return rawResults.stream()
                .map(HybridRetriever::toSearchResult)
                .toList();
    }

    @Override
    public boolean supportsMode(String mode) {
        String actualMode = normalizeMode(mode);
        return MODE_HYBRID.equals(actualMode) || MODE_VECTOR.equals(actualMode) || MODE_SPARSE.equals(actualMode);
    }

    @Override
    public void close() {
        if (vectorStore != null) {
            vectorStore.close();
        }
    }

    private List<RetrievalResult> fetchResults(String query, int topK, String mode, Map<String, Object> options) {
        if (MODE_HYBRID.equals(mode)) {
            List<Double> queryVector = embedModel == null ? null : await(embedModel.embedQuery(query));
            return await(vectorStore.hybridSearch(
                    query,
                    queryVector,
                    topK,
                    resolveAlpha(options),
                    VectorStore.VectorStoreFilter.none(),
                    Map.of()
            ));
        }
        if (MODE_VECTOR.equals(mode)) {
            if (embedModel == null) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                        "error_msg",
                        "embed_model is required for vector search"
                );
            }
            List<Double> queryVector = await(embedModel.embedQuery(query));
            List<RetrievalResult> searchResults = await(vectorStore.search(
                    queryVector,
                    topK,
                    VectorStore.VectorStoreFilter.none(),
                    Map.of()
            ));
            if (searchResults.isEmpty()) {
                return await(vectorStore.sparseSearch(query, topK, VectorStore.VectorStoreFilter.none(), Map.of()));
            }
            return searchResults;
        }
        if (MODE_SPARSE.equals(mode)) {
            return await(vectorStore.sparseSearch(query, topK, VectorStore.VectorStoreFilter.none(), Map.of()));
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                "error_msg",
                "Unsupported mode: " + mode
        );
    }

    private double resolveAlpha(Map<String, Object> options) {
        Object alphaValue = options.get("alpha");
        if (alphaValue instanceof Number number) {
            return number.doubleValue();
        }
        return alpha;
    }

    private static RetrievalResult toRetrievalResult(RetrievalResult searchResult) {
        Map<String, Object> metadata = new LinkedHashMap<>(searchResult.getMetadata());
        String docId = searchResult.getDocId();
        if (docId == null) {
            Object metadataDocId = metadata.get("doc_id");
            docId = metadataDocId == null ? null : String.valueOf(metadataDocId);
        }
        return new RetrievalResult(
                searchResult.getText(),
                searchResult.getScore(),
                metadata,
                docId,
                searchResult.getChunkId()
        );
    }

    private static SearchResult toSearchResult(RetrievalResult result) {
        String id = result.getChunkId();
        if (id == null || id.isBlank()) {
            id = result.getDocId();
        }
        if (id == null || id.isBlank()) {
            id = Integer.toHexString(result.getText().hashCode());
        }
        return new SearchResult(id, result.getText(), result.getScore(), result.getMetadata());
    }

    private static String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? MODE_HYBRID : mode;
    }

    private static Map<String, Object> optionsOrEmpty(Map<String, Object> options) {
        return options == null ? Map.of() : options;
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }
}
