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
 * Vector retriever implementation backed by vector-store search.
 * <p>
 * Mirrors Python's {@code VectorRetriever} in
 * {@code openjiuwen/core/retrieval/retriever/vector_retriever.py}.
 * </p>
 */
public class VectorRetriever implements Retriever {

    private static final String MODE_VECTOR = "vector";

    private final VectorStore vectorStore;
    private final Embedding embedModel;

    public VectorRetriever(VectorStore vectorStore) {
        this(vectorStore, null);
    }

    public VectorRetriever(VectorStore vectorStore, Embedding embedModel) {
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
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
        if (!MODE_VECTOR.equals(actualMode)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                    "error_msg",
                    "VectorRetriever only supports 'vector' mode, got " + actualMode
            );
        }

        List<RetrievalResult> searchResults = fetchVectorThenSparse(query, topK);
        List<RetrievalResult> retrievalResults = new ArrayList<>();
        for (RetrievalResult result : searchResults) {
            if (scoreThreshold != null && result.getScore() < scoreThreshold) {
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
        return fetchVectorThenSparse(query, topK).stream()
                .map(VectorRetriever::toSearchResult)
                .toList();
    }

    @Override
    public boolean supportsMode(String mode) {
        return MODE_VECTOR.equals(normalizeMode(mode));
    }

    @Override
    public void close() {
        if (vectorStore != null) {
            vectorStore.close();
        }
    }

    private List<RetrievalResult> fetchVectorThenSparse(String query, int topK) {
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

    private static RetrievalResult toRetrievalResult(RetrievalResult searchResult) {
        Map<String, Object> metadata = new LinkedHashMap<>(searchResult.getMetadata());
        Object metadataDocId = metadata.get("doc_id");
        Object metadataChunkId = metadata.get("chunk_id");
        return new RetrievalResult(
                searchResult.getText(),
                searchResult.getScore(),
                metadata,
                metadataDocId == null ? null : String.valueOf(metadataDocId),
                metadataChunkId == null ? null : String.valueOf(metadataChunkId)
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
        return mode == null || mode.isBlank() ? MODE_VECTOR : mode;
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
