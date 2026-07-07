/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusVectorField;
import com.openjiuwen.core.foundation.store.vector_fields.VectorField;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.openjiuwen.core.common.VirtualThreadSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Milvus vector store implementation.
 *
 * <p>Mirrors Python's {@code MilvusVectorStore} in
 * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.</p>
 */
public class MilvusVectorStore implements VectorStore {

    public static final String PYTHON_MODULE = "openjiuwen/core/retrieval/vector_store/milvus_store.py";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final java.util.concurrent.Executor IO_EXECUTOR = VirtualThreadSupport.newThreadPerTaskExecutor("milvus-retrieval-vector-store-io");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger(MilvusVectorStore.class.getName());
    private static final int DEFAULT_BATCH_SIZE = 128;
    private static final int RRF_K = 60;
    private static final double DEFAULT_L2_MAX_DISTANCE = 4.0d;

    private final VectorStoreConfig config;
    private final String milvusUri;
    private final String milvusToken;
    private final String textField;
    private final MilvusVectorField vectorField;
    private final String sparseVectorField;
    private final String metadataField;
    private final String docIdField;
    private final String databaseName;
    private final String distanceMetric;
    private final String milvusMetricType;
    private final String indexType;
    private final Map<String, Object> constructConfig;
    private final Map<String, Object> searchConfig;
    private final String milvusAlias;
    private final MilvusClientFacade client;
    private boolean collectionLoaded;

    public MilvusVectorStore(VectorStoreConfig config, String milvusUri) {
        this(config, milvusUri, null);
    }

