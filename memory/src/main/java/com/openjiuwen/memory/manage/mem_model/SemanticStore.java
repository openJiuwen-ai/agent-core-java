/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.memory.common.MemoryUtils;
import com.openjiuwen.memory.migration.MigrationPlan;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic store wrapping {@link BaseVectorStore} for the Memory module.
 * Handles embedding internally so callers pass text, matching Python BaseVectorStore behaviour.
 * 
 * @since 0.1.7
 */
public class SemanticStore {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final String VECTOR_FIELD = "embedding";
    private static final String ID_FIELD = "id";
    private static final int MAX_COLLECTION_NAME_LENGTH = 255;

    private final BaseVectorStore vectorStore;
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
    public SemanticStore(BaseVectorStore vectorStore) {
        this(vectorStore, null);
    }

    /**
     * SemanticStore.
     * 
     * @param vectorStore vectorStore
     * @param embedding embedding
     * @since 0.1.7
     */
    public SemanticStore(BaseVectorStore vectorStore, Embedding embedding) {
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
        boolean exists = callVectorStore(() -> vectorStore.collectionExists(collectionName, Map.of()),
                "check collection " + collectionName);
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
        if (!isValidCollectionName(tableName)) {
            MEMORY_LOGGER.error("[{}] Invalid vector collection name.", LogEventType.MEMORY_STORE);
            return false;
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

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(ID_FIELD, docs.get(i).getKey());
            row.put("text", docs.get(i).getValue());
            row.put(VECTOR_FIELD, vectors.get(i));
            data.add(row);
        }
        runVectorStore(() -> vectorStore.addDocs(tableName, data, bootstrapOptions(vectors)),
                "add documents to collection " + tableName);
        return true;
    }

    private static boolean isValidCollectionName(String collectionName) {
        if (collectionName == null || collectionName.isBlank()
                || collectionName.length() > MAX_COLLECTION_NAME_LENGTH) {
            return false;
        }
        if (!Character.isLetter(collectionName.charAt(0)) && collectionName.charAt(0) != '_') {
            return false;
        }
        for (int i = 0; i < collectionName.length(); i++) {
            char current = collectionName.charAt(i);
            if (current > 0x7F || (!Character.isLetterOrDigit(current) && current != '_')) {
                return false;
            }
        }
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
        List<VectorSearchResult> results = callVectorStore(
                () -> vectorStore.search(tableName, queryVector, VECTOR_FIELD, topK, null, Map.of()),
                "search collection " + tableName);
        List<Map.Entry<String, Double>> hits = new ArrayList<>();
        if (results != null) {
            for (VectorSearchResult result : results) {
                Object id = result.getFields().get(ID_FIELD);
                if (id != null) {
                    hits.add(new AbstractMap.SimpleEntry<>(String.valueOf(id), result.getScore()));
                }
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
        runVectorStore(() -> vectorStore.deleteDocsByIds(tableName, ids, Map.of()),
                "delete documents from collection " + tableName);
    }

    /**
     * Delete an entire collection/table.
     * 
     * @param tableName tableName
     * @since 0.1.7
     */
    public void deleteTable(String tableName) {
        runVectorStore(() -> vectorStore.deleteCollection(tableName, Map.of()),
                "delete collection " + tableName);
        knownCollections.remove(tableName);
        collectionMetadata.remove(tableName);
    }

    /**
     * List collection names.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> listCollectionNames() {
        Set<String> collectionNames = new LinkedHashSet<>(callVectorStore(vectorStore::listCollectionNames,
                "list collections"));
        collectionNames.addAll(knownCollections);
        return new ArrayList<>(collectionNames);
    }

    /**
     * Update schema.
     * 
     * @param collectionName collectionName
     * @param operations operations
     * @return the result
     * @since 0.1.7
     */
    public boolean updateSchema(String collectionName, List<?> operations) {
        runVectorStore(() -> vectorStore.updateSchema(collectionName, new ArrayList<>(operations)),
                "update schema for collection " + collectionName);
        return true;
    }

    /**
     * Get collection metadata.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        Map<String, Object> metadata = new LinkedHashMap<>(collectionMetadata.getOrDefault(collectionName, Map.of()));
        Map<String, Object> backendMetadata = callVectorStore(() -> vectorStore.getCollectionMetadata(collectionName),
                "get metadata for collection " + collectionName);
        if (backendMetadata != null) {
            metadata.putAll(backendMetadata);
        }
        return metadata;
    }

    /**
     * Update collection metadata.
     * 
     * @param collectionName collectionName
     * @param metadata metadata
     * @since 0.1.7
     */
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>()).putAll(metadata);
        runVectorStore(() -> vectorStore.updateCollectionMetadata(collectionName, metadata),
                "update metadata for collection " + collectionName);
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
        if (collectionExist(collectionName)) {
            knownCollections.add(collectionName);
            return;
        }
        Map<String, Object> options = new LinkedHashMap<>(schema);
        options.putIfAbsent("index_type", "vector");
        options.putIfAbsent("dimension", dimension);
        runVectorStore(() -> vectorStore.createCollection(collectionName, collectionSchema(dimension), options),
                "create collection " + collectionName);
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

    private static CollectionSchema collectionSchema(int dimension) {
        return CollectionSchema.fromFields(
                List.of(FieldSchema.builder().name(ID_FIELD).dtype(VectorDataType.VARCHAR).isPrimary(true)
                                .maxLength(256).build(),
                        FieldSchema.builder().name(VECTOR_FIELD).dtype(VectorDataType.FLOAT_VECTOR).dim(dimension)
                                .build(),
                        FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                        FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()),
                "Memory semantic store", false);
    }

    private static <T> T callVectorStore(VectorStoreCall<T> call, String operation) {
        try {
            return call.execute();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to " + operation, e);
        }
    }

    private static void runVectorStore(VectorStoreAction action, String operation) {
        callVectorStore(() -> {
            action.execute();
            return Boolean.TRUE;
        }, operation);
    }

    @FunctionalInterface
    private interface VectorStoreCall<T> {
        T execute() throws Exception;
    }

    @FunctionalInterface
    private interface VectorStoreAction {
        void execute() throws Exception;
    }
}
