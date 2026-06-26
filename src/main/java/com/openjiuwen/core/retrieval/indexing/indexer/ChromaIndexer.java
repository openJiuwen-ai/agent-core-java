/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField;
import com.openjiuwen.core.foundation.store.vector_fields.VectorField;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.TqdmCallback;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code ChromaIndexer} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
 */
public class ChromaIndexer extends Indexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromaIndexer.class);

    private final String chromaPath;
    private final String textField;
    private final ChromaVectorField vectorField;
    private final String sparseVectorField;
    private final String metadataField;
    private final String docIdField;
    private final String databaseName;
    private final String distanceMetric;
    private final Map<String, Object> constructConfig;
    private final Map<String, Object> searchConfig;
    private final Class<? extends BaseCallback> docIndexCallback;
    private final ChromaClientGateway client;

    public ChromaIndexer(VectorStoreConfig config, String chromaPath) {
        this(config, chromaPath, "content", "embedding", "sparse_vector", "metadata", "document_id",
                TqdmCallback.class, null);
    }

    public ChromaIndexer(
            VectorStoreConfig config,
            String chromaPath,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            Class<?> docIndexCallback
    ) {
        this(config, chromaPath, textField, vectorField, sparseVectorField, metadataField, docIdField,
                docIndexCallback, null);
    }

    public ChromaIndexer(
            VectorStoreConfig config,
            String chromaPath,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            Class<?> docIndexCallback,
            ChromaClientGateway client
    ) {
        if (chromaPath == null || chromaPath.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_PATH_NOT_FOUND,
                    "error_msg",
                    "chroma_path is required and cannot be empty"
            );
        }
        VectorStoreConfig activeConfig = config == null ? new VectorStoreConfig() : config;
        this.chromaPath = chromaPath;
        this.textField = textField == null ? "content" : textField;
        this.vectorField = normalizeVectorField(vectorField);
        this.sparseVectorField = sparseVectorField == null ? "sparse_vector" : sparseVectorField;
        this.metadataField = metadataField == null ? "metadata" : metadataField;
        this.docIdField = docIdField == null ? "document_id" : docIdField;
        this.databaseName = activeConfig.getDatabaseName() == null ? "" : activeConfig.getDatabaseName();
        this.distanceMetric = normalizeDistanceMetric(activeConfig.getDistanceMetric());
        this.constructConfig = new LinkedHashMap<>(this.vectorField.toDict(VectorField.STAGE_CONSTRUCT));
        this.constructConfig.put("space", this.distanceMetric);
        this.searchConfig = new LinkedHashMap<>(this.vectorField.toDict(VectorField.STAGE_SEARCH));
        this.docIndexCallback = normalizeCallback(docIndexCallback);
        this.client = client == null ? new InMemoryChromaClientGateway() : client;
    }

    /**
     * Compatibility constructor for earlier Java skeleton code.
     *
     * @param ignoredVectorStore ignored by Chroma's Python indexer semantics
     */
    public ChromaIndexer(VectorStore ignoredVectorStore) {
        this(new VectorStoreConfig(), "memory");
    }

    public String getChromaPath() {
        return chromaPath;
    }

    public String getTextField() {
        return textField;
    }

    public ChromaVectorField getVectorField() {
        return vectorField;
    }

    public String getSparseVectorField() {
        return sparseVectorField;
    }

    public String getMetadataField() {
        return metadataField;
    }

    public String getDocIdField() {
        return docIdField;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    public Map<String, Object> getConstructConfig() {
        return new LinkedHashMap<>(constructConfig);
    }

    public Map<String, Object> getSearchConfig() {
        return new LinkedHashMap<>(searchConfig);
    }

    public Class<? extends BaseCallback> getDocIndexCallback() {
        return docIndexCallback;
    }

    @Override
    public CompletableFuture<Boolean> buildIndex(
            List<TextChunk> chunks,
            IndexConfig config,
            Embedding embedModel,
            Map<String, Object> kwargs
    ) {
        try {
            List<TextChunk> safeChunks = chunks == null ? List.of() : chunks;
            IndexConfig activeConfig = config == null ? new IndexConfig() : config;
            String collectionName = activeConfig.getIndexName();
            ChromaCollectionGateway collection = client.getOrCreateCollection(
                    collectionName,
                    constructMetadata(collectionName)
            );
            List<String> duplicateDocIds = duplicateDocIds(collection, safeChunks);
            if (!duplicateDocIds.isEmpty()) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR,
                        "error_msg",
                        "some documents with same doc_id already exist, if they are the same documents, "
                                + "please consider updating instead of adding. duplicate_doc_ids=" + duplicateDocIds
                );
            }

            CompletableFuture<Void> embeddingFuture;
            if (requiresEmbedding(activeConfig)) {
                if (embedModel == null) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND,
                            "error_msg",
                            "embed_model is required for vector/hybrid index type"
                    );
                }
                embeddingFuture = EmbedChunks.computeChunkEmbeddings(
                        safeChunks,
                        embedModel,
                        docIndexCallback,
                        activeConfig.isUseCaptionForImages()
                );
            } else {
                embeddingFuture = CompletableFuture.completedFuture(null);
            }

            return embeddingFuture.thenApply(ignored -> {
                collection.add(toChromaData(safeChunks));
                LOGGER.info("Successfully built index {} with {} chunks", collectionName, safeChunks.size());
                return Boolean.TRUE;
            }).exceptionally(throwable -> {
                Throwable cause = unwrap(throwable);
                if (cause instanceof BaseError baseError) {
                    throw baseError;
                }
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR,
                        null,
                        null,
                        cause,
                        Map.of("error_msg", cause.getMessage() == null ? String.valueOf(cause) : cause.getMessage())
                );
            });
        } catch (BaseError error) {
            throw error;
        } catch (Exception exception) {
            LOGGER.error("Failed to build index: {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", exception.getMessage() == null ? String.valueOf(exception) : exception.getMessage())
            );
        }
    }

    @Override
    public CompletableFuture<Boolean> updateIndex(
            List<TextChunk> chunks,
            String docId,
            IndexConfig config,
            Embedding embedModel,
            Map<String, Object> kwargs
    ) {
        try {
            return deleteIndex(docId, config.getIndexName(), kwargs)
                    .thenCompose(ignored -> buildIndex(chunks, config, embedModel, kwargs))
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to update index: {}", unwrap(throwable).getMessage());
                        return Boolean.FALSE;
                    });
        } catch (Exception exception) {
            LOGGER.error("Failed to update index: {}", exception.getMessage());
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChromaCollectionGateway collection = client.getCollection(indexName);
                List<String> idsToDelete = collection.idsWhere(Map.of(docIdField, docId));
                if (idsToDelete.isEmpty()) {
                    LOGGER.info("No entries found for doc_id={}", docId);
                    return Boolean.FALSE;
                }
                collection.delete(idsToDelete);
                LOGGER.info("Deleted {} entries for doc_id={}", idsToDelete.size(), docId);
                return Boolean.TRUE;
            } catch (Exception exception) {
                LOGGER.error("Failed to delete index entries: {}", exception.getMessage());
                return Boolean.FALSE;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> indexExists(String indexName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                client.getCollection(indexName);
                return Boolean.TRUE;
            } catch (Exception ignored) {
                return Boolean.FALSE;
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
        return indexExists(indexName).thenApply(exists -> {
            if (!Boolean.TRUE.equals(exists)) {
                return Map.of("exists", false);
            }
            try {
                ChromaCollectionGateway collection = client.getCollection(indexName);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("exists", true);
                info.put("collection_name", indexName);
                info.put("count", collection.count());
                info.put("metadata", collection.metadata());
                return info;
            } catch (Exception exception) {
                LOGGER.error("Failed to get index info: {}", exception.getMessage());
                return Map.of("exists", false, "error", exception.getMessage());
            }
        });
    }

    public void close() {
        // ChromaDB Python PersistentClient does not require explicit closing.
    }

    private Map<String, Object> constructMetadata(String collectionName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("collection_name", collectionName);
        metadata.put("database_name", databaseName);
        metadata.put("distance_metric", distanceMetric);
        metadata.put("construct_config", new LinkedHashMap<>(constructConfig));
        metadata.put("search_config", new LinkedHashMap<>(searchConfig));
        metadata.put("text_field", textField);
        metadata.put("vector_field", vectorField.getVectorField());
        metadata.put("sparse_vector_field", sparseVectorField);
        metadata.put("metadata_field", metadataField);
        metadata.put("doc_id_field", docIdField);
        return metadata;
    }

    private List<String> duplicateDocIds(ChromaCollectionGateway collection, List<TextChunk> chunks) {
        Set<String> docIds = new LinkedHashSet<>();
        for (TextChunk chunk : chunks) {
            if (chunk.getDocId() != null && !chunk.getDocId().isBlank()) {
                docIds.add(chunk.getDocId());
            }
        }
        List<String> duplicates = new ArrayList<>();
        docIds.stream().sorted().forEach(docId -> {
            if (!collection.idsWhere(Map.of(docIdField, docId)).isEmpty()) {
                duplicates.add(docId);
            }
        });
        return duplicates;
    }

    private List<Map<String, Object>> toChromaData(List<TextChunk> chunks) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (TextChunk chunk : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", chunk.getId_());
            item.put(docIdField, chunk.getDocId());
            item.put(textField, chunk.getText());
            item.put(metadataField, chunk.getMetadata());
            if (chunk.getEmbedding() != null) {
                item.put(vectorField.getVectorField(), chunk.getEmbedding());
            }
            data.add(item);
        }
        return data;
    }

    private boolean requiresEmbedding(IndexConfig config) {
        String indexType = config.getIndexType();
        return "vector".equals(indexType) || "hybrid".equals(indexType);
    }

    private ChromaVectorField normalizeVectorField(Object value) {
        if (value == null || value instanceof String) {
            ChromaVectorField field = new ChromaVectorField();
            if (value instanceof String text) {
                field.setVectorField(text);
            }
            return field;
        }
        if (value instanceof ChromaVectorField field) {
            return field;
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_INDEXING_VECTOR_FIELD_INVALID,
                "error_msg",
                "vector_field must be either a str or ChromaVectorField instance"
        );
    }

    @SuppressWarnings("unchecked")
    private Class<? extends BaseCallback> normalizeCallback(Class<?> callbackClass) {
        Class<?> activeClass = callbackClass == null ? TqdmCallback.class : callbackClass;
        if (!BaseCallback.class.isAssignableFrom(activeClass)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID,
                    "error_msg",
                    "doc_index_callback in ChromaIndexer must be a subclass of BaseCallback, got "
                            + activeClass.getName()
            );
        }
        return (Class<? extends BaseCallback>) activeClass;
    }

    private String normalizeDistanceMetric(String value) {
        String metric = value == null ? "cosine" : value;
        return metric.replace("dot", "ip").replace("euclidean", "l2");
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Mirrors Python's ChromaDB persistent client boundary in
     * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
     */
    public interface ChromaClientGateway {
        ChromaCollectionGateway getCollection(String name);

        ChromaCollectionGateway getOrCreateCollection(String name, Map<String, Object> metadata);
    }

    /**
     * Mirrors Python's ChromaDB collection boundary in
     * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
     */
    public interface ChromaCollectionGateway {
        Map<String, Object> metadata();

        void add(List<Map<String, Object>> data);

        List<String> idsWhere(Map<String, Object> where);

        void delete(List<String> ids);

        int count();
    }

    /**
     * Mirrors Python's default ChromaDB client created for the indexer in
     * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
     */
    static final class InMemoryChromaClientGateway implements ChromaClientGateway {
        private final Map<String, InMemoryChromaCollectionGateway> collections = new LinkedHashMap<>();

        @Override
        public ChromaCollectionGateway getCollection(String name) {
            InMemoryChromaCollectionGateway collection = collections.get(name);
            if (collection == null) {
                throw new IllegalArgumentException("Collection does not exist: " + name);
            }
            return collection;
        }

        @Override
        public ChromaCollectionGateway getOrCreateCollection(String name, Map<String, Object> metadata) {
            return collections.computeIfAbsent(name, key -> new InMemoryChromaCollectionGateway(metadata));
        }
    }

    /**
     * Mirrors Python's Chroma collection data operations in
     * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
     */
    static final class InMemoryChromaCollectionGateway implements ChromaCollectionGateway {
        private final Map<String, Object> metadata;
        private final Map<String, Map<String, Object>> rowsById = new LinkedHashMap<>();

        private InMemoryChromaCollectionGateway(Map<String, Object> metadata) {
            this.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        }

        @Override
        public Map<String, Object> metadata() {
            return new LinkedHashMap<>(metadata);
        }

        @Override
        public void add(List<Map<String, Object>> data) {
            for (Map<String, Object> item : data) {
                rowsById.put(String.valueOf(item.get("id")), new LinkedHashMap<>(item));
            }
        }

        @Override
        public List<String> idsWhere(Map<String, Object> where) {
            if (where == null || where.isEmpty()) {
                return rowsById.keySet().stream().sorted().toList();
            }
            return rowsById.entrySet().stream()
                    .filter(entry -> matches(entry.getValue(), where))
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        @Override
        public void delete(List<String> ids) {
            if (ids == null) {
                return;
            }
            for (String id : ids) {
                rowsById.remove(id);
            }
        }

        @Override
        public int count() {
            return rowsById.size();
        }

        private boolean matches(Map<String, Object> row, Map<String, Object> where) {
            for (Map.Entry<String, Object> condition : where.entrySet()) {
                if (!Objects.equals(row.get(condition.getKey()), condition.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }
}
