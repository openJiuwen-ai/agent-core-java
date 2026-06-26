/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.foundation.store.vector.VectorStoreUtils;
import com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField;
import com.openjiuwen.core.foundation.store.vector_fields.VectorField;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * ChromaDB vector store implementation.
 *
 * <p>Mirrors Python's {@code ChromaVectorStore} in
 * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.</p>
 */
public class ChromaVectorStore implements VectorStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromaVectorStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_DATABASE = "default_database";
    private static final int DEFAULT_BATCH_SIZE = 128;

    private final VectorStoreConfig config;
    private final String collectionName;
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
    private final ChromaClientAdapter client;
    private final ChromaCollectionAdapter collection;

    public ChromaVectorStore(VectorStoreConfig config, String chromaPath) {
        this(config, chromaPath, "content", "embedding", "sparse_vector", "metadata", "document_id");
    }

    public ChromaVectorStore(VectorStoreConfig config,
                             String chromaPath,
                             String textField,
                             Object vectorField,
                             String sparseVectorField,
                             String metadataField,
                             String docIdField) {
        this(config, chromaPath, textField, vectorField, sparseVectorField, metadataField, docIdField, null);
    }

    public ChromaVectorStore(VectorStoreConfig config,
                             String chromaPath,
                             String textField,
                             Object vectorField,
                             String sparseVectorField,
                             String metadataField,
                             String docIdField,
                             ChromaClientAdapter client) {
        if (chromaPath == null || chromaPath.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_VECTOR_STORE_PATH_NOT_FOUND,
                    "error_msg",
                    "chroma_path is required and cannot be empty"
            );
        }
        this.config = Objects.requireNonNull(config, "config");
        this.collectionName = config.getCollectionName();
        this.chromaPath = chromaPath;
        this.textField = textField == null ? "content" : textField;
        this.vectorField = normalizeVectorField(vectorField);
        this.sparseVectorField = sparseVectorField == null ? "sparse_vector" : sparseVectorField;
        this.metadataField = metadataField == null ? "metadata" : metadataField;
        this.docIdField = docIdField == null ? "document_id" : docIdField;
        this.databaseName = config.getDatabaseName() == null ? "" : config.getDatabaseName();
        this.distanceMetric = normalizeDistanceMetric(config.getDistanceMetric());
        this.constructConfig = new LinkedHashMap<>(this.vectorField.toDict(VectorField.STAGE_CONSTRUCT));
        this.constructConfig.put("space", this.distanceMetric);
        this.searchConfig = new LinkedHashMap<>(this.vectorField.toDict(VectorField.STAGE_SEARCH));
        this.client = client == null
                ? createClient(this.databaseName, this.chromaPath, "", Map.of())
                : client;

        Map<String, Object> hnswConfig = new LinkedHashMap<>(this.constructConfig);
        hnswConfig.putAll(this.searchConfig);
        this.collection = this.client.getOrCreateCollection(
                this.collectionName,
                Map.of("hnsw", hnswConfig)
        );
    }

    public VectorStoreConfig getConfig() {
        return config;
    }

    public String getCollectionName() {
        return collectionName;
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

    public ChromaClientAdapter getClient() {
        return client;
    }

    public ChromaCollectionAdapter getCollection() {
        return collection;
    }

    public static ChromaClientAdapter createClient(String databaseName,
                                                   String pathOrUri,
                                                   String token,
                                                   Map<String, Object> kwargs) {
        String activeDatabase = databaseName == null || databaseName.isBlank() ? DEFAULT_DATABASE : databaseName;
        return new InMemoryChromaClientAdapter(activeDatabase, pathOrUri);
    }

    @Override
    public void checkVectorField() {
        Map<String, Object> configuration = collection.configuration();
        Object hnsw = configuration.get("hnsw");
        VectorStore.checkConfigsMatching(constructConfig, asStringObjectMap(hnsw));
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data,
                                       Integer batchSize,
                                       Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (data == null || data.isEmpty()) {
                return;
            }
            int safeBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
            int processed = 0;
            int total = data.size();
            List<Map<String, Object>> cache = new ArrayList<>();
            for (Map<String, Object> document : data) {
                cache.add(document);
                if (cache.size() >= safeBatchSize) {
                    List<Map<String, Object>> nodes = new ArrayList<>(cache.subList(0, safeBatchSize));
                    cache.clear();
                    addBatch(nodes);
                    processed += nodes.size();
                    if (processed % 100 == 0) {
                        LOGGER.info("Written {}/{} records to {}", processed, total, collectionName);
                    }
                }
            }
            if (!cache.isEmpty()) {
                addBatch(cache);
                processed += cache.size();
            }
            LOGGER.info("Writing completed, total {}/{} records to {}", processed, total, collectionName);
        });
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(List<Double> queryVector,
                                                           int topK,
                                                           VectorStoreFilter filters,
                                                           Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            ChromaCollectionAdapter latestCollection = client.getCollection(collectionName);
            Map<String, Object> results = latestCollection.query(queryVector, null, topK, buildQueryArgs(filters));
            return chromaResultToSearchResults(results, "vector");
        });
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> sparseSearch(String queryText,
                                                                 int topK,
                                                                 VectorStoreFilter filters,
                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChromaCollectionAdapter latestCollection = client.getCollection(collectionName);
                Map<String, Object> results = latestCollection.query(null, queryText, topK, buildQueryArgs(filters));
                if (hasRows(results)) {
                    return chromaResultToSearchResults(results, "sparse");
                }
                return List.of();
            } catch (RuntimeException exception) {
                LOGGER.warn("Text search failed: {}", exception.getMessage());
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> hybridSearch(String queryText,
                                                                 List<Double> queryVector,
                                                                 int topK,
                                                                 double alpha,
                                                                 VectorStoreFilter filters,
                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<RetrievalResult>> resultsByMode = new LinkedHashMap<>();
            if (queryVector != null) {
                try {
                    resultsByMode.put("vector", search(queryVector, topK * 2, filters, kwargs).join());
                } catch (RuntimeException exception) {
                    LOGGER.warn("vector search failed in hybrid search: {}", exception.getMessage());
                    resultsByMode.put("vector", List.of());
                }
            }
            try {
                resultsByMode.put("text", sparseSearch(queryText, topK * 2, filters, kwargs).join());
            } catch (RuntimeException exception) {
                LOGGER.warn("text search failed in hybrid search: {}", exception.getMessage());
                resultsByMode.put("text", List.of());
            }

            List<List<RetrievalResult>> fusionInputs = resultsByMode.values().stream()
                    .filter(results -> results != null && !results.isEmpty())
                    .toList();
            if (fusionInputs.isEmpty()) {
                return List.of();
            }

            Map<String, String> idMapping = new LinkedHashMap<>();
            List<List<RetrievalResult>> retrievalInputs = new ArrayList<>();
            for (List<RetrievalResult> results : fusionInputs) {
                List<RetrievalResult> retrievalResults = new ArrayList<>();
                for (RetrievalResult result : results) {
                    String resultId = result.getChunkId() == null
                            ? Integer.toString(Objects.hashCode(result.getText()))
                            : result.getChunkId();
                    idMapping.put(result.getText(), resultId);
                    Map<String, Object> metadata = new LinkedHashMap<>(result.getMetadata());
                    metadata.put("id", resultId);
                    retrievalResults.add(new RetrievalResult(
                            result.getText(),
                            result.getScore(),
                            metadata,
                            result.getDocId(),
                            metadata.get("chunk_id") == null ? null : String.valueOf(metadata.get("chunk_id"))
                    ));
                }
                retrievalInputs.add(retrievalResults);
            }

            List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(retrievalInputs, 60);
            List<RetrievalResult> output = new ArrayList<>();
            for (int index = 0; index < Math.min(topK, fused.size()); index++) {
                RetrievalResult result = fused.get(index);
                Map<String, Object> metadata = new LinkedHashMap<>(result.getMetadata());
                Object metadataId = metadata.remove("id");
                String resultId = metadataId == null
                        ? idMapping.getOrDefault(result.getText(), Integer.toString(Objects.hashCode(result.getText())))
                        : String.valueOf(metadataId);
                String docId = metadata.get("doc_id") == null ? result.getDocId() : String.valueOf(metadata.get("doc_id"));
                output.add(new RetrievalResult(result.getText(), result.getScore(), metadata, docId, resultId));
            }
            return List.copyOf(output);
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(List<String> ids,
                                             DeleteFilter filterExpr,
                                             Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChromaCollectionAdapter latestCollection = client.getCollection(collectionName);
                Map<String, Object> queryArgs = new LinkedHashMap<>();
                queryArgs.put("ids", ids);
                if (filterExpr != null && filterExpr.queryExpr() != null) {
                    queryArgs.putAll(queryExprArgs(filterExpr.queryExpr()));
                } else if (filterExpr != null && filterExpr.expression() != null) {
                    LOGGER.warn("ChromaDB does not support string filter expressions.");
                    return Boolean.FALSE;
                }
                latestCollection.delete(ids, queryArgs);
                return Boolean.TRUE;
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to delete vectors: {}", exception.getMessage());
                return Boolean.FALSE;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return CompletableFuture.supplyAsync(() -> client.listCollections().stream()
                .anyMatch(chromaCollection -> Objects.equals(chromaCollection.name(), tableName)));
    }

    @Override
    public CompletableFuture<Void> deleteTable(String tableName) {
        return CompletableFuture.runAsync(() -> client.deleteCollection(tableName));
    }

    @Override
    public void close() {
        // ChromaDB Python PersistentClient does not expose a close method.
    }

    private void addBatch(List<Map<String, Object>> nodes) {
        List<String> ids = new ArrayList<>();
        List<List<Double>> embeddings = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();

        for (Map<String, Object> node : nodes) {
            Map<String, Object> safeNode = node == null ? Map.of() : node;
            List<Double> embedding = doubleList(safeNode.get(vectorField.getVectorField()));
            if (embedding.isEmpty()) {
                LOGGER.warn("Node has no embedding, skipping: {}", safeNode.getOrDefault("id", "unknown"));
                continue;
            }

            String nodeId = extractNodeId(safeNode);
            ids.add(nodeId);
            embeddings.add(embedding);
            documents.add(safeNode.containsKey(textField) ? pythonString(safeNode.get(textField)) : "");
            metadatas.add(buildMetadata(safeNode));
        }

        if (ids.isEmpty()) {
            return;
        }
        client.getCollection(collectionName).add(ids, embeddings, documents, metadatas);
    }

    private String extractNodeId(Map<String, Object> node) {
        Object rawId = node.containsKey("id") ? node.get("id") : node.getOrDefault("pk", "");
        String nodeId = pythonString(rawId);
        return nodeId.isEmpty() ? UUID.randomUUID().toString() : nodeId;
    }

    private Map<String, Object> buildMetadata(Map<String, Object> node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (node.containsKey(metadataField)) {
            Object rawMetadata = node.get(metadataField);
            if (rawMetadata instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    metadata.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else if (rawMetadata instanceof String text) {
                try {
                    metadata.putAll(OBJECT_MAPPER.readValue(text, MAP_TYPE));
                } catch (JsonProcessingException exception) {
                    LOGGER.warn("Failed to load metadata: {}", rawMetadata);
                }
            }
        }
        if (node.containsKey(docIdField)) {
            metadata.put(docIdField, pythonString(node.get(docIdField)));
        }
        if (node.containsKey("chunk_id")) {
            metadata.put("chunk_id", pythonString(node.get("chunk_id")));
        }
        if (node.containsKey(sparseVectorField)) {
            Object sparseVector = node.get(sparseVectorField);
            if (sparseVector instanceof List<?> || sparseVector instanceof Map<?, ?>) {
                metadata.put(sparseVectorField, writeJson(sparseVector));
            }
        }
        return metadata;
    }

    private List<RetrievalResult> chromaResultToSearchResults(Map<String, Object> results, String mode) {
        if (!hasRows(results)) {
            return List.of();
        }

        List<String> ids = firstStringList(results.get("ids"));
        List<String> documents = firstStringList(results.get("documents"));
        List<Map<String, Object>> metadatas = firstMapList(results.get("metadatas"));
        List<Double> distances = firstDoubleList(results.get("distances"));

        List<RetrievalResult> output = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            String text = index < documents.size() ? documents.get(index) : "";
            Map<String, Object> metadata = index < metadatas.size() && metadatas.get(index) != null
                    ? new LinkedHashMap<>(metadatas.get(index))
                    : new LinkedHashMap<>();

            if (!metadata.containsKey("doc_id")) {
                metadata.put("doc_id", metadata.remove(docIdField));
            }
            if (metadata.containsKey(sparseVectorField)) {
                Object rawSparseVector = metadata.get(sparseVectorField);
                if (rawSparseVector instanceof String sparseJson) {
                    try {
                        metadata.put(sparseVectorField, OBJECT_MAPPER.readValue(sparseJson, Object.class));
                    } catch (JsonProcessingException exception) {
                        LOGGER.warn("Failed to load sparse vector: {}", rawSparseVector);
                    }
                }
            }

            Double rawScore = index < distances.size() ? distances.get(index) : null;
            Double rawScoreScaled = null;
            double finalScore = 0.0d;
            if ("vector".equals(mode)) {
                if (rawScore != null) {
                    rawScoreScaled = normalizeVectorDistance(rawScore);
                    finalScore = rawScoreScaled;
                }
            } else if ("sparse".equals(mode)) {
                if (rawScore != null) {
                    finalScore = rawScore <= 1.0d ? 1.0d - rawScore : rawScore;
                } else {
                    finalScore = 0.5d;
                }
            } else if (rawScore != null) {
                finalScore = rawScore;
            }

            metadata.putIfAbsent("raw_score", rawScore);
            if (rawScoreScaled != null) {
                metadata.putIfAbsent("raw_score_scaled", rawScoreScaled);
            }
            Object docId = metadata.get("doc_id");
            Object chunkId = metadata.get("chunk_id");
            output.add(new RetrievalResult(
                    text,
                    finalScore,
                    metadata,
                    docId == null ? null : String.valueOf(docId),
                    chunkId == null ? ids.get(index) : String.valueOf(chunkId)
            ));
        }
        return List.copyOf(output);
    }

    private double normalizeVectorDistance(double rawScore) {
        if ("l2".equals(distanceMetric)) {
            return VectorStoreUtils.convertL2Squared(rawScore);
        }
        if ("cosine".equals(distanceMetric)) {
            return VectorStoreUtils.convertCosineDistance(rawScore);
        }
        return VectorStoreUtils.convertIpDistance(rawScore);
    }

    private Map<String, Object> buildQueryArgs(VectorStoreFilter filters) {
        if (filters == null) {
            return Map.of();
        }
        if (filters.mapping() != null) {
            Map<String, Object> where = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : filters.mapping().entrySet()) {
                where.put(entry.getKey(), entry.getValue());
            }
            return Map.of("where", where);
        }
        if (filters.queryExpr() != null) {
            return queryExprArgs(filters.queryExpr());
        }
        return Map.of();
    }

    private Map<String, Object> queryExprArgs(QueryExpr queryExpr) {
        Map<String, Object> queryArgs = new LinkedHashMap<>();
        Object expression = queryExpr.toExpr("chroma");
        if (expression instanceof Map<?, ?> expressionMap) {
            for (Map.Entry<?, ?> entry : expressionMap.entrySet()) {
                if (isTruthy(entry.getValue())) {
                    queryArgs.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return queryArgs;
    }

    private boolean hasRows(Map<String, Object> results) {
        List<String> ids = firstStringList(results == null ? null : results.get("ids"));
        return !ids.isEmpty();
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

    private String normalizeDistanceMetric(String value) {
        String metric = value == null ? "cosine" : value;
        return metric.replace("dot", "ip").replace("euclidean", "l2");
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private Map<String, Object> asStringObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private List<Double> doubleList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            }
        }
        return result;
    }

    private List<String> firstStringList(Object raw) {
        if (!(raw instanceof List<?> outer) || outer.isEmpty()) {
            return List.of();
        }
        return stringList(outer.getFirst());
    }

    private List<Map<String, Object>> firstMapList(Object raw) {
        if (!(raw instanceof List<?> outer) || outer.isEmpty()) {
            return List.of();
        }
        return mapList(outer.getFirst());
    }

    private List<Double> firstDoubleList(Object raw) {
        if (!(raw instanceof List<?> outer) || outer.isEmpty()) {
            return List.of();
        }
        return doubleList(outer.getFirst());
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : collection) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private List<Map<String, Object>> mapList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(asStringObjectMap(map));
            }
        }
        return result;
    }

    private String pythonString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "failed to serialize Chroma metadata payload"
            );
        }
    }

    /**
     * Mirrors Python's ChromaDB persistent client boundary in
     * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
     */
    public interface ChromaClientAdapter {
        ChromaCollectionAdapter getCollection(String name);

        ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> configuration);

        void deleteCollection(String name);

        List<ChromaCollectionAdapter> listCollections();
    }

    /**
     * Mirrors Python's ChromaDB collection boundary in
     * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
     */
    public interface ChromaCollectionAdapter {
        String name();

        Map<String, Object> configuration();

        void add(List<String> ids,
                 List<List<Double>> embeddings,
                 List<String> documents,
                 List<Map<String, Object>> metadatas);

        Map<String, Object> query(List<Double> queryEmbedding,
                                  String queryText,
                                  int nResults,
                                  Map<String, Object> queryArgs);

        void delete(List<String> ids, Map<String, Object> queryArgs);
    }

    /**
     * Mirrors Python's default ChromaDB client creation boundary in
     * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
     */
    static final class InMemoryChromaClientAdapter implements ChromaClientAdapter {
        private final Map<String, InMemoryChromaCollectionAdapter> collections = new LinkedHashMap<>();
        private final String databaseName;
        private final String persistDirectory;

        InMemoryChromaClientAdapter(String databaseName, String persistDirectory) {
            this.databaseName = databaseName;
            this.persistDirectory = persistDirectory;
        }

        @Override
        public ChromaCollectionAdapter getCollection(String name) {
            InMemoryChromaCollectionAdapter collection = collections.get(name);
            if (collection == null) {
                throw new IllegalArgumentException("Collection does not exist: " + name);
            }
            return collection;
        }

        @Override
        public ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> configuration) {
            return collections.computeIfAbsent(
                    name,
                    key -> new InMemoryChromaCollectionAdapter(key, new LinkedHashMap<>(configuration))
            );
        }

        @Override
        public void deleteCollection(String name) {
            collections.remove(name);
        }

        @Override
        public List<ChromaCollectionAdapter> listCollections() {
            return new ArrayList<>(collections.values());
        }

        String databaseName() {
            return databaseName;
        }

        String persistDirectory() {
            return persistDirectory;
        }
    }

    /**
     * Mirrors Python's ChromaDB collection data operations in
     * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
     */
    static final class InMemoryChromaCollectionAdapter implements ChromaCollectionAdapter {
        private final String name;
        private final Map<String, Object> configuration;
        private final Map<String, StoredDocument> rowsById = new LinkedHashMap<>();

        InMemoryChromaCollectionAdapter(String name, Map<String, Object> configuration) {
            this.name = name;
            this.configuration = configuration;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Map<String, Object> configuration() {
            return new LinkedHashMap<>(configuration);
        }

        @Override
        public void add(List<String> ids,
                        List<List<Double>> embeddings,
                        List<String> documents,
                        List<Map<String, Object>> metadatas) {
            for (int index = 0; index < ids.size(); index++) {
                Map<String, Object> metadata = index < metadatas.size() && metadatas.get(index) != null
                        ? new LinkedHashMap<>(metadatas.get(index))
                        : new LinkedHashMap<>();
                rowsById.put(ids.get(index), new StoredDocument(
                        ids.get(index),
                        index < embeddings.size() ? new ArrayList<>(embeddings.get(index)) : List.of(),
                        index < documents.size() ? documents.get(index) : "",
                        metadata
                ));
            }
        }

        @Override
        public Map<String, Object> query(List<Double> queryEmbedding,
                                         String queryText,
                                         int nResults,
                                         Map<String, Object> queryArgs) {
            Map<String, Object> where = queryMap(queryArgs, "where");
            Map<String, Object> whereDocument = queryMap(queryArgs, "where_document");
            String metric = metric();
            List<StoredDocument> rows = rowsById.values().stream()
                    .filter(row -> matchesWhere(row.metadata(), where))
                    .filter(row -> matchesWhereDocument(row.document(), whereDocument))
                    .sorted(Comparator.comparingDouble(row -> distance(metric, queryEmbedding, queryText, row)))
                    .limit(Math.max(0, nResults))
                    .toList();

            List<String> ids = new ArrayList<>();
            List<String> documents = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            for (StoredDocument row : rows) {
                ids.add(row.id());
                documents.add(row.document());
                metadatas.add(new LinkedHashMap<>(row.metadata()));
                distances.add(distance(metric, queryEmbedding, queryText, row));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ids", List.of(ids));
            result.put("documents", List.of(documents));
            result.put("metadatas", List.of(metadatas));
            result.put("distances", List.of(distances));
            return result;
        }

        @Override
        public void delete(List<String> ids, Map<String, Object> queryArgs) {
            if (ids != null) {
                for (String id : ids) {
                    rowsById.remove(id);
                }
            }
            Map<String, Object> where = queryMap(queryArgs, "where");
            if (!where.isEmpty()) {
                List<String> deleteIds = rowsById.values().stream()
                        .filter(row -> matchesWhere(row.metadata(), where))
                        .map(StoredDocument::id)
                        .toList();
                for (String id : deleteIds) {
                    rowsById.remove(id);
                }
            }
        }

        private Map<String, Object> queryMap(Map<String, Object> queryArgs, String key) {
            if (queryArgs == null || queryArgs.isEmpty()) {
                return Map.of();
            }
            Object value = queryArgs.get(key);
            if (value == null && !queryArgs.containsKey("where") && !queryArgs.containsKey("where_document")) {
                value = queryArgs;
            }
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        private String metric() {
            Object hnsw = configuration.get("hnsw");
            if (hnsw instanceof Map<?, ?> map && map.get("space") != null) {
                return String.valueOf(map.get("space"));
            }
            return "cosine";
        }

        private boolean matchesWhere(Map<String, Object> metadata, Map<String, Object> where) {
            if (where == null || where.isEmpty()) {
                return true;
            }
            if (where.containsKey("$and")) {
                return conditionList(where.get("$and")).stream().allMatch(condition -> matchesWhere(metadata, condition));
            }
            if (where.containsKey("$or")) {
                return conditionList(where.get("$or")).stream().anyMatch(condition -> matchesWhere(metadata, condition));
            }
            for (Map.Entry<String, Object> entry : where.entrySet()) {
                if (!matchesCondition(metadata.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private List<Map<String, Object>> conditionList(Object raw) {
            if (!(raw instanceof Collection<?> collection)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> condition = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        condition.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(condition);
                }
            }
            return result;
        }

        private boolean matchesCondition(Object actual, Object expected) {
            if (!(expected instanceof Map<?, ?> condition)) {
                return Objects.equals(actual, expected);
            }
            for (Map.Entry<?, ?> entry : condition.entrySet()) {
                String operator = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                int comparison = compare(actual, value);
                switch (operator) {
                    case "$gt":
                        if (comparison <= 0) {
                            return false;
                        }
                        break;
                    case "$gte":
                        if (comparison < 0) {
                            return false;
                        }
                        break;
                    case "$lt":
                        if (comparison >= 0) {
                            return false;
                        }
                        break;
                    case "$lte":
                        if (comparison > 0) {
                            return false;
                        }
                        break;
                    case "$in":
                        if (!(value instanceof Collection<?> values) || !values.contains(actual)) {
                            return false;
                        }
                        break;
                    case "$nin":
                        if (value instanceof Collection<?> values && values.contains(actual)) {
                            return false;
                        }
                        break;
                    default:
                        if (!Objects.equals(actual, value)) {
                            return false;
                        }
                        break;
                }
            }
            return true;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private int compare(Object actual, Object expected) {
            if (actual instanceof Number left && expected instanceof Number right) {
                return Double.compare(left.doubleValue(), right.doubleValue());
            }
            if (actual instanceof Comparable comparable && expected != null) {
                return comparable.compareTo(expected);
            }
            return String.valueOf(actual).compareTo(String.valueOf(expected));
        }

        private boolean matchesWhereDocument(String document, Map<String, Object> whereDocument) {
            if (whereDocument == null || whereDocument.isEmpty()) {
                return true;
            }
            if (whereDocument.containsKey("$and")) {
                return conditionList(whereDocument.get("$and")).stream()
                        .allMatch(condition -> matchesWhereDocument(document, condition));
            }
            if (whereDocument.containsKey("$or")) {
                return conditionList(whereDocument.get("$or")).stream()
                        .anyMatch(condition -> matchesWhereDocument(document, condition));
            }
            Object contains = whereDocument.get("$contains");
            if (contains != null && !document.contains(String.valueOf(contains))) {
                return false;
            }
            Object regex = whereDocument.get("$regex");
            return regex == null || Pattern.compile(String.valueOf(regex)).matcher(document).find();
        }

        private double distance(String metric, List<Double> queryEmbedding, String queryText, StoredDocument row) {
            if (queryEmbedding == null) {
                return textDistance(queryText, row.document());
            }
            if ("l2".equals(metric)) {
                double sum = 0.0d;
                for (int index = 0; index < Math.min(queryEmbedding.size(), row.embedding().size()); index++) {
                    double delta = queryEmbedding.get(index) - row.embedding().get(index);
                    sum += delta * delta;
                }
                return sum;
            }
            double dot = 0.0d;
            double leftNorm = 0.0d;
            double rightNorm = 0.0d;
            for (int index = 0; index < Math.min(queryEmbedding.size(), row.embedding().size()); index++) {
                double left = queryEmbedding.get(index);
                double right = row.embedding().get(index);
                dot += left * right;
                leftNorm += left * left;
                rightNorm += right * right;
            }
            if ("ip".equals(metric)) {
                return 1.0d - dot;
            }
            if (leftNorm == 0.0d || rightNorm == 0.0d) {
                return 2.0d;
            }
            return 1.0d - dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        }

        private double textDistance(String queryText, String document) {
            String query = queryText == null ? "" : queryText;
            if (query.isEmpty()) {
                return 1.0d;
            }
            return document != null && document.contains(query) ? 0.0d : 1.0d;
        }

        /**
         * Mirrors Python's transient Chroma document payload in
         * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
         */
        private record StoredDocument(String id,
                                      List<Double> embedding,
                                      String document,
                                      Map<String, Object> metadata) {
        }
    }
}
