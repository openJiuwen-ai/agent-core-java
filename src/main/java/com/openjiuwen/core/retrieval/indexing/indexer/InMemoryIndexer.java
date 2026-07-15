/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.foundation.store.query.QueryExpressions;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Backward-compatible in-memory-style index manager backed by {@link VectorStore}.
 * <p>
 * Mirrors Python's {@code InMemoryIndexer} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/in_memory_indexer.py}.
 * </p>
 */
public class InMemoryIndexer extends Indexer implements IndexBackendConfig {

    private final VectorStore vectorStore;

    public InMemoryIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public CompletableFuture<Boolean> buildIndex(List<TextChunk> chunks,
                                                 IndexConfig config,
                                                 Embedding embedModel,
                                                 Map<String, Object> kwargs) {
        List<TextChunk> safeChunks = chunks == null ? List.of() : chunks;
        CompletableFuture<Void> embeddingFuture = requiresEmbedding(config)
                ? EmbedChunks.computeChunkEmbeddings(safeChunks, embedModel, null, false)
                : CompletableFuture.completedFuture(null);
        return embeddingFuture
                .thenCompose(ignored -> vectorStore.add(toDocs(safeChunks), 128, kwargs == null ? Map.of() : kwargs))
                .thenApply(ignored -> Boolean.TRUE);
    }

    @Override
    public CompletableFuture<Boolean> updateIndex(List<TextChunk> chunks,
                                                  String docId,
                                                  IndexConfig config,
                                                  Embedding embedModel,
                                                  Map<String, Object> kwargs) {
        String indexName = config == null ? null : config.getIndexName();
        return deleteIndex(docId, indexName, kwargs)
                .thenCompose(ignored -> buildIndex(chunks, config, embedModel, kwargs));
    }

    @Override
    public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
        if (docId == null || docId.isBlank()) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        String activeDocIdField = getDocIdField();
        if (activeDocIdField == null || activeDocIdField.isBlank()) {
            activeDocIdField = "doc_id";
        }
        VectorStore.DeleteFilter filter = VectorStore.DeleteFilter.ofQuery(
                QueryExpressions.eq(activeDocIdField, docId));
        return vectorStore.delete(List.of(), filter, kwargs == null ? Map.of() : kwargs);
    }

    @Override
    public CompletableFuture<Boolean> indexExists(String indexName) {
        return vectorStore.tableExists(indexName);
    }

    @Override
    public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
        return indexExists(indexName).thenApply(exists -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("index_name", indexName);
            info.put("count", 0);
            info.put("isExists", exists);
            return info;
        });
    }

    @Override
    public String getDatabaseName() {
        return invokeStringGetter("getDatabaseName");
    }

    @Override
    public String getDistanceMetric() {
        return invokeStringGetter("getDistanceMetric");
    }

    @Override
    public String getIndexType() {
        return invokeStringGetter("getIndexType");
    }

    @Override
    public String getTextField() {
        return invokeStringGetter("getTextField");
    }

    @Override
    public String getVectorField() {
        Object value = invokeGetter("getVectorField");
        if (value == null) {
            return null;
        }
        Object nested = invokeGetter(value, "getVectorField");
        return nested == null ? String.valueOf(value) : String.valueOf(nested);
    }

    @Override
    public String getSparseVectorField() {
        return invokeStringGetter("getSparseVectorField");
    }

    @Override
    public String getMetadataField() {
        return invokeStringGetter("getMetadataField");
    }

    @Override
    public String getDocIdField() {
        return invokeStringGetter("getDocIdField");
    }

    private static boolean requiresEmbedding(IndexConfig config) {
        String indexType = config == null ? "hybrid" : config.getIndexType();
        return !"bm25".equals(indexType);
    }

    private static List<Map<String, Object>> toDocs(List<TextChunk> chunks) {
        List<Map<String, Object>> docs = new ArrayList<>(chunks.size());
        for (TextChunk chunk : chunks) {
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
            metadata.putIfAbsent("doc_id", chunk.getDocId());
            metadata.putIfAbsent("chunk_id", chunk.getId_());

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", chunk.getId_());
            doc.put("content", chunk.getText());
            doc.put("document_id", chunk.getDocId());
            doc.put("chunk_id", chunk.getId_());
            doc.put("metadata", metadata);
            if (chunk.getEmbedding() != null) {
                doc.put("embedding", chunk.getEmbedding());
            }
            docs.add(doc);
        }
        return docs;
    }

    private String invokeStringGetter(String methodName) {
        Object value = invokeGetter(methodName);
        return value == null ? null : String.valueOf(value);
    }

    private Object invokeGetter(String methodName) {
        return invokeGetter(vectorStore, methodName);
    }

    private static Object invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
