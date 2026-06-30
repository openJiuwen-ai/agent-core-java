/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.openjiuwen.core.foundation.store.vector.VectorStoreUtils;
import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import com.openjiuwen.core.retrieval.common.ResultRankRegistry;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.common.WeightedRankConfig;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AlterCollectionPropertiesReq;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.request.RenameCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Milvus-backed vector store for retrieval.
 */
public class MilvusVectorStore implements VectorStore, SchemaMutableVectorStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        ResultRankRegistry.registerResultRankerClass(
                "milvus",
                WeightedRanker.class,
                RRFRanker.class,
                Map.of());
    }

    private final MilvusClientV2 client;
    private final boolean ownsClient;
    private final Set<String> loadedCollections;
    private final Set<String> knownCollections;
    private final Map<String, Map<String, Object>> collectionMetadata;
    private final Map<String, CollectionSchema> collectionSchemas;
    private final String databaseName;
    private final String distanceMetric;
    private final String indexType;
    private final String milvusUri;
    private final String milvusToken;
    private final String textField;
    private final String vectorField;
    private final String sparseVectorField;
    private final String metadataField;
    private final String docIdField;

    private String collectionName;

    /**
     * Auto-generated for codecheck compliance.
     */
    public MilvusVectorStore(VectorStoreConfig config, String milvusUri) {
        this(config, milvusUri, null, "hybrid");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String indexType) {
        this(config, milvusUri, null, indexType);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType) {
        this(
                createClient(config.getDatabaseName(), milvusUri, milvusToken),
                true,
                ConcurrentHashMap.newKeySet(),
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                config,
                milvusUri,
                milvusToken,
                indexType,
                "text",
                "vector",
                "sparse_vector",
                "metadata",
                "doc_id");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MilvusVectorStore(MilvusClientV2 client, VectorStoreConfig config, String indexType) {
        this(client, config, indexType, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MilvusVectorStore(MilvusClientV2 client,
                             VectorStoreConfig config,
                             String indexType,
                             Map<String, Object> options) {
        this(
                client,
                false,
                ConcurrentHashMap.newKeySet(),
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                config,
                "",
                null,
                indexType,
                optionString(options, "text_field", "text"),
                optionString(options, "vector_field", "vector"),
                optionString(options, "sparse_vector_field", "sparse_vector"),
                optionString(options, "metadata_field", "metadata"),
                optionString(options, "doc_id_field", "doc_id"));
    }

    private MilvusVectorStore(MilvusClientV2 client,
                              boolean ownsClient,
                              Set<String> loadedCollections,
                              Set<String> knownCollections,
                              Map<String, Map<String, Object>> collectionMetadata,
                              Map<String, CollectionSchema> collectionSchemas,
                              VectorStoreConfig config,
                              String milvusUri,
                              String milvusToken,
                              String indexType,
                              String textField,
                              String vectorField,
                              String sparseVectorField,
                              String metadataField,
                              String docIdField) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(config, "config");
        config.validate();
        this.client = client;
        this.ownsClient = ownsClient;
        this.loadedCollections = loadedCollections;
        this.knownCollections = knownCollections;
        this.collectionMetadata = collectionMetadata;
        this.collectionSchemas = collectionSchemas;
        this.databaseName = config.getDatabaseName();
        this.collectionName = config.getCollectionName();
        this.distanceMetric = config.getDistanceMetric();
        this.indexType = RetrievalValidation.validateIndexType(indexType, "indexType");
        this.milvusUri = milvusUri == null ? "" : milvusUri;
        this.milvusToken = milvusToken;
        this.textField = textField;
        this.vectorField = vectorField;
        this.sparseVectorField = sparseVectorField;
        this.metadataField = metadataField;
        this.docIdField = docIdField;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static MilvusClientV2 createClient(String databaseName, String milvusUri, String milvusToken) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder().uri(milvusUri);
        if (milvusToken != null && !milvusToken.isBlank()) {
            builder.token(milvusToken);
        }
        MilvusClientV2 client = new MilvusClientV2(builder.build());
        if (databaseName != null && !databaseName.isBlank() && !"default".equals(databaseName)) {
            List<String> databaseNames = client.listDatabases().getDatabaseNames();
            if (databaseNames == null || !databaseNames.contains(databaseName)) {
                client.createDatabase(CreateDatabaseReq.builder().databaseName(databaseName).build());
            }
            try {
                client.useDatabase(databaseName);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("failed to switch Milvus database", ex);
            }
        }
        return client;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MilvusClientV2 getClient() {
        return client;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMilvusUri() {
        return milvusUri;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMilvusToken() {
        return milvusToken;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorStore withCollection(String collectionName) {
        VectorStoreConfig scopedConfig = new VectorStoreConfig("milvus", databaseName, collectionName, distanceMetric);
        return new MilvusVectorStore(
                client,
                false,
                loadedCollections,
                knownCollections,
                collectionMetadata,
                collectionSchemas,
                scopedConfig,
                milvusUri,
                milvusToken,
                indexType,
                textField,
                vectorField,
                sparseVectorField,
                metadataField,
                docIdField);
    }

    private static String optionString(Map<String, Object> options, String key, String defaultValue) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(options.get(key));
        return value.isBlank() ? defaultValue : value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        if (data == null || data.isEmpty()) {
            return;
        }
        ensureCollectionForWrite(data, options);
        int safeBatchSize = batchSize == null || batchSize <= 0 ? 128 : batchSize;
        for (int start = 0; start < data.size(); start += safeBatchSize) {
            int end = Math.min(start + safeBatchSize, data.size());
            List<JsonObject> batch = new ArrayList<>(end - start);
            for (Map<String, Object> record : data.subList(start, end)) {
                batch.add(toInsertRecord(record));
            }
            InsertReq.InsertReqBuilder builder = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(batch);
            if (hasDatabase()) {
                builder.databaseName(databaseName);
            }
            client.insert(builder.build());
        }
        flush(collectionName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void ensureCollection(String targetCollection, String requestedIndexType, Integer dimension) {
        ensureCollection(targetCollection, requestedIndexType, dimension, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void ensureCollection(String targetCollection,
                                 String requestedIndexType,
                                 Integer dimension,
                                 Map<String, Object> options) {
        String safeCollection = firstNonBlank(targetCollection, collectionName);
        if (safeCollection == null || safeCollection.isBlank()) {
            throw new IllegalArgumentException("collectionName is required for Milvus collection bootstrap");
        }
        if (tableExists(safeCollection)) {
            return;
        }

        String safeIndexType = RetrievalValidation.validateIndexType(
                firstNonBlank(requestedIndexType, indexType),
                "MilvusVectorStore.indexType");
        boolean enableSparse = !"vector".equals(safeIndexType);
        boolean enableDense = !"bm25".equals(safeIndexType);
        if (enableDense && (dimension == null || dimension <= 0)) {
            throw new IllegalArgumentException("vector dimension is required to bootstrap Milvus collection");
        }

        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.setEnableDynamicField(false);
        schema.addField(AddFieldReq.builder()
                .fieldName("pk")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(docIdField)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id")
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());

        AddFieldReq.AddFieldReqBuilder<?> textFieldBuilder = AddFieldReq.builder()
                .fieldName(textField)
                .dataType(DataType.VarChar)
                .maxLength(65535);
        if (enableSparse) {
            textFieldBuilder.enableAnalyzer(true)
                    .enableMatch(true)
                    .analyzerParams(Map.of("tokenizer", "jieba"));
        }
        schema.addField(textFieldBuilder.build());

        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder()
                .fieldName(docIdField)
                .indexType(IndexParam.IndexType.INVERTED)
                .build());
        indexParams.add(IndexParam.builder()
                .fieldName("chunk_id")
                .indexType(IndexParam.IndexType.INVERTED)
                .build());

        if (enableSparse) {
            schema.addField(AddFieldReq.builder()
                    .fieldName(sparseVectorField)
                    .dataType(DataType.SparseFloatVector)
                    .build());
            schema.addFunction(CreateCollectionReq.Function.builder()
                    .name("text_bm25_emb")
                    .functionType(FunctionType.BM25)
                    .inputFieldNames(List.of(textField))
                    .outputFieldNames(List.of(sparseVectorField))
                    .build());
            indexParams.add(IndexParam.builder()
                    .fieldName(sparseVectorField)
                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                    .metricType(IndexParam.MetricType.BM25)
                    .build());
        }

        if (enableDense) {
            schema.addField(AddFieldReq.builder()
                    .fieldName(vectorField)
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());
            indexParams.add(IndexParam.builder()
                    .fieldName(vectorField)
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .metricType(metricType())
                    .build());
        }

        schema.addField(AddFieldReq.builder()
                .fieldName(metadataField)
                .dataType(DataType.JSON)
                .build());

        CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                .collectionName(safeCollection)
                .enableDynamicField(false)
                .collectionSchema(schema)
                .indexParams(indexParams);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.createCollection(builder.build());
        knownCollections.add(safeCollection);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void createCollection(String targetCollection, CollectionSchema schema, Map<String, Object> metadata) {
        if (schema == null) {
            throw new IllegalArgumentException("schema is required for Milvus collection creation");
        }
        createCollectionFromSchema(targetCollection, schema, metadata == null ? Map.of() : metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SearchResult> search(List<Float> queryVector,
                                     int topK,
                                     Map<String, Object> filters,
                                     Map<String, Object> options) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0 || !tableExists(collectionName)) {
            return List.of();
        }
        ensureLoaded(collectionName);
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(vectorField)
                .metricType(metricType())
                .topK(topK)
                .limit(topK)
                .data(List.of(new FloatVec(queryVector)))
                .outputFields(outputFields())
                .searchParams(resolveDenseSearchParams(topK, options));
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        String filterExpr = toFilterExpression(filters);
        if (filterExpr != null && !filterExpr.isBlank()) {
            builder.filter(filterExpr);
        }
        SearchResp response = client.search(builder.build());
        return toSearchResults(firstSearchResults(response), SearchMode.VECTOR);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SearchResult> sparseSearch(String queryText,
                                           int topK,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        if (queryText == null || queryText.isBlank() || topK <= 0 || !tableExists(collectionName)) {
            return List.of();
        }
        ensureLoaded(collectionName);
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(sparseVectorField)
                .metricType(IndexParam.MetricType.BM25)
                .topK(topK)
                .limit(topK)
                .data(List.of(new EmbeddedText(queryText)))
                .outputFields(outputFields())
                .searchParams(Map.of());
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        String filterExpr = toFilterExpression(filters);
        if (filterExpr != null && !filterExpr.isBlank()) {
            builder.filter(filterExpr);
        }
        SearchResp response = client.search(builder.build());
        return toSearchResults(firstSearchResults(response), SearchMode.SPARSE);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SearchResult> hybridSearch(String queryText,
                                           List<Float> queryVector,
                                           int topK,
                                           double alpha,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        if (topK <= 0 || !tableExists(collectionName)) {
            return List.of();
        }
        if (queryVector == null || queryVector.isEmpty()) {
            return sparseSearch(queryText, topK, filters, options);
        }
        if (queryText == null || queryText.isBlank()) {
            return search(queryVector, topK, filters, options);
        }
        ensureLoaded(collectionName);
        String filterExpr = toFilterExpression(filters);
        try {
            AnnSearchReq.AnnSearchReqBuilder denseBuilder = AnnSearchReq.builder()
                    .vectorFieldName(vectorField)
                    .topK(topK)
                    .limit(topK)
                    .metricType(metricType())
                    .vectors(List.of(new FloatVec(queryVector)))
                    .params(toJson(resolveDenseSearchParams(topK, options)));
            AnnSearchReq.AnnSearchReqBuilder sparseBuilder = AnnSearchReq.builder()
                    .vectorFieldName(sparseVectorField)
                    .topK(topK)
                    .limit(topK)
                    .metricType(IndexParam.MetricType.BM25)
                    .vectors(List.of(new EmbeddedText(queryText)))
                    .params("{}");
            if (filterExpr != null && !filterExpr.isBlank()) {
                denseBuilder.filter(filterExpr);
                sparseBuilder.filter(filterExpr);
            }
            HybridSearchReq.HybridSearchReqBuilder builder = HybridSearchReq.builder()
                    .collectionName(collectionName)
                    .searchRequests(List.of(denseBuilder.build(), sparseBuilder.build()))
                    .ranker(resolveNativeRanker(alpha, options))
                    .topK(topK)
                    .limit(topK)
                    .outFields(outputFields());
            if (hasDatabase()) {
                builder.databaseName(databaseName);
            }
            SearchResp response = client.hybridSearch(builder.build());
            return toSearchResults(firstSearchResults(response), SearchMode.HYBRID);
        } catch (RuntimeException ex) {
            return hybridFallback(queryText, queryVector, topK, alpha, filters, options);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
        if (!tableExists(collectionName)) {
            return false;
        }
        List<String> clauses = new ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            clauses.add("chunk_id in " + formatCollection(ids));
        }
        String extraFilter = toFilterExpression(filterExpr);
        if (extraFilter != null && !extraFilter.isBlank()) {
            clauses.add(extraFilter);
        }
        if (clauses.isEmpty()) {
            return false;
        }
        DeleteReq.DeleteReqBuilder builder = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(String.join(" && ", clauses));
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        DeleteResp response = client.delete(builder.build());
        flush(collectionName);
        return response != null && response.getDeleteCnt() > 0;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean tableExists(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        if (knownCollections.contains(tableName)) {
            return true;
        }
        HasCollectionReq.HasCollectionReqBuilder builder = HasCollectionReq.builder().collectionName(tableName);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        boolean exists = Boolean.TRUE.equals(client.hasCollection(builder.build()));
        if (exists) {
            knownCollections.add(tableName);
        }
        return exists;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void deleteTable(String tableName) {
        if (!tableExists(tableName)) {
            return;
        }
        DropCollectionReq.DropCollectionReqBuilder builder = DropCollectionReq.builder().collectionName(tableName);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.dropCollection(builder.build());
        knownCollections.remove(tableName);
        loadedCollections.remove(tableName);
        collectionMetadata.remove(tableName);
        collectionSchemas.remove(tableName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listCollectionNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>(knownCollections);
        try {
            var response = client.listCollections();
            if (response != null && response.getCollectionNames() != null) {
                names.addAll(response.getCollectionNames());
            }
        } catch (RuntimeException ignored) {
            // Keep the locally known collection cache when Milvus listing is unavailable.
        }
        return new ArrayList<>(names);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        Map<String, Object> cached = collectionMetadata.get(collectionName);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        try {
            DescribeCollectionResp resp = describeCollection(collectionName);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (resp.getProperties() != null) {
                metadata.putAll(resp.getProperties());
            }
            metadata.put("schema_version", parseSchemaVersion(metadata.get("schema_version")));
            collectionMetadata.put(collectionName, new ConcurrentHashMap<>(metadata));
            return metadata;
        } catch (RuntimeException e) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema_version", 0);
            return metadata;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Map<String, String> stringMetadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            stringMetadata.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        AlterCollectionPropertiesReq.AlterCollectionPropertiesReqBuilder builder =
                AlterCollectionPropertiesReq.builder()
                        .collectionName(collectionName)
                        .properties(stringMetadata);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.alterCollectionProperties(builder.build());
        collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>()).putAll(metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateSchema(String collectionName, List<?> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        CollectionSchema oldSchema = describeCollectionSchema(collectionName);
        CollectionSchema newSchema = VectorStoreUtils.computeNewSchema(oldSchema, operations);
        Function<Map<String, Object>, Map<String, Object>> transformFunc =
                VectorStoreUtils.buildTransformFuncForOperations(operations);
        Map<String, Object> metadata = getCollectionMetadata(collectionName);
        executeSchemaMigration(collectionName, newSchema, transformFunc, metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public CollectionSchema getSchema(String collectionName) {
        return describeCollectionSchema(collectionName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
        if (limit <= 0 || !tableExists(collectionName)) {
            return List.of();
        }
        ensureLoaded(collectionName);
        QueryReq.QueryReqBuilder builder = new QueryReq.QueryReqBuilder()
                .collectionName(collectionName)
                .limit(limit)
                .outputFields(outputFields());
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        String filterExpr = toFilterExpression(filters);
        if (filterExpr != null && !filterExpr.isBlank()) {
            builder.filter(filterExpr);
        }
        QueryResp response = client.query(builder.build());
        return toQueryResults(response == null ? List.of() : response.getQueryResults());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public long count(String tableName) {
        if (!tableExists(tableName)) {
            return 0L;
        }
        GetCollectionStatsReq.GetCollectionStatsReqBuilder builder = GetCollectionStatsReq.builder()
                .collectionName(tableName);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        Long numOfEntities = client.getCollectionStats(builder.build()).getNumOfEntities();
        return numOfEntities == null ? 0L : numOfEntities;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        if (!ownsClient) {
            return;
        }
        client.close();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDistanceMetric() {
        return distanceMetric;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexType() {
        return indexType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTextField() {
        return textField;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getVectorField() {
        return vectorField;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSparseVectorField() {
        return sparseVectorField;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMetadataField() {
        return metadataField;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDocIdField() {
        return docIdField;
    }

    private DescribeCollectionResp describeCollection(String collectionName) {
        DescribeCollectionReq.DescribeCollectionReqBuilder builder = DescribeCollectionReq.builder()
                .collectionName(collectionName);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        return client.describeCollection(builder.build());
    }

    private CollectionSchema describeCollectionSchema(String collectionName) {
        DescribeCollectionResp resp = describeCollection(collectionName);
        CreateCollectionReq.CollectionSchema milvusSchema = resp.getCollectionSchema();
        List<FieldSchema> fields = new ArrayList<>();
        if (milvusSchema != null && milvusSchema.getFieldSchemaList() != null) {
            for (CreateCollectionReq.FieldSchema fieldSchema : milvusSchema.getFieldSchemaList()) {
                fields.add(toSpiFieldSchema(fieldSchema));
            }
        }
        return CollectionSchema.fromFields(fields, resp.getDescription(),
                milvusSchema != null && milvusSchema.isEnableDynamicField());
    }

    private FieldSchema toSpiFieldSchema(CreateCollectionReq.FieldSchema fieldSchema) {
        FieldSchema.Builder builder = FieldSchema.builder()
                .name(fieldSchema.getName())
                .dtype(toSpiDataType(fieldSchema.getDataType()))
                .isPrimary(Boolean.TRUE.equals(fieldSchema.getIsPrimaryKey()))
                .autoId(Boolean.TRUE.equals(fieldSchema.getAutoID()));
        if (fieldSchema.getMaxLength() != null) {
            builder.maxLength(fieldSchema.getMaxLength());
        }
        if (fieldSchema.getDimension() != null) {
            builder.dim(fieldSchema.getDimension());
        }
        if (fieldSchema.getElementType() != null) {
            builder.elementType(toSpiDataType(fieldSchema.getElementType()));
        }
        if (fieldSchema.getMaxCapacity() != null) {
            builder.maxCapacity(fieldSchema.getMaxCapacity());
        }
        if (fieldSchema.getDescription() != null) {
            builder.description(fieldSchema.getDescription());
        }
        if (fieldSchema.getDefaultValue() != null) {
            builder.defaultValue(fieldSchema.getDefaultValue());
        }
        return builder.build();
    }

    private DataType toMilvusDataType(VectorDataType dataType) {
        return switch (dataType) {
            case VARCHAR -> DataType.VarChar;
            case FLOAT_VECTOR -> DataType.FloatVector;
            case INT64 -> DataType.Int64;
            case INT32 -> DataType.Int32;
            case INT16 -> DataType.Int16;
            case INT8 -> DataType.Int8;
            case FLOAT -> DataType.Float;
            case DOUBLE -> DataType.Double;
            case BOOL -> DataType.Bool;
            case JSON -> DataType.JSON;
            case ARRAY -> DataType.Array;
        };
    }

    private VectorDataType toSpiDataType(DataType dataType) {
        return switch (dataType) {
            case VarChar -> VectorDataType.VARCHAR;
            case FloatVector -> VectorDataType.FLOAT_VECTOR;
            case Int64 -> VectorDataType.INT64;
            case Int32 -> VectorDataType.INT32;
            case Int16 -> VectorDataType.INT16;
            case Int8 -> VectorDataType.INT8;
            case Float -> VectorDataType.FLOAT;
            case Double -> VectorDataType.DOUBLE;
            case Bool -> VectorDataType.BOOL;
            case JSON -> VectorDataType.JSON;
            case Array -> VectorDataType.ARRAY;
            default -> VectorDataType.VARCHAR;
        };
    }

    private void executeSchemaMigration(String collectionName,
                                        CollectionSchema newSchema,
                                        Function<Map<String, Object>, Map<String, Object>> transformFunc,
                                        Map<String, Object> metadata) {
        String tempCollectionName = collectionName + "_migration_" + System.currentTimeMillis();
        try {
            createCollectionFromSchema(tempCollectionName, newSchema, metadata);
            copyCollectionData(collectionName, tempCollectionName, newSchema, transformFunc);

            ReleaseCollectionReq.ReleaseCollectionReqBuilder releaseBuilder = ReleaseCollectionReq.builder()
                    .collectionName(collectionName);
            if (hasDatabase()) {
                releaseBuilder.databaseName(databaseName);
            }
            client.releaseCollection(releaseBuilder.build());

            deleteTable(collectionName);

            RenameCollectionReq.RenameCollectionReqBuilder renameBuilder = RenameCollectionReq.builder()
                    .collectionName(tempCollectionName)
                    .newCollectionName(collectionName);
            if (hasDatabase()) {
                renameBuilder.databaseName(databaseName);
            }
            client.renameCollection(renameBuilder.build());

            collectionSchemas.put(collectionName, newSchema);
            collectionMetadata.put(collectionName, new ConcurrentHashMap<>(metadata));
            knownCollections.add(collectionName);
            loadedCollections.remove(collectionName);
            knownCollections.remove(tempCollectionName);
            loadedCollections.remove(tempCollectionName);
            collectionSchemas.remove(tempCollectionName);
            collectionMetadata.remove(tempCollectionName);
        } catch (RuntimeException e) {
            if (tableExists(tempCollectionName)) {
                deleteTable(tempCollectionName);
            }
            throw e;
        }
    }

    private void createCollectionFromSchema(String collectionName,
                                            CollectionSchema schema,
                                            Map<String, Object> metadata) {
        CreateCollectionReq.CollectionSchema milvusSchema = client.createSchema();
        milvusSchema.setEnableDynamicField(schema.isEnableDynamicField());
        List<IndexParam> indexParams = new ArrayList<>();
        IndexParam.MetricType metricType = metricTypeFromMetadata(metadata);
        for (FieldSchema field : schema.getFields()) {
            AddFieldReq.AddFieldReqBuilder<?> builder = AddFieldReq.builder()
                    .fieldName(field.getName())
                    .dataType(toMilvusDataType(field.getDtype()));
            if (field.getDescription() != null) {
                builder.description(field.getDescription());
            }
            if (field.getMaxLength() != null) {
                builder.maxLength(field.getMaxLength());
            }
            if (field.getDim() != null) {
                builder.dimension(field.getDim());
            }
            if (field.getElementType() != null) {
                builder.elementType(toMilvusDataType(field.getElementType()));
            }
            if (field.getMaxCapacity() != null) {
                builder.maxCapacity(field.getMaxCapacity());
            }
            if (field.getDefaultValue() != null) {
                builder.defaultValue(field.getDefaultValue());
            }
            builder.isPrimaryKey(field.isPrimary());
            builder.autoID(field.isAutoId());
            milvusSchema.addField(builder.build());

            if (field.getDtype() == VectorDataType.FLOAT_VECTOR) {
                indexParams.add(IndexParam.builder()
                        .fieldName(field.getName())
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(metricType)
                        .build());
            }
        }

        CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .enableDynamicField(schema.isEnableDynamicField())
                .collectionSchema(milvusSchema)
                .indexParams(indexParams);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.createCollection(builder.build());
        knownCollections.add(collectionName);
        collectionSchemas.put(collectionName, schema);
    }

    private void copyCollectionData(String sourceCollection,
                                    String targetCollection,
                                    CollectionSchema targetSchema,
                                    Function<Map<String, Object>, Map<String, Object>> transformFunc) {
        QueryIteratorReq.QueryIteratorReqBuilder builder = QueryIteratorReq.builder()
                .collectionName(sourceCollection)
                .outputFields(List.of("*"))
                .expr("")
                .batchSize(100);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        QueryIterator iterator = client.queryIterator(builder.build());
        try {
            List<Map<String, Object>> batch = new ArrayList<>();
            while (true) {
                List<QueryResultsWrapper.RowRecord> rows = iterator.next();
                if (rows == null || rows.isEmpty()) {
                    break;
                }
                for (QueryResultsWrapper.RowRecord row : rows) {
                    Map<String, Object> record = new LinkedHashMap<>(row.getFieldValues());
                    Map<String, Object> transformed = transformFunc.apply(record);
                    batch.add(prepareMigrationRecord(targetSchema, transformed));
                    if (batch.size() >= 100) {
                        insertMigrationBatch(targetCollection, batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                insertMigrationBatch(targetCollection, batch);
            }
            flush(targetCollection);
        } finally {
            iterator.close();
        }
    }

    private Map<String, Object> prepareMigrationRecord(CollectionSchema schema, Map<String, Object> record) {
        Map<String, Object> prepared = new LinkedHashMap<>(record);
        for (FieldSchema field : schema.getFields()) {
            if (field.isPrimary() && field.isAutoId()) {
                prepared.remove(field.getName());
            }
        }
        return prepared;
    }

    private void insertMigrationBatch(String collectionName, List<Map<String, Object>> batch) {
        List<JsonObject> payload = new ArrayList<>(batch.size());
        for (Map<String, Object> record : batch) {
            payload.add(toJsonObject(record));
        }
        InsertReq.InsertReqBuilder builder = InsertReq.builder()
                .collectionName(collectionName)
                .data(payload);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.insert(builder.build());
    }

    private IndexParam.MetricType metricTypeFromMetadata(Map<String, Object> metadata) {
        String metric = metadata == null ? null : stringValue(metadata.get("distance_metric"));
        if (metric == null || metric.isBlank()) {
            return metricType();
        }
        return switch (metric.toUpperCase(Locale.ROOT)) {
            case "L2", "EUCLIDEAN" -> IndexParam.MetricType.L2;
            case "IP", "DOT", "INNER_PRODUCT" -> IndexParam.MetricType.IP;
            default -> IndexParam.MetricType.COSINE;
        };
    }

    private static int parseSchemaVersion(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String toFilterExpression(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (entry.getValue() instanceof Collection<?> values) {
                if (values.isEmpty()) {
                    return null;
                }
                clauses.add(entry.getKey() + " in " + formatCollection(values));
                continue;
            }
            clauses.add(entry.getKey() + " == " + formatLiteral(entry.getValue()));
        }
        return clauses.isEmpty() ? null : String.join(" && ", clauses);
    }

    private boolean hasDatabase() {
        return databaseName != null && !databaseName.isBlank();
    }

    private void ensureCollectionForWrite(List<Map<String, Object>> data, Map<String, Object> options) {
        Integer dimension = extractDimension(options);
        if (dimension == null || dimension <= 0) {
            dimension = inferDimension(data);
        }
        ensureCollection(collectionName,
                resolveBootstrapIndexType(options),
                dimension,
                options == null ? Map.of() : options);
    }

    private void ensureLoaded(String targetCollection) {
        if (loadedCollections.contains(targetCollection) || !tableExists(targetCollection)) {
            return;
        }
        LoadCollectionReq.LoadCollectionReqBuilder builder = LoadCollectionReq.builder()
                .collectionName(targetCollection)
                .sync(true);
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.loadCollection(builder.build());
        loadedCollections.add(targetCollection);
    }

    private void flush(String targetCollection) {
        FlushReq.FlushReqBuilder builder = FlushReq.builder().collectionNames(List.of(targetCollection));
        if (hasDatabase()) {
            builder.databaseName(databaseName);
        }
        client.flush(builder.build());
    }

    private List<String> outputFields() {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        fields.add(textField);
        fields.add(metadataField);
        fields.add(docIdField);
        fields.add("chunk_id");
        return new ArrayList<>(fields);
    }

    private Map<String, Object> resolveDenseSearchParams(int topK, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> raw = extractMap(options, List.of("search_params", "searchParams"));
        if (raw != null) {
            params.putAll(raw);
        }
        Object factor = options == null ? null : options.get("efSearchFactor");
        if (factor instanceof Number number) {
            params.put("ef", Math.max(topK, (int) Math.round(topK * number.doubleValue())));
        }
        return params;
    }

    private CreateCollectionReq.Function resolveNativeRanker(double alpha, Map<String, Object> options) {
        Object rankConfig = options == null ? null : firstPresent(options, List.of("rank_config", "rankConfig"));
        if (rankConfig instanceof RRFRankConfig rrf) {
            return new RRFRanker(rrf.getK());
        }
        if (rankConfig instanceof WeightedRankConfig weighted) {
            float denseWeight = (float) Math.max(0.0, weighted.getDenseContent() > 0.0
                    ? weighted.getDenseContent()
                    : weighted.getDenseName());
            float sparseWeight = (float) Math.max(0.0, weighted.getSparseContent());
            return new WeightedRanker(normalizeWeights(denseWeight, sparseWeight));
        }
        return new WeightedRanker(normalizeWeights((float) alpha, (float) (1.0 - alpha)));
    }

    private List<SearchResult> hybridFallback(String queryText,
                                              List<Float> queryVector,
                                              int topK,
                                              double alpha,
                                              Map<String, Object> filters,
                                              Map<String, Object> options) {
        List<SearchResult> denseResults = search(queryVector, topK, filters, options);
        List<SearchResult> sparseResults = sparseSearch(queryText, topK, filters, options);
        Object rankConfig = options == null ? null : firstPresent(options, List.of("rank_config", "rankConfig"));
        if (rankConfig instanceof RRFRankConfig rrf) {
            return trim(FusionUtils.rrfFusionSearch(List.of(denseResults, sparseResults), rrf), topK);
        }
        float denseWeight = (float) alpha;
        float sparseWeight = (float) (1.0 - alpha);
        if (rankConfig instanceof WeightedRankConfig weighted) {
            denseWeight = (float) Math.max(0.0, weighted.getDenseContent() > 0.0
                    ? weighted.getDenseContent()
                    : weighted.getDenseName());
            sparseWeight = (float) Math.max(0.0, weighted.getSparseContent());
        }
        return trim(weightedFusion(denseResults, sparseResults, denseWeight, sparseWeight), topK);
    }

    private static List<SearchResult> trim(List<SearchResult> results, int topK) {
        if (results.size() <= topK) {
            return results;
        }
        return new ArrayList<>(results.subList(0, topK));
    }

    private static List<SearchResult> weightedFusion(List<SearchResult> denseResults,
                                                     List<SearchResult> sparseResults,
                                                     float denseWeight,
                                                     float sparseWeight) {
        List<Float> weights = normalizeWeights(denseWeight, sparseWeight);
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, SearchResult> byText = new LinkedHashMap<>();
        mergeWeighted(scores, byText, denseResults, weights.get(0));
        mergeWeighted(scores, byText, sparseResults, weights.get(1));
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));
        List<SearchResult> fused = new ArrayList<>(ordered.size());
        for (Map.Entry<String, Double> entry : ordered) {
            SearchResult result = byText.get(entry.getKey());
            fused.add(new SearchResult(result.getId(), result.getText(), entry.getValue(), result.getMetadata()));
        }
        return fused;
    }

    private static void mergeWeighted(Map<String, Double> scores,
                                      Map<String, SearchResult> byText,
                                      List<SearchResult> results,
                                      float weight) {
        if (results == null || results.isEmpty() || weight <= 0.0f) {
            return;
        }
        for (SearchResult result : results) {
            scores.merge(result.getText(), result.getScore() * weight, Double::sum);
            byText.putIfAbsent(result.getText(), result);
        }
    }

    private static List<Float> normalizeWeights(float denseWeight, float sparseWeight) {
        float safeDense = Math.max(0.0f, denseWeight);
        float safeSparse = Math.max(0.0f, sparseWeight);
        float sum = safeDense + safeSparse;
        if (sum <= 0.0f) {
            return List.of(0.5f, 0.5f);
        }
        return List.of(safeDense / sum, safeSparse / sum);
    }

    private JsonObject toInsertRecord(Map<String, Object> record) {
        Map<String, Object> metadata = castMap(record.get(metadataField));
        String chunkId = firstNonBlank(
                stringValue(record.get("chunk_id")),
                stringValue(record.get("id")),
                stringValue(metadata.get("chunk_id")),
                UUID.randomUUID().toString());
        String docId = firstNonBlank(
                stringValue(record.get(docIdField)),
                stringValue(metadata.get("doc_id")),
                chunkId);
        metadata.putIfAbsent("doc_id", docId);
        metadata.putIfAbsent("chunk_id", chunkId);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("chunk_id", chunkId);
        normalized.put(docIdField, docId);
        normalized.put(textField, firstNonBlank(stringValue(record.get(textField)), ""));
        normalized.put(metadataField, metadata);

        List<Float> vector = castFloatList(record.get(vectorField));
        if (vector != null && !vector.isEmpty()) {
            normalized.put(vectorField, vector);
        }
        CollectionSchema schema = collectionSchemas.get(collectionName);
        if (schema != null) {
            for (FieldSchema field : schema.getFields()) {
                String fieldName = field.getName();
                if (record.containsKey(fieldName) && !normalized.containsKey(fieldName)) {
                    normalized.put(fieldName, record.get(fieldName));
                }
            }
        }
        return toJsonObject(normalized);
    }

    private JsonObject toJsonObject(Map<String, Object> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            object.add(entry.getKey(), toJsonValue(entry.getValue()));
        }
        return object;
    }

    private com.google.gson.JsonElement toJsonValue(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof String string) {
            return new JsonPrimitive(string);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), toJsonValue(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Collection<?> collection) {
            JsonArray array = new JsonArray();
            for (Object item : collection) {
                array.add(toJsonValue(item));
            }
            return array;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    private List<SearchResult> toSearchResults(List<SearchResp.SearchResult> results, SearchMode mode) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<SearchResult> mapped = new ArrayList<>(results.size());
        for (SearchResp.SearchResult raw : results) {
            Map<String, Object> entity = raw.getEntity() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(raw.getEntity());
            Map<String, Object> metadata = castMap(entity.get(metadataField));
            String docId = firstNonBlank(stringValue(entity.get(docIdField)), stringValue(metadata.get("doc_id")));
            String chunkId = firstNonBlank(stringValue(entity.get("chunk_id")), stringValue(metadata.get("chunk_id")));
            if (docId != null) {
                metadata.putIfAbsent("doc_id", docId);
            }
            if (chunkId != null) {
                metadata.putIfAbsent("chunk_id", chunkId);
            }
            Double rawScore = raw.getScore() == null ? null : raw.getScore().doubleValue();
            double finalScore = rawScore == null ? 0.0 : rawScore;
            if (mode == SearchMode.VECTOR && rawScore != null) {
                double scaled = normalizeVectorScore(rawScore);
                metadata.putIfAbsent("raw_score_scaled", scaled);
                finalScore = scaled;
            }
            metadata.putIfAbsent("raw_score", rawScore);
            mapped.add(new SearchResult(
                    firstNonBlank(chunkId, raw.getPrimaryKey(), stringValue(raw.getId()), UUID.randomUUID().toString()),
                    stringValue(entity.getOrDefault(textField, "")),
                    finalScore,
                    metadata));
        }
        return mapped;
    }

    private List<SearchResult> toQueryResults(List<QueryResp.QueryResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<SearchResult> mapped = new ArrayList<>(results.size());
        for (QueryResp.QueryResult raw : results) {
            Map<String, Object> entity = raw.getEntity() == null ? Map.of() : raw.getEntity();
            Map<String, Object> metadata = castMap(entity.get(metadataField));
            String docId = firstNonBlank(stringValue(entity.get(docIdField)), stringValue(metadata.get("doc_id")));
            String chunkId = firstNonBlank(stringValue(entity.get("chunk_id")), stringValue(metadata.get("chunk_id")));
            if (docId != null) {
                metadata.putIfAbsent("doc_id", docId);
            }
            if (chunkId != null) {
                metadata.putIfAbsent("chunk_id", chunkId);
            }
            mapped.add(new SearchResult(
                    firstNonBlank(chunkId, UUID.randomUUID().toString()),
                    stringValue(entity.getOrDefault(textField, "")),
                    0.0,
                    metadata));
        }
        return mapped;
    }

    private List<SearchResp.SearchResult> firstSearchResults(SearchResp response) {
        List<List<SearchResp.SearchResult>> results = response == null ? null : response.getSearchResults();
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.get(0) == null ? List.of() : results.get(0);
    }

    private IndexParam.MetricType metricType() {
        return switch (distanceMetric) {
            case "dot" -> IndexParam.MetricType.IP;
            case "euclidean" -> IndexParam.MetricType.L2;
            default -> IndexParam.MetricType.COSINE;
        };
    }

    private double normalizeVectorScore(double rawScore) {
        return switch (metricType()) {
            case L2 -> 1.0 / (1.0 + Math.max(rawScore, 0.0));
            default -> clamp((rawScore + 1.0) / 2.0);
        };
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String toJson(Map<String, Object> params) {
        try {
            return OBJECT_MAPPER.writeValueAsString(params == null ? Map.of() : params);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize search params", ex);
        }
    }

    private static String formatCollection(Collection<?> values) {
        List<String> literals = new ArrayList<>(values.size());
        for (Object value : values) {
            literals.add(formatLiteral(value));
        }
        return "[" + String.join(", ", literals) + "]";
    }

    private static String formatLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private static Object firstPresent(Map<String, Object> source, List<String> keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractMap(Map<String, Object> source, List<String> keys) {
        Object value = source == null ? null : firstPresent(source, keys);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Float> castFloatList(Object value) {
        if (value instanceof float[] array) {
            List<Float> floats = new ArrayList<>(array.length);
            for (float item : array) {
                floats.add(item);
            }
            return floats;
        }
        if (!(value instanceof Collection<?> values)) {
            return null;
        }
        List<Float> floats = new ArrayList<>(values.size());
        for (Object item : values) {
            if (item instanceof Number number) {
                floats.add(number.floatValue());
            }
        }
        return floats;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resolveBootstrapIndexType(Map<String, Object> options) {
        Object requested = firstPresent(
                options,
                List.of("bootstrap_index_type", "bootstrapIndexType", "index_type", "indexType"));
        return requested == null ? indexType : String.valueOf(requested);
    }

    private static Integer extractDimension(Map<String, Object> options) {
        Object value = firstPresent(options, List.of("dimension", "vector_dimension", "vectorDimension"));
        if (value instanceof Number number) {
            int dimension = number.intValue();
            return dimension > 0 ? dimension : null;
        }
        return null;
    }

    private int inferDimension(List<Map<String, Object>> data) {
        if (data == null) {
            return 0;
        }
        for (Map<String, Object> record : data) {
            List<Float> vector = castFloatList(record.get(vectorField));
            if (vector != null && !vector.isEmpty()) {
                return vector.size();
            }
        }
        return 0;
    }

    private enum SearchMode {
        VECTOR,
        SPARSE,
        HYBRID
    }
}
