/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<String> knownCollections = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

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
        boolean exists = vectorStore.tableExists(collectionName);
        if (exists) {
            knownCollections.add(collectionName);
        }
        return exists;
    }

    /**
     * Create a collection when the backend supports explicit bootstrap.
     */
    public void createCollection(String collectionName, int dimension, Map<String, Object> schema) {
        if (collectionExist(collectionName)) {
            return;
        }
        VectorStore scoped = vectorStore.withCollection(collectionName);
        knownCollections.add(collectionName);
        collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        scoped.ensureCollection(collectionName, "vector", dimension, schema == null ? Map.of() : schema);
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
        knownCollections.add(tableName);

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
        scoped.add(data, null, bootstrapOptions(vectors));
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
        knownCollections.remove(tableName);
        collectionMetadata.remove(tableName);
    }

    /**
     * List collection names. Not supported by Java VectorStore.
     */
    public List<String> listCollectionNames() {
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            return schemaMutableVectorStore.listCollectionNames();
        }
        return new ArrayList<>(knownCollections);
    }

    /**
     * Update schema. Not supported by Java VectorStore.
     */
    public boolean updateSchema(String collectionName, List<?> operations) {
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            schemaMutableVectorStore.updateSchema(collectionName, new ArrayList<>(operations));
            return true;
        }
        MEMORY_LOGGER.warn("[{}] updateSchema not supported for collection {}.",
                LogEventType.MEMORY_STORE, collectionName);
        return false;
    }

    /**
     * Get collection metadata. Not supported by Java VectorStore.
     */
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            return schemaMutableVectorStore.getCollectionMetadata(collectionName);
        }
        return new LinkedHashMap<>(collectionMetadata.getOrDefault(collectionName, Map.of()));
    }

    /**
     * Update collection metadata. Not supported by Java VectorStore.
     */
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>()).putAll(metadata);
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            schemaMutableVectorStore.updateCollectionMetadata(collectionName, metadata);
            return;
        }
        MEMORY_LOGGER.warn("[{}] updateCollectionMetadata persisted only in SemanticStore metadata cache for collection {}.",
                LogEventType.MEMORY_STORE, collectionName);
    }

    private static Map<String, Object> bootstrapOptions(List<List<Float>> vectors) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("bootstrap_index_type", "vector");
        Integer dimension = inferDimension(vectors);
        if (dimension != null) {
            options.put("dimension", dimension);
        }
        return options;
    }

    private static Integer inferDimension(List<List<Float>> vectors) {
        if (vectors == null) {
            return null;
        }
        for (List<Float> vector : vectors) {
            if (vector != null && !vector.isEmpty()) {
                return vector.size();
            }
        }
        return null;
    }
}
