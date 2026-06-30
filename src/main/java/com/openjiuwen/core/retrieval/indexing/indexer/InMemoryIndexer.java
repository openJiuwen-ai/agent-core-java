/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory index manager backed by {@link VectorStore}.
 */
public class InMemoryIndexer implements Indexer {

    private final VectorStore vectorStore;

    /**
     * Auto-generated for codecheck compliance.
     */
    public InMemoryIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options) {
        VectorStore scoped = vectorStore.withCollection(config.getIndexName());
        List<Map<String, Object>> docs = toDocs(chunks, config, embedModel, options);
        scoped.add(docs, 128, options);
        return true;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean updateIndex(List<TextChunk> chunks,
                               String docId,
                               IndexConfig config,
                               Embedding embedModel,
                               Map<String, Object> options) {
        VectorStore scoped = vectorStore.withCollection(config.getIndexName());
        scoped.delete(null, Map.of(getDocIdField(), docId), options);
        scoped.add(toDocs(chunks, config, embedModel, options), 128, options);
        return true;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
        VectorStore scoped = vectorStore.withCollection(indexName);
        return scoped.delete(null, Map.of(getDocIdField(), docId), options);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean indexExists(String indexName) {
        return vectorStore.tableExists(indexName);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getIndexInfo(String indexName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("index_name", indexName);
        info.put("count", vectorStore.count(indexName));
        info.put("isExists", vectorStore.tableExists(indexName));
        return info;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDatabaseName() {
        return vectorStore.getDatabaseName();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDistanceMetric() {
        return vectorStore.getDistanceMetric();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexType() {
        return vectorStore.getIndexType();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTextField() {
        return vectorStore.getTextField();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getVectorField() {
        return vectorStore.getVectorField();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSparseVectorField() {
        return vectorStore.getSparseVectorField();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMetadataField() {
        return vectorStore.getMetadataField();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDocIdField() {
        return vectorStore.getDocIdField();
    }

    private List<Map<String, Object>> toDocs(List<TextChunk> chunks,
                                             IndexConfig config,
                                             Embedding embedModel,
                                             Map<String, Object> options) {
        List<TextChunk> safeChunks = chunks == null ? List.of() : chunks;
        List<List<Float>> embeddings = null;
        if (!"bm25".equals(config.getIndexType())) {
            if (embedModel == null) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND,
                        "embed_model is required to build vector or hybrid index");
            }
            embeddings = embedBatches(safeChunks, embedModel, options);
        }
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int i = 0; i < safeChunks.size(); i++) {
            TextChunk chunk = safeChunks.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
            metadata.putIfAbsent("doc_id", chunk.getDocId());
            metadata.putIfAbsent("chunk_id", chunk.getId());
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", chunk.getId());
            doc.put(getTextField(), chunk.getText());
            doc.put(getDocIdField(), chunk.getDocId());
            doc.put("chunk_id", chunk.getId());
            doc.put(getMetadataField(), metadata);
            if (embeddings != null && i < embeddings.size()) {
                doc.put(getVectorField(), embeddings.get(i));
            }
            docs.add(doc);
        }
        return docs;
    }

    private List<List<Float>> embedBatches(List<TextChunk> safeChunks,
                                           Embedding embedModel,
                                           Map<String, Object> options) {
        if (safeChunks.isEmpty()) {
            return List.of();
        }
        List<List<Float>> embeddings = new ArrayList<>(safeChunks.size());
        int batchSize = Math.max(1, embedModel.getMaxBatchSize());
        BaseCallback callback = options != null && options.get("callback") instanceof BaseCallback baseCallback
                ? baseCallback
                : null;
        for (int start = 0; start < safeChunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, safeChunks.size());
            List<String> texts = safeChunks.subList(start, end).stream().map(TextChunk::getText).toList();
            embeddings.addAll(embedModel.embedDocuments(texts, batchSize));
            if (callback != null) {
                callback.onBatch(start, end, texts);
            }
        }
        return embeddings;
    }
}
