/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.*;

/**
 * Semantic store wrapping VectorStore for memory module.
 * Handles embedding internally so callers pass text, matching Python BaseVectorStore behaviour.
 */
public class SemanticStore {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final String VECTOR_FIELD = "vector";
    private static final String TEXT_FIELD = "text";
    private static final String ID_FIELD = "id";

    private final VectorStore vectorStore;
    private Embedding embeddingModel;

    public SemanticStore(VectorStore vectorStore) {
        this(vectorStore, null);
    }

    public SemanticStore(VectorStore vectorStore, Embedding embedding) {
        if (vectorStore == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_STORE_INIT_FAILED,
                    "store_type", "semantic store",
                    "error_msg", "vector store instance is None in SemanticStore"
            );
        }
        this.vectorStore = vectorStore;
        this.embeddingModel = embedding;
    }

    public void initializeEmbeddingModel(Embedding embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Check if a collection exists.
     */
    public boolean collectionExist(String collectionName) {
        return vectorStore.tableExists(collectionName);
    }

    /**
     * Create a collection. In Java VectorStore, collections are auto-created on first add.
     */
    public void createCollection(String collectionName, int dimension, Map<String, Object> schema) {
        if (collectionExist(collectionName)) {
            return;
        }
        Map<String, Object> options = new HashMap<>();
        options.put("dimension", dimension);
        if (schema != null) {
            options.put("schema", schema);
        }
        VectorStore scoped = vectorStore.withCollection(collectionName);
        scoped.add(Collections.emptyList(), null, options);
    }

    /**
     * Add documents as (id, text) pairs. Embeds text internally.
     *
     * @param docs      list of (id, text) entries
     * @param tableName collection name
     * @return true on success
     */
    public boolean addDocs(List<Map.Entry<String, String>> docs, String tableName) {
        if (docs == null || docs.isEmpty()) {
            return true;
        }
        if (embeddingModel == null) {
            MEMORY_LOGGER.error("[{}] Embedding model not initialized for collection {}.",
                    LogEventType.MEMORY_STORE, tableName);
            return false;
        }
        VectorStore scoped = vectorStore.withCollection(tableName);

        List<String> texts = new ArrayList<>();
        for (Map.Entry<String, String> doc : docs) {
            texts.add(doc.getValue());
        }

        List<List<Float>> vectors = embeddingModel.embedDocuments(texts, null);

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(ID_FIELD, docs.get(i).getKey());
            row.put(TEXT_FIELD, docs.get(i).getValue());
            row.put(VECTOR_FIELD, vectors.get(i));
            data.add(row);
        }
        scoped.add(data, null, null);
        return true;
    }

    /**
     * Search by text query. Embeds the query internally.
     * Returns list of (id, score) pairs matching Python's return format.
     */
    public List<Map.Entry<String, Double>> search(String query, String tableName, int topK) {
        if (embeddingModel == null) {
            MEMORY_LOGGER.error("[{}] Embedding model not initialized for collection {}.",
                    LogEventType.MEMORY_RETRIEVE, tableName);
            return List.of();
        }
        List<Float> queryVector = embeddingModel.embedQuery(query);
        VectorStore scoped = vectorStore.withCollection(tableName);
        List<SearchResult> results = scoped.search(queryVector, topK, null, null);
        List<Map.Entry<String, Double>> hits = new ArrayList<>();
        if (results != null) {
            for (SearchResult sr : results) {
                hits.add(new AbstractMap.SimpleEntry<>(sr.getId(), sr.getScore()));
            }
        }
        return hits;
    }

    /**
     * Delete documents by IDs from a collection.
     */
    public void deleteDocs(List<String> ids, String tableName) {
        VectorStore scoped = vectorStore.withCollection(tableName);
        scoped.delete(ids, null, null);
    }

    /**
     * Delete an entire collection/table.
     */
    public void deleteTable(String tableName) {
        vectorStore.deleteTable(tableName);
    }

    /**
     * List collection names. Not supported by Java VectorStore.
     */
    public List<String> listCollectionNames() {
        MEMORY_LOGGER.warn("[{}] listCollectionNames not supported by Java VectorStore.",
                LogEventType.MEMORY_STORE);
        return Collections.emptyList();
    }

    /**
     * Update schema. Not supported by Java VectorStore.
     */
    public boolean updateSchema(String collectionName, List<Map<String, Object>> newFields) {
        MEMORY_LOGGER.warn("[{}] updateSchema not supported for collection {}.",
                LogEventType.MEMORY_STORE, collectionName);
        return false;
    }

    /**
     * Get collection metadata. Not supported by Java VectorStore.
     */
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        MEMORY_LOGGER.warn("[{}] getCollectionMetadata not supported for collection {}.",
                LogEventType.MEMORY_STORE, collectionName);
        return Collections.emptyMap();
    }

    /**
     * Update collection metadata. Not supported by Java VectorStore.
     */
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        MEMORY_LOGGER.warn("[{}] updateCollectionMetadata not supported for collection {}.",
                LogEventType.MEMORY_STORE, collectionName);
    }
}
