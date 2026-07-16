/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.common.MemoryUtils;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic store wrapping VectorStore for memory module.
 * Handles embedding internally so callers pass text, matching Python BaseVectorStore behaviour.
 * 
 * @since 0.1.7
 */
public class SemanticStore {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final String VECTOR_FIELD = "embedding";
    private static final String ID_FIELD = "id";

    private final VectorStore vectorStore;
    private Embedding embeddingModel;

    /**
     * ConcurrentHashMap.newKeySet.
     * 
     * @since 0.1.7
     */
    private final Set<String> knownCollections = ConcurrentHashMap.newKeySet();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

    /**
     * SemanticStore.
     * 
     * @param vectorStore vectorStore
     * @since 0.1.7
     */
    public SemanticStore(VectorStore vectorStore) {
        this(vectorStore, null);
    }

    /**
     * SemanticStore.
     * 
     * @param vectorStore vectorStore
     * @param embedding embedding
     * @since 0.1.7
     */
    public SemanticStore(VectorStore vectorStore, Embedding embedding) {
        if (vectorStore == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_INIT_FAILED, "store_type", "semantic store",
                    "error_msg", "vector store instance is None in SemanticStore");
        }
        this.vectorStore = vectorStore;
        this.embeddingModel = embedding;
    }

    /**
     * initializeEmbeddingModel.
     * 
     * @param embeddingModel embeddingModel
     * @since 0.1.7
     */
    public void initializeEmbeddingModel(Embedding embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Check if a collection exists.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
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
     * 
     * @param collectionName collectionName
     * @param dimension dimension
     * @param schema schema
     * @since 0.1.7
     */
    public void createCollection(String collectionName, int dimension, Map<String, Object> schema) {
        if (collectionExist(collectionName)) {
            return;
        }
        createCollectionIfNotExists(collectionName, dimension, schema == null ? Map.of() : schema);
    }

    /**
     * Add documents as (id, text) pairs. Embeds text internally.
     * 
     * @param docs list of (id, text) entries
     * @param tableName collection name
     * @return true on success
     * @since 0.1.7
     */
    public boolean addDocs(List<Map.Entry<String, String>> docs, String tableName) {
        if (docs == null || docs.isEmpty()) {
            return true;
        }
        if (embeddingModel == null) {
            MEMORY_LOGGER.error("[{}] Embedding model not initialized for collection {}.", LogEventType.MEMORY_STORE,
                    tableName);
            return false;
        }
        List<String> texts = new ArrayList<>();
        for (Map.Entry<String, String> doc : docs) {
            texts.add(doc.getValue());
        }

        List<List<Float>> vectors = embeddingModel.embedDocuments(texts, null);
        if (vectors.size() != docs.size()) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID, "store_type", "semantic store",
                    "error_msg", "memory_ids and embeddings must have same length");
        }
        Integer dimension = inferDimension(vectors);
        if (dimension != null) {
            createCollectionIfNotExists(tableName, dimension, Map.of());
        }

        VectorStore scoped = vectorStore.withCollection(tableName);

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(ID_FIELD, docs.get(i).getKey());
            String textField = vectorStore.getTextField();
            if (textField != null && !textField.isBlank()) {
                row.put(textField, docs.get(i).getValue());
            }
            String vectorField = vectorStore.getVectorField();
            row.put(vectorField == null || vectorField.isBlank() ? VECTOR_FIELD : vectorField, vectors.get(i));
            data.add(row);
        }
        scoped.add(data, null, bootstrapOptions(vectors));
        return true;
    }

    /**
     * Search by text query. Embeds the query internally.
     * Returns list of (id, score) pairs matching Python's return format.
     * 
     * @param query query
     * @param tableName tableName
     * @param topK topK
     * @return the result
     * @since 0.1.7
     */
    public List<Map.Entry<String, Double>> search(String query, String tableName, int topK) {
        if (embeddingModel == null) {
            MEMORY_LOGGER.error("[{}] Embedding model not initialized for collection {}.", LogEventType.MEMORY_RETRIEVE,
                    tableName);
            return List.of();
        }
        List<Float> queryVector = embeddingModel.embedQuery(query);
        if (!collectionExist(tableName)) {
            return List.of();
        }
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
     * 
     * @param ids ids
     * @param tableName tableName
     * @since 0.1.7
     */
    public void deleteDocs(List<String> ids, String tableName) {
        if (!collectionExist(tableName)) {
            return;
        }
        VectorStore scoped = vectorStore.withCollection(tableName);
        scoped.delete(ids, null, null);
    }

    /**
     * Delete an entire collection/table.
     * 
     * @param tableName tableName
     * @since 0.1.7
     */
    public void deleteTable(String tableName) {
        vectorStore.deleteTable(tableName);
        knownCollections.remove(tableName);
        collectionMetadata.remove(tableName);
    }

    /**
     * List collection names. Not supported by Java VectorStore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> listCollectionNames() {
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            return schemaMutableVectorStore.listCollectionNames();
        }
        return new ArrayList<>(knownCollections);
    }

    /**
     * Update schema. Not supported by Java VectorStore.
     * 
     * @param collectionName collectionName
     * @param operations operations
     * @return the result
     * @since 0.1.7
     */
    public boolean updateSchema(String collectionName, List<?> operations) {
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            schemaMutableVectorStore.updateSchema(collectionName, new ArrayList<>(operations));
            return true;
        }
        MEMORY_LOGGER.warn("[{}] updateSchema not supported for collection {}.", LogEventType.MEMORY_STORE,
                collectionName);
        return false;
    }

    /**
     * Get collection metadata. Not supported by Java VectorStore.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            return schemaMutableVectorStore.getCollectionMetadata(collectionName);
        }
        return new LinkedHashMap<>(collectionMetadata.getOrDefault(collectionName, Map.of()));
    }

    /**
     * Update collection metadata. Not supported by Java VectorStore.
     * 
     * @param collectionName collectionName
     * @param metadata metadata
     * @since 0.1.7
     */
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>()).putAll(metadata);
        if (vectorStore instanceof SchemaMutableVectorStore schemaMutableVectorStore) {
            schemaMutableVectorStore.updateCollectionMetadata(collectionName, metadata);
            return;
        }
        MEMORY_LOGGER.warn(
                "[{}] updateCollectionMetadata persisted only in SemanticStore metadata cache for collection {}.",
                LogEventType.MEMORY_STORE, collectionName);
    }

    /**
     * createCollectionIfNotExists.
     * 
     * @param collectionName collectionName
     * @param dimension dimension
     * @param schema schema
     * @since 0.1.7
     */
    private void createCollectionIfNotExists(String collectionName, int dimension, Map<String, Object> schema) {
        if (knownCollections.contains(collectionName)) {
            return;
        }
        if (vectorStore.tableExists(collectionName)) {
            knownCollections.add(collectionName);
            return;
        }
        VectorStore scoped = vectorStore.withCollection(collectionName);
        scoped.ensureCollection(collectionName, "vector", dimension, schema);
        knownCollections.add(collectionName);
        int schemaVersion = latestSchemaVersion(collectionName);
        updateCollectionMetadata(collectionName, Map.of("schema_version", schemaVersion));
    }

    /**
     * latestSchemaVersion.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
     */
    private static int latestSchemaVersion(String collectionName) {
        String memType = MemoryUtils.parseMemTypeFromIdxName(collectionName);
        return MigrationPlan.getVectorRegistry().getCurrentVersion("vector_" + memType);
    }

    /**
     * bootstrapOptions.
     * 
     * @param vectors vectors
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> bootstrapOptions(List<List<Float>> vectors) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("bootstrap_index_type", "vector");
        Integer dimension = inferDimension(vectors);
        if (dimension != null) {
            options.put("dimension", dimension);
        }
        return options;
    }

    /**
     * inferDimension.
     * 
     * @param vectors vectors
     * @return the result
     * @since 0.1.7
     */
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