    public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String milvusToken) {
        this(
                config,
                milvusUri,
                milvusToken,
                "content",
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                null,
                Map.of()
        );
    }

    public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String milvusToken, String textField) {
        this(
                config,
                milvusUri,
                milvusToken,
                resolveLegacyTextField(textField),
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                null,
                Map.of()
        );
    }

    public MilvusVectorStore(MilvusClientV2 client, VectorStoreConfig config, String indexType) {
        this(
                config,
                "mock://milvus",
                null,
                indexType,
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                null,
                new DefaultMilvusClientFacade(config == null ? "" : config.getDatabaseName(), client)
        );
    }

    public MilvusVectorStore(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            String vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            String milvusAlias,
            Map<String, Object> kwargs
    ) {
        this(
                config,
                milvusUri,
                milvusToken,
                textField,
                (Object) vectorField,
                sparseVectorField,
                metadataField,
                docIdField,
                milvusAlias,
                kwargs
        );
    }

    public MilvusVectorStore(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            MilvusVectorField vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            String milvusAlias,
            Map<String, Object> kwargs
    ) {
        this(
                config,
                milvusUri,
                milvusToken,
                textField,
                (Object) vectorField,
                sparseVectorField,
                metadataField,
                docIdField,
                milvusAlias,
                kwargs
        );
    }

    MilvusVectorStore(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            String milvusAlias,
            Map<String, Object> kwargs
    ) {
        this(
                config,
                milvusUri,
                milvusToken,
                textField,
                vectorField,
                sparseVectorField,
                metadataField,
                docIdField,
                milvusAlias,
                new DefaultMilvusClientFacade(
                        config == null ? "" : config.getDatabaseName(),
                        milvusUri,
                        milvusToken,
                        kwargs
                )
        );
    }

    MilvusVectorStore(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            String milvusAlias,
            MilvusClientFacade client
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.milvusUri = Objects.requireNonNull(milvusUri, "milvusUri");
        this.milvusToken = milvusToken;
        this.textField = defaultIfBlank(textField, "content");
        this.vectorField = resolveVectorField(vectorField);
        this.sparseVectorField = defaultIfBlank(sparseVectorField, "sparse_vector");
        this.metadataField = defaultIfBlank(metadataField, "metadata");
        this.docIdField = defaultIfBlank(docIdField, "document_id");
        this.databaseName = config.getDatabaseName() == null ? "" : config.getDatabaseName();
        this.distanceMetric = normalizeDistanceMetricLabel(config.getDistanceMetric());
        this.milvusMetricType = toMilvusMetricType(distanceMetric);
        this.indexType = resolveLegacyIndexType(textField, this.vectorField);
        this.constructConfig = buildConstructConfig(this.vectorField, milvusMetricType);
        this.searchConfig = new LinkedHashMap<>(this.vectorField.toDict(VectorField.STAGE_SEARCH));
        this.milvusAlias = CommonUtils.createMilvusAlias(milvusAlias, milvusUri, "", milvusToken);
        this.client = Objects.requireNonNull(client, "client");
    }

    public static MilvusClientV2 createClient(String databaseName, String pathOrUri, String token) {
        return createClient(databaseName, pathOrUri, token, Map.of());
    }

    public static MilvusClientV2 createClient(
            String databaseName,
            String pathOrUri,
            String token,
            Map<String, Object> kwargs
    ) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
        long timeoutMs = Math.max(1L, Math.round(doubleValue(safeKwargs.get("timeout"), 3.0d) * 1000.0d));
        String dbName = databaseName == null || databaseName.isBlank() ? "default" : databaseName;
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(pathOrUri)
                .connectTimeoutMs(timeoutMs)
                .rpcDeadlineMs(timeoutMs)
                .enablePrecheck(false);
        if (token != null && !token.isBlank()) {
            builder.token(token);
        }
        if (!"default".equals(dbName)) {
            builder.dbName(dbName);
        }
        MilvusClientV2 createdClient = new MilvusClientV2(builder.build());
        if (!"default".equals(dbName)) {
            if (!createdClient.listDatabases().getDatabaseNames().contains(dbName)) {
                createdClient.createDatabase(CreateDatabaseReq.builder().databaseName(dbName).build());
            }
            try {
                createdClient.useDatabase(dbName);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while switching Milvus database", error);
            }
        }
        return createdClient;
    }

    public MilvusClientFacade getClient() {
        return client;
    }

    public String getCollectionName() {
        return config.getCollectionName();
    }

    public String getMilvusUri() {
        return milvusUri;
    }

    public String getMilvusToken() {
        return milvusToken;
    }

    public String getTextField() {
        return textField;
    }

    public MilvusVectorField getVectorFieldConfig() {
        return vectorField;
    }

    public String getVectorField() {
        return vectorField.getVectorField();
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

    public String getIndexType() {
        return indexType;
    }

    public Map<String, Object> getConstructConfig() {
        return new LinkedHashMap<>(constructConfig);
    }

    public Map<String, Object> getSearchConfig() {
        return new LinkedHashMap<>(searchConfig);
    }

    public String getMilvusAlias() {
        return milvusAlias;
    }

    public Map<String, Object> getSearchParams(int topK) {
        Map<String, Object> params = new LinkedHashMap<>(searchConfig);
        Object factor = params.remove("efSearchFactor");
        if (factor instanceof Number number) {
            params.put("ef", Math.round(topK * number.doubleValue()));
        }
        return params;
    }

    @Override
    public void checkVectorField() {
        String collectionName = getCollectionName();
        if (!client.hasCollection(collectionName)) {
            return;
        }
        Map<String, Object> actual = client.describeIndex(collectionName, getVectorField());
        if (actual.isEmpty()) {
            List<FieldDescription> vectorFields = client.describeCollection(collectionName).fields().stream()
                    .filter(FieldDescription::isFloatVector)
                    .toList();
            StringBuilder vectorFieldList = new StringBuilder();
            for (int index = 0; index < vectorFields.size(); index++) {
                FieldDescription field = vectorFields.get(index);
                vectorFieldList.append("- [")
                        .append(index)
                        .append("] ")
                        .append(field.name())
                        .append(": ")
                        .append(field.params())
                        .append(System.lineSeparator());
            }
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "MilvusVectorStore has vector_field at " + getVectorField()
                            + " while actual database has vector field(s) at:" + System.lineSeparator()
                            + vectorFieldList + "You may want to call delete_collection method on collection \""
                            + collectionName + "\""
            );
        }
        String indexType = vectorField.getIndexType();
        String variant = vectorField.getVariant() == null ? "" : vectorField.getVariant();
        if (!"auto".equals(indexType)) {
            String returnedType = String.valueOf(actual.getOrDefault("index_type", "unknown"));
            String expectedPrefix = indexType.toUpperCase(Locale.ROOT);
            if (!(returnedType.startsWith(expectedPrefix) && returnedType.endsWith(variant))) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "error_msg",
                        "MilvusVectorStore has index_type of " + indexType
                                + " while actual database has index_type of " + returnedType
                                + ", do not change index_type after Knowledge Base is constructed."
                );
            }
        }
        VectorStore.checkConfigsMatching(constructConfig, actual);
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (data == null || data.isEmpty()) {
                return;
            }
            ensureLoaded();
            int safeBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
            client.insert(getCollectionName(), data, safeBatchSize);
            client.flush(getCollectionName());
            LOGGER.info("Writing completed, total " + data.size() + "/" + data.size()
                    + " records to " + getCollectionName());
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(
            List<Double> queryVector,
            int topK,
            VectorStoreFilter filters,
            Map<String, Object> kwargs
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
                return List.of();
            }
            ensureLoaded();
            List<SearchHit> hits = client.search(
                    getCollectionName(),
                    SearchRequest.vector(getVectorField(), queryVector),
                    milvusMetricType,
                    topK,
                    List.of(textField, metadataField, docIdField, "chunk_id"),
                    getSearchParams(topK),
                    toFilterExpression(filters)
            );
            return toRetrievalResults(hits, SearchMode.VECTOR);
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> sparseSearch(
            String queryText,
            int topK,
            VectorStoreFilter filters,
            Map<String, Object> kwargs
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (queryText == null || queryText.isBlank() || topK <= 0) {
                return List.of();
            }
            ensureLoaded();
            try {
                List<SearchHit> hits = client.search(
                        getCollectionName(),
                        SearchRequest.text(sparseVectorField, queryText),
                        "BM25",
                        topK,
                        List.of(textField, metadataField, docIdField),
                        Map.of(),
                        toFilterExpression(filters)
                );
                return toRetrievalResults(hits, SearchMode.SPARSE);
            } catch (RuntimeException error) {
                LOGGER.log(Level.WARNING, "BM25 text search failed: " + error.getMessage(), error);
                return List.of();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> hybridSearch(
            String queryText,
            List<Double> queryVector,
            int topK,
            double alpha,
            VectorStoreFilter filters,
            Map<String, Object> kwargs
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (queryText == null || queryText.isBlank() || topK <= 0) {
                return List.of();
            }
            ensureLoaded();
            String filterExpression = toFilterExpression(filters);
            List<SearchRequest> requests = new ArrayList<>();
            if (queryVector != null && !queryVector.isEmpty()) {
                requests.add(SearchRequest.vector(getVectorField(), queryVector)
                        .withMetricType(milvusMetricType)
                        .withParams(getSearchParams(topK))
                        .withFilter(filterExpression));
            }
            requests.add(SearchRequest.text(sparseVectorField, queryText)
                    .withMetricType("BM25")
                    .withParams(Map.of())
                    .withFilter(filterExpression));
            try {
                List<SearchHit> hits = client.hybridSearch(
                        getCollectionName(),
                        requests,
                        topK,
                        List.of(textField, metadataField, docIdField)
                );
                return toRetrievalResults(hits, SearchMode.HYBRID);
            } catch (RuntimeException error) {
                LOGGER.log(Level.WARNING,
                        "Hybrid search failed, falling back to separate searches: " + error.getMessage(),
                        error);
                return hybridFallback(queryText, queryVector, topK);
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            ensureLoaded();
            try {
                String filter = toDeleteFilterExpression(filterExpr);
                if ((ids == null || ids.isEmpty()) && (filter == null || filter.isBlank())) {
                    return Boolean.FALSE;
                }
                long deleteCount = client.delete(getCollectionName(), ids, filter);
                client.flush(getCollectionName());
                return deleteCount > 0;
            } catch (RuntimeException error) {
                LOGGER.log(Level.SEVERE, "Failed to delete vectors: " + error.getMessage(), error);
                return Boolean.FALSE;
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return CompletableFuture.supplyAsync(() -> client.hasCollection(tableName), IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> deleteTable(String tableName) {
        return CompletableFuture.runAsync(() -> client.dropCollection(tableName), IO_EXECUTOR);
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (RuntimeException error) {
            LOGGER.log(Level.WARNING, "Failed to close Milvus client: " + error.getMessage(), error);
        }
    }

    private List<RetrievalResult> hybridFallback(String queryText, List<Double> queryVector, int topK) {
        List<RetrievalResult> vectorResults = queryVector == null || queryVector.isEmpty()
                ? List.of()
                : search(queryVector, topK, VectorStoreFilter.none(), Map.of()).join();
        List<RetrievalResult> sparseResults = sparseSearch(queryText, topK, VectorStoreFilter.none(), Map.of()).join();
        List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(List.of(vectorResults, sparseResults), RRF_K);
        return trim(fused, topK);
    }

    private List<RetrievalResult> toRetrievalResults(List<SearchHit> hits, SearchMode mode) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RetrievalResult> results = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            Map<String, Object> item = normalizeMilvusHit(hit);
            String resultId = stringValue(firstNonNull(item.get("id"), item.get("pk"), ""));
            String text = stringValue(item.getOrDefault(textField, ""));
            Map<String, Object> metadata = parseMetadata(item.get(metadataField));
            if (!metadata.containsKey("doc_id")) {
                Object fieldDocId = item.remove(docIdField);
                Object metadataDocId = metadata.remove(docIdField);
                metadata.put("doc_id", firstNonNull(fieldDocId, metadataDocId));
            }
            if (item.get("chunk_id") != null) {
                metadata.putIfAbsent("chunk_id", item.get("chunk_id"));
            }

            Double rawScore = doubleValueOrNull(firstNonNull(item.get("score"), item.get("distance")));
            Double rawScoreScaled = null;
            double finalScore = 0.0d;
            if (rawScore != null) {
                if (mode == SearchMode.VECTOR) {
                    rawScoreScaled = normalizeVectorScore(rawScore);
                    finalScore = rawScoreScaled;
                } else {
                    finalScore = rawScore;
                }
            }
            metadata.putIfAbsent("raw_score", rawScore);
            if (rawScoreScaled != null) {
                metadata.putIfAbsent("raw_score_scaled", rawScoreScaled);
            }
            String docId = stringValue(metadata.get("doc_id"));
            String chunkId = firstNonBlank(stringValue(metadata.get("chunk_id")), resultId);
            results.add(new RetrievalResult(text, finalScore, metadata, docId, chunkId));
        }
        return results;
    }

    private Map<String, Object> normalizeMilvusHit(SearchHit hit) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (hit.entity() != null) {
            item.putAll(hit.entity());
        }
        if (hit.id() != null) {
            item.putIfAbsent("id", hit.id());
        }
        if (hit.primaryKey() != null) {
            item.putIfAbsent("pk", hit.primaryKey());
        }
        if (hit.score() != null) {
            item.putIfAbsent("score", hit.score());
        }
        if (hit.distance() != null) {
            item.putIfAbsent("distance", hit.distance());
        }
        return item;
    }

    private String toFilterExpression(VectorStoreFilter filters) {
        if (filters == null) {
            return null;
        }
        if (filters.queryExpr() != null) {
            Object expression = filters.queryExpr().toExpr("milvus");
            return expression == null ? null : String.valueOf(expression);
        }
        Map<String, Object> mapping = filters.mapping();
        if (mapping == null || mapping.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            Object value = entry.getValue();
            parts.add(entry.getKey() + " == " + formatMilvusLiteral(value));
        }
        return String.join(" && ", parts);
    }

    private String toDeleteFilterExpression(DeleteFilter filterExpr) {
        if (filterExpr == null) {
            return null;
        }
        if (filterExpr.queryExpr() != null) {
            Object expression = filterExpr.queryExpr().toExpr("milvus");
            return expression == null ? null : String.valueOf(expression);
        }
        return filterExpr.expression();
    }

    private void ensureLoaded() {
        if (collectionLoaded) {
            return;
        }
        String collectionName = getCollectionName();
        if (client.hasCollection(collectionName)) {
            LOGGER.info("Retrieval Milvus Store: loading collection " + collectionName);
            client.loadCollection(collectionName);
            LOGGER.info("Retrieval Milvus Store: " + collectionName + " collection loaded");
            collectionLoaded = true;
        }
    }

    private double normalizeVectorScore(double rawScore) {
        return switch (milvusMetricType) {
            case "L2" -> Math.max(0.0d, (DEFAULT_L2_MAX_DISTANCE - rawScore) / DEFAULT_L2_MAX_DISTANCE);
            case "COSINE" -> (rawScore + 1.0d) / 2.0d;
            default -> Math.max(0.0d, Math.min(1.0d, (rawScore + 1.0d) / 2.0d));
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (raw instanceof String json) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, MAP_TYPE);
                return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
            } catch (JsonProcessingException error) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private static MilvusVectorField resolveVectorField(Object vectorField) {
        if (vectorField instanceof String fieldName) {
            MilvusAUTO auto = new MilvusAUTO();
            auto.setVectorField(fieldName);
            return auto;
        }
        if (vectorField instanceof MilvusVectorField milvusVectorField) {
            return milvusVectorField;
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_INDEXING_VECTOR_FIELD_INVALID,
                "error_msg",
                "vector_field must be either a str or MilvusVectorField instance"
        );
    }

    private static Map<String, Object> buildConstructConfig(MilvusVectorField vectorField, String distanceMetric) {
        Map<String, Object> result = "auto".equals(vectorField.getIndexType())
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(vectorField.toDict(VectorField.STAGE_CONSTRUCT));
        result.put("metric_type", distanceMetric);
        return result;
    }

    private static String normalizeDistanceMetricLabel(String distanceMetric) {
        String value = distanceMetric == null || distanceMetric.isBlank() ? "cosine" : distanceMetric;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "ip" -> "dot";
            case "l2" -> "euclidean";
            case "cosine", "euclidean", "dot" -> value.toLowerCase(Locale.ROOT);
            default -> value;
        };
    }

    private static String toMilvusMetricType(String distanceMetric) {
        String value = distanceMetric == null || distanceMetric.isBlank() ? "cosine" : distanceMetric;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "dot", "ip" -> "IP";
            case "euclidean", "l2" -> "L2";
            default -> value.toUpperCase(Locale.ROOT);
        };
    }

    private static String resolveLegacyTextField(String value) {
        return isLegacyIndexType(value) ? "content" : value;
    }

    private static String resolveLegacyIndexType(String textField, MilvusVectorField vectorField) {
        return isLegacyIndexType(textField) ? textField : vectorField.getIndexType();
    }

    private static boolean isLegacyIndexType(String value) {
        return "hybrid".equals(value);
    }

    private static String formatMilvusLiteral(Object value) {
        if (value instanceof String string) {
            return QueryExpr.sanitizeStr(string);
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value == null) {
            return "None";
        }
        return String.valueOf(value);
    }

    private static List<RetrievalResult> trim(List<RetrievalResult> results, int topK) {
        if (results.size() <= topK) {
            return results;
        }
        return new ArrayList<>(results.subList(0, topK));
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double doubleValueOrNull(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String toJson(Map<String, Object> params) {
        try {
            return OBJECT_MAPPER.writeValueAsString(params == null ? Map.of() : params);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("failed to serialize Milvus search params", error);
        }
    }

    private static List<Float> toFloatVector(List<Double> queryVector) {
        List<Float> floats = new ArrayList<>(queryVector.size());
        for (Double value : queryVector) {
            floats.add(value == null ? 0.0f : value.floatValue());
        }
        return floats;
    }

    /**
     * Mirrors Python's Milvus client boundary in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    public interface MilvusClientFacade extends AutoCloseable {
        boolean hasCollection(String collectionName);

        void loadCollection(String collectionName);

        void insert(String collectionName, List<Map<String, Object>> rows, int batchSize);

        List<SearchHit> search(
                String collectionName,
                SearchRequest request,
                String metricType,
                int topK,
                List<String> outputFields,
                Map<String, Object> searchParams,
                String filter
        );

        List<SearchHit> hybridSearch(
                String collectionName,
                List<SearchRequest> requests,
                int topK,
                List<String> outputFields
        );

        long delete(String collectionName, List<String> ids, String filter);

        void flush(String collectionName);

        void dropCollection(String collectionName);

        Map<String, Object> describeIndex(String collectionName, String fieldName);

        CollectionDescription describeCollection(String collectionName);

        @Override
        void close();
    }

    /**
     * Mirrors Python's search hit normalization boundary in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    public record SearchHit(Object id,
                            Object primaryKey,
                            Double score,
                            Double distance,
                            Map<String, Object> entity) {
    }

    /**
     * Mirrors Python's Milvus collection description usage in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    public record CollectionDescription(List<FieldDescription> fields) {
    }

    /**
     * Mirrors Python's Milvus field description usage in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    public record FieldDescription(String name, DataType dataType, Map<String, Object> params) {
        boolean isFloatVector() {
            return dataType == DataType.FloatVector;
        }
    }

    /**
     * Mirrors Python's AnnSearchRequest inputs in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    public record SearchRequest(String annsField,
                                List<Double> vector,
                                String text,
                                String metricType,
                                Map<String, Object> params,
                                String filter) {
        static SearchRequest vector(String annsField, List<Double> vector) {
            return new SearchRequest(annsField, new ArrayList<>(vector), null, null, Map.of(), null);
        }

        static SearchRequest text(String annsField, String text) {
            return new SearchRequest(annsField, null, text, null, Map.of(), null);
        }

        SearchRequest withMetricType(String value) {
            return new SearchRequest(annsField, vector, text, value, params, filter);
        }

        SearchRequest withParams(Map<String, Object> value) {
            return new SearchRequest(annsField, vector, text, metricType,
                    value == null ? Map.of() : new LinkedHashMap<>(value), filter);
        }

        SearchRequest withFilter(String value) {
            return new SearchRequest(annsField, vector, text, metricType, params, value);
        }
    }

    /**
     * Mirrors Python's search result mode branches in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    private enum SearchMode {
        VECTOR,
        SPARSE,
        HYBRID
    }

    /**
     * Mirrors Python's default MilvusClient usage in
     * {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    private static final class DefaultMilvusClientFacade implements MilvusClientFacade {
        private final String databaseName;
        private final MilvusClientV2 delegate;

        private DefaultMilvusClientFacade(
                String databaseName,
                String milvusUri,
                String milvusToken,
                Map<String, Object> kwargs
        ) {
            this.databaseName = databaseName == null || databaseName.isBlank() ? "default" : databaseName;
            this.delegate = createClient(this.databaseName, milvusUri, milvusToken, kwargs);
        }

        private DefaultMilvusClientFacade(String databaseName, MilvusClientV2 delegate) {
            this.databaseName = databaseName == null || databaseName.isBlank() ? "default" : databaseName;
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean hasCollection(String collectionName) {
            return delegate.hasCollection(HasCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
        }

        @Override
        public void loadCollection(String collectionName) {
            delegate.loadCollection(LoadCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .sync(true)
                    .build());
        }

        @Override
        public void insert(String collectionName, List<Map<String, Object>> rows, int batchSize) {
            int safeBatchSize = Math.max(1, batchSize);
            for (int start = 0; start < rows.size(); start += safeBatchSize) {
                List<JsonObject> payload = rows.subList(start, Math.min(start + safeBatchSize, rows.size()))
                        .stream()
                        .map(row -> GSON.toJsonTree(row).getAsJsonObject())
                        .toList();
                delegate.insert(InsertReq.builder()
                        .databaseName(databaseName)
                        .collectionName(collectionName)
                        .data(payload)
                        .build());
            }
        }

        @Override
        public List<SearchHit> search(
                String collectionName,
                SearchRequest request,
                String metricType,
                int topK,
                List<String> outputFields,
                Map<String, Object> searchParams,
                String filter
        ) {
            SearchReq.SearchReqBuilder builder = SearchReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .annsField(request.annsField())
                    .topK(topK)
                    .limit(topK)
                    .outputFields(outputFields)
                    .searchParams(searchParams == null ? Map.of() : searchParams)
                    .metricType(IndexParam.MetricType.valueOf(metricType));
            if (request.vector() != null) {
                builder.data(List.of(new FloatVec(toFloatVector(request.vector()))));
            } else {
                builder.data(List.of(new EmbeddedText(request.text())));
            }
            if (filter != null && !filter.isBlank()) {
                builder.filter(filter);
            }
            return firstSearchHits(delegate.search(builder.build()));
        }

        @Override
        public List<SearchHit> hybridSearch(
                String collectionName,
                List<SearchRequest> requests,
                int topK,
                List<String> outputFields
        ) {
            List<AnnSearchReq> annRequests = new ArrayList<>(requests.size());
            for (SearchRequest request : requests) {
                AnnSearchReq.AnnSearchReqBuilder builder = AnnSearchReq.builder()
                        .vectorFieldName(request.annsField())
                        .topK(topK)
                        .limit(topK)
                        .metricType(IndexParam.MetricType.valueOf(request.metricType()))
                        .params(toJson(request.params()));
                if (request.vector() != null) {
                    builder.vectors(List.of(new FloatVec(toFloatVector(request.vector()))));
                } else {
                    builder.vectors(List.of(new EmbeddedText(request.text())));
                }
                if (request.filter() != null && !request.filter().isBlank()) {
                    builder.filter(request.filter());
                }
                annRequests.add(builder.build());
            }
            SearchResp response = delegate.hybridSearch(HybridSearchReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .searchRequests(annRequests)
                    .ranker(new RRFRanker(RRF_K))
                    .topK(topK)
                    .limit(topK)
                    .outFields(outputFields)
                    .build());
            return firstSearchHits(response);
        }

        @Override
        public long delete(String collectionName, List<String> ids, String filter) {
            DeleteReq.DeleteReqBuilder builder = DeleteReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName);
            if (ids != null && !ids.isEmpty()) {
                builder.ids(new ArrayList<>(ids));
            }
            if (filter != null && !filter.isBlank()) {
                builder.filter(filter);
            }
            DeleteResp response = delegate.delete(builder.build());
            return response.getDeleteCnt();
        }

        @Override
        public void flush(String collectionName) {
            delegate.flush(FlushReq.builder()
                    .databaseName(databaseName)
                    .collectionNames(List.of(collectionName))
                    .build());
        }

        @Override
        public void dropCollection(String collectionName) {
            delegate.dropCollection(DropCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
        }

        @Override
        public Map<String, Object> describeIndex(String collectionName, String fieldName) {
            DescribeIndexResp response = delegate.describeIndex(DescribeIndexReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .indexName(fieldName)
                    .build());
            DescribeIndexResp.IndexDesc index = response.getIndexDescByFieldName(fieldName);
            if (index == null && !response.getIndexDescriptions().isEmpty()) {
                index = response.getIndexDescriptions().get(0);
            }
            if (index == null) {
                return Map.of();
            }
            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("index_type", index.getIndexType() == null ? null : index.getIndexType().name());
            actual.put("metric_type", index.getMetricType() == null ? null : index.getMetricType().name());
            if (index.getExtraParams() != null) {
                actual.putAll(index.getExtraParams());
            }
            if (index.getProperties() != null) {
                actual.putAll(index.getProperties());
            }
            return actual;
        }

        @Override
        public CollectionDescription describeCollection(String collectionName) {
            DescribeCollectionResp response = delegate.describeCollection(DescribeCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
            List<FieldDescription> fields = new ArrayList<>();
            if (response.getCollectionSchema() != null) {
                for (CreateCollectionReq.FieldSchema field : response.getCollectionSchema().getFieldSchemaList()) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    if (field.getDimension() != null) {
                        params.put("dim", field.getDimension());
                    }
                    if (field.getMaxLength() != null) {
                        params.put("max_length", field.getMaxLength());
                    }
                    fields.add(new FieldDescription(field.getName(), field.getDataType(), params));
                }
            }
            return new CollectionDescription(fields);
        }

        @Override
        public void close() {
            delegate.close();
        }

        private List<SearchHit> firstSearchHits(SearchResp response) {
            List<List<SearchResp.SearchResult>> searchResults = response == null ? null : response.getSearchResults();
            if (searchResults == null || searchResults.isEmpty() || searchResults.get(0) == null) {
                return List.of();
            }
            List<SearchHit> hits = new ArrayList<>(searchResults.get(0).size());
            for (SearchResp.SearchResult result : searchResults.get(0)) {
                Map<String, Object> entity = result.getEntity() == null
                        ? Map.of()
                        : new LinkedHashMap<>(result.getEntity());
                hits.add(new SearchHit(
                        result.getId(),
                        result.getPrimaryKey(),
                        result.getScore() == null ? null : result.getScore().doubleValue(),
                        null,
                        entity
                ));
            }
            return hits;
        }
    }
}
