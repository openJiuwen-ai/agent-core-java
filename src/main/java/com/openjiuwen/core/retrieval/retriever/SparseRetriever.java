/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Sparse retriever implementation backed by vector-store sparse search.
 * <p>
 * Mirrors Python's {@code SparseRetriever} in
 * {@code openjiuwen/core/retrieval/retriever/sparse_retriever.py}.
 * </p>
 */
public class SparseRetriever implements Retriever {

    private static final String MODE_SPARSE = "sparse";

    private final VectorStore vectorStore;

    public SparseRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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
        if (!MODE_SPARSE.equals(actualMode)) {
            throw unsupportedMode(actualMode);
        }
        List<RetrievalResult> searchResults = fetchSparseResults(query, topK);
        return searchResults.stream()
                .map(SparseRetriever::toRetrievalResult)
                .toList();
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
        String actualMode = normalizeMode(mode);
        if (!MODE_SPARSE.equals(actualMode)) {
            throw unsupportedMode(actualMode);
        }
        return fetchSparseResults(query, topK).stream()
                .map(SparseRetriever::toSearchResult)
                .toList();
    }

    @Override
    public boolean supportsMode(String mode) {
        return MODE_SPARSE.equals(normalizeMode(mode));
    }

    @Override
    public void close() {
        if (vectorStore != null) {
            vectorStore.close();
        }
    }

    private List<RetrievalResult> fetchSparseResults(String query, int topK) {
        return await(vectorStore.sparseSearch(query, topK, VectorStore.VectorStoreFilter.none(), Map.of()));
    }

    private RuntimeException unsupportedMode(String mode) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                "error_msg",
                "SparseRetriever only supports 'sparse' mode, got " + mode
        );
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
        return mode == null || mode.isBlank() ? MODE_SPARSE : mode;
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
