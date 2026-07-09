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
 * 
 * @since 0.1.7
 */
public class InMemoryIndexer implements Indexer {
    private final VectorStore vectorStore;

    /**
     * InMemoryIndexer.
     * 
     * @param vectorStore vectorStore
     * @since 0.1.7
     */
    public InMemoryIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * buildIndex.
     * 
     * @param chunks chunks
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel,
            Map<String, Object> options) {
        VectorStore scoped = vectorStore.withCollection(config.getIndexName());
        List<Map<String, Object>> docs = toDocs(chunks, config, embedModel, options);
        scoped.add(docs, 128, options);
        return true;
    }

    /**
     * updateIndex.
     * 
     * @param chunks chunks
     * @param docId docId
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel,
            Map<String, Object> options) {
        VectorStore scoped = vectorStore.withCollection(config.getIndexName());
        scoped.delete(null, Map.of(getDocIdField(), docId), options);
        scoped.add(toDocs(chunks, config, embedModel, options), 128, options);
        return true;
    }

    /**
     * deleteIndex.
     * 
     * @param docId docId
     * @param indexName indexName
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
        VectorStore scoped = vectorStore.withCollection(indexName);
        return scoped.delete(null, Map.of(getDocIdField(), docId), options);
    }

    /**
     * indexExists.
     * 
     * @param indexName indexName
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean indexExists(String indexName) {
        return vectorStore.tableExists(indexName);
    }

    /**
     * getIndexInfo.
     * 
     * @param indexName indexName
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getIndexInfo(String indexName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("index_name", indexName);
        info.put("count", vectorStore.count(indexName));
        info.put("isExists", vectorStore.tableExists(indexName));
        return info;
    }

    /**
     * getDatabaseName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDatabaseName() {
        return vectorStore.getDatabaseName();
    }

    /**
     * getDistanceMetric.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDistanceMetric() {
        return vectorStore.getDistanceMetric();
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return vectorStore.getIndexType();
    }

    /**
     * getTextField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getTextField() {
        return vectorStore.getTextField();
    }

    /**
     * getVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getVectorField() {
        return vectorStore.getVectorField();
    }

    /**
     * getSparseVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getSparseVectorField() {
        return vectorStore.getSparseVectorField();
    }

    /**
     * getMetadataField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getMetadataField() {
        return vectorStore.getMetadataField();
    }

    /**
     * getDocIdField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDocIdField() {
        return vectorStore.getDocIdField();
    }

    /**
     * toDocs.
     * 
     * @param chunks chunks
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private List<Map<String, Object>> toDocs(List<TextChunk> chunks, IndexConfig config, Embedding embedModel,
            Map<String, Object> options) {
        List<TextChunk> safeChunks = chunks == null ? List.of() : chunks;
        List<List<Float>> embeddings = null;
        if (!"bm25".equals(config.getIndexType())) {
            if (embedModel == null) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND,
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

    /**
     * embedBatches.
     * 
     * @param safeChunks safeChunks
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private List<List<Float>> embedBatches(List<TextChunk> safeChunks, Embedding embedModel,
            Map<String, Object> options) {
        if (safeChunks.isEmpty()) {
            return List.of();
        }
        List<List<Float>> embeddings = new ArrayList<>(safeChunks.size());
        int batchSize = Math.max(1, embedModel.getMaxBatchSize());
        BaseCallback callback =
            options != null && options.get("callback") instanceof BaseCallback baseCallback ? baseCallback : null;
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
