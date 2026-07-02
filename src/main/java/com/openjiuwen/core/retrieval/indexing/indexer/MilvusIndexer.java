/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusVectorField;
import com.openjiuwen.core.foundation.store.vector_fields.VectorField;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.TqdmCallback;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors Python's {@code MilvusIndexer} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/milvus_indexer.py}.
 */
public class MilvusIndexer extends Indexer implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger(MilvusIndexer.class.getName());
    private static final int DEFAULT_BATCH_SIZE = 128;
    private static final Set<String> VECTOR_INDEX_TYPES = Set.of("vector", "hybrid");
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("milvus-indexer-io");

    private final String milvusUri;
    private final String milvusToken;
    private final String textField;
    private final MilvusVectorField vectorField;
    private final String sparseVectorField;
    private final String metadataField;
    private final String docIdField;
    private final String databaseName;
    private final Map<String, Object> constructConfig;
    private final String distanceMetric;
    private final Map<String, Object> searchConfig;
    private final Class<? extends BaseCallback> docIndexCallback;
    private final String milvusAlias;
    private final MilvusClientFacade client;

    public MilvusIndexer(VectorStoreConfig config, String milvusUri) {
        this(config, milvusUri, null);
    }

    public MilvusIndexer(VectorStoreConfig config, String milvusUri, String milvusToken) {
        this(
                config,
                milvusUri,
                milvusToken,
                "content",
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                TqdmCallback.class,
                null,
                Map.of()
        );
    }

    public MilvusIndexer(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            String vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            Class<? extends BaseCallback> docIndexCallback,
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
                docIndexCallback,
                milvusAlias,
                kwargs
        );
    }

    public MilvusIndexer(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            MilvusVectorField vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            Class<? extends BaseCallback> docIndexCallback,
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
                docIndexCallback,
                milvusAlias,
                kwargs
        );
    }

    MilvusIndexer(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            Class<? extends BaseCallback> docIndexCallback,
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
                docIndexCallback,
                milvusAlias,
                new DefaultMilvusClientFacade(
                        config == null ? "" : config.getDatabaseName(),
                        milvusUri,
                        milvusToken,
                        kwargs
                )
        );
    }

    MilvusIndexer(
            VectorStoreConfig config,
            String milvusUri,
            String milvusToken,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            Class<? extends BaseCallback> docIndexCallback,
            String milvusAlias,
            MilvusClientFacade client
    ) {
        this.milvusUri = Objects.requireNonNull(milvusUri, "milvusUri");
        this.milvusToken = milvusToken;
        this.textField = textField == null || textField.isBlank() ? "content" : textField;
        this.sparseVectorField = sparseVectorField == null || sparseVectorField.isBlank()
                ? "sparse_vector" : sparseVectorField;
        this.metadataField = metadataField == null || metadataField.isBlank() ? "metadata" : metadataField;
        this.docIdField = docIdField == null || docIdField.isBlank() ? "document_id" : docIdField;
        this.databaseName = config == null || config.getDatabaseName() == null ? "" : config.getDatabaseName();
        this.vectorField = resolveVectorField(vectorField);
        this.distanceMetric = normalizeDistanceMetric(config == null ? null : config.getDistanceMetric());
        this.constructConfig = buildConstructConfig(this.vectorField, distanceMetric);
        this.searchConfig = new LinkedHashMap<>(this.vectorField.toDict(VectorField.STAGE_SEARCH));
        this.docIndexCallback = docIndexCallback == null ? TqdmCallback.class : docIndexCallback;
        this.milvusAlias = CommonUtils.createMilvusAlias(milvusAlias, milvusUri, "", milvusToken);
        this.client = Objects.requireNonNull(client, "client");
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

    public Map<String, Object> getConstructConfig() {
        return new LinkedHashMap<>(constructConfig);
    }

    public Map<String, Object> getSearchConfig() {
        return new LinkedHashMap<>(searchConfig);
    }

    public Class<? extends BaseCallback> getDocIndexCallback() {
        return docIndexCallback;
    }

    public String getMilvusAlias() {
        return milvusAlias;
    }

    public MilvusClientFacade getClient() {
        return client;
    }

    @Override
    public CompletableFuture<Boolean> buildIndex(
            List<TextChunk> chunks,
            IndexConfig config,
            Embedding embedModel,
            Map<String, Object> kwargs
    ) {
        List<TextChunk> safeChunks = chunks == null ? List.of() : chunks;
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
        try {
            String collectionName = Objects.requireNonNull(config, "config").getIndexName();
            return ensureCollection(collectionName, config, embedModel)
                    .thenCompose(ignored -> findDuplicateDocIds(collectionName, safeChunks))
                    .thenCompose(duplicateDocIds -> {
                        if (!duplicateDocIds.isEmpty()) {
                            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                                    StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR,
                                    "error_msg",
                                    "some documents with same doc_id already exist, if they are the same documents, "
                                            + "please consider updating instead of adding. duplicate_doc_ids="
                                            + duplicateDocIds
                            ));
                        }
                        if (!requiresDenseVector(config)) {
                            return CompletableFuture.completedFuture(null);
                        }
                        if (embedModel == null) {
                            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                                    StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND,
                                    "error_msg",
                                    "embed_model is required for vector/hybrid index type"
                            ));
                        }
                        return EmbedChunks.computeChunkEmbeddings(
                                safeChunks,
                                embedModel,
                                docIndexCallback,
                                config.isUseCaptionForImages()
                        );
                    })
                    .thenCompose(ignored -> client.insert(collectionName, toMilvusRows(safeChunks), DEFAULT_BATCH_SIZE))
                    .thenApply(ignored -> Boolean.TRUE)
                    .handle((result, error) -> {
                        if (error == null) {
                            LOGGER.info("Successfully built index " + collectionName
                                    + " with " + safeChunks.size() + " chunks");
                            return result;
                        }
                        throw wrapBuildIndexFailure(error);
                    });
        } catch (BaseError error) {
            return CompletableFuture.failedFuture(error);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(buildAddDocError(error));
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
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
        try {
            return deleteIndex(docId, config.getIndexName(), safeKwargs)
                    .thenCompose(ignored -> client.flush(config.getIndexName()))
                    .thenCompose(ignored -> buildIndex(chunks, config, embedModel, safeKwargs))
                    .exceptionally(error -> {
                        LOGGER.log(Level.SEVERE, "Failed to update index: " + unwrap(error).getMessage(), unwrap(error));
                        return Boolean.FALSE;
                    });
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Failed to update index: " + error.getMessage(), error);
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
        String filterExpr = docIdField + " == \"" + docId + "\"";
        return client.delete(indexName, filterExpr)
                .thenApply(deleteCount -> {
                    LOGGER.info("Deleted " + deleteCount + " entries for doc_id=" + docId);
                    return deleteCount > 0;
                })
                .exceptionally(error -> {
                    LOGGER.log(Level.SEVERE,
                            "Failed to delete index entries: " + unwrap(error).getMessage(), unwrap(error));
                    return Boolean.FALSE;
                });
    }

    @Override
    public CompletableFuture<Boolean> indexExists(String indexName) {
        return client.hasCollection(indexName)
                .exceptionally(error -> {
                    LOGGER.log(Level.SEVERE,
                            "Failed to check index existence: " + unwrap(error).getMessage(), unwrap(error));
                    return Boolean.FALSE;
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
        return indexExists(indexName).thenCompose(exists -> {
            if (!exists) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("exists", Boolean.FALSE);
                return CompletableFuture.completedFuture(result);
            }
            CompletableFuture<Long> countFuture = client.count(indexName)
                    .exceptionally(error -> 0L);
            CompletableFuture<Object> infoFuture = client.describeCollection(indexName)
                    .<Object>thenApply(description -> description)
                    .exceptionally(error -> {
                        Map<String, Object> fallback = new LinkedHashMap<>();
                        fallback.put("error", unwrap(error).getMessage());
                        return fallback;
                    });
            return countFuture.thenCombine(infoFuture, (count, info) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("exists", Boolean.TRUE);
                result.put("collection_name", indexName);
                result.put("info", info);
                result.put("count", count);
                return result;
            });
        }).exceptionally(error -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", Boolean.FALSE);
            result.put("error", unwrap(error).getMessage());
            return result;
        });
    }

    public CompletableFuture<Void> ensureCollection(
            String collectionName,
            IndexConfig config,
            Embedding embedModel
    ) {
        return client.hasCollection(collectionName).thenCompose(exists -> {
            if (exists) {
                return CompletableFuture.completedFuture(null);
            }
            return resolveDimension(config, embedModel)
                    .thenCompose(dimension -> client.createCollection(
                            collectionName,
                            buildSchema(config, dimension),
                            buildIndexParams(config),
                            buildCreateCollectionOptions()
                    ));
        });
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception error) {
            LOGGER.log(Level.WARNING, "Failed to close Milvus client: " + error.getMessage(), error);
        }
    }

    private CompletableFuture<List<String>> findDuplicateDocIds(String collectionName, List<TextChunk> chunks) {
        Set<String> docIds = new LinkedHashSet<>();
        for (TextChunk chunk : chunks) {
            if (chunk.getDocId() != null && !chunk.getDocId().isBlank()) {
                docIds.add(chunk.getDocId());
            }
        }
        if (docIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String filter = docIdField + " in " + quoteArray(docIds);
        return client.query(collectionName, filter, List.of(docIdField))
                .thenApply(rows -> {
                    Set<String> duplicates = new LinkedHashSet<>();
                    for (Map<String, Object> row : rows) {
                        Object value = row.get(docIdField);
                        if (value != null && docIds.contains(String.valueOf(value))) {
                            duplicates.add(String.valueOf(value));
                        }
                    }
                    return new ArrayList<>(duplicates);
                });
    }

    private CompletableFuture<Integer> resolveDimension(IndexConfig config, Embedding embedModel) {
        if (!requiresDenseVector(config)) {
            return CompletableFuture.completedFuture(null);
        }
        if (embedModel == null) {
            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_DIMENSION_NOT_FOUND,
                    "error_msg",
                    "dimension is required for vector/hybrid index type"
            ));
        }
        int dimension = embedModel.getDimension();
        if (dimension > 0) {
            return CompletableFuture.completedFuture(dimension);
        }
        return embedModel.embedQuery("X", Map.of())
                .handle((embedding, error) -> {
                    if (error != null) {
                        LOGGER.log(Level.WARNING,
                                "Unable to get dimension through API call: " + unwrap(error).getMessage(),
                                unwrap(error));
                        return 0;
                    }
                    return embedding == null ? 0 : embedding.size();
                })
                .thenApply(resolved -> {
                    if (resolved == null || resolved == 0) {
                        throw ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_INDEXING_DIMENSION_NOT_FOUND,
                                "error_msg",
                                "dimension is required for vector/hybrid index type"
                        );
                    }
                    LOGGER.fine("Got dimension through API call: " + resolved);
                    return resolved;
                });
    }

    private CreateCollectionReq.CollectionSchema buildSchema(IndexConfig config, Integer dimension) {
        boolean enableBm25 = isBm25Enabled(config);
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .build();
        schema.addField(AddFieldReq.builder()
                .fieldName(docIdField)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("pk")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id")
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());
        AddFieldReq.AddFieldReqBuilder<?> textBuilder = AddFieldReq.builder()
                .fieldName(textField)
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .enableAnalyzer(enableBm25);
        if (enableBm25) {
            textBuilder.analyzerParams(Map.of("tokenizer", "jieba"));
        }
        schema.addField(textBuilder.build());
        if (enableBm25) {
            schema.addField(AddFieldReq.builder()
                    .fieldName(sparseVectorField)
                    .dataType(DataType.SparseFloatVector)
                    .build());
            schema.addFunction(CreateCollectionReq.Function.builder()
                    .name("text_bm25_emb")
                    .inputFieldNames(List.of(textField))
                    .outputFieldNames(List.of(sparseVectorField))
                    .functionType(FunctionType.BM25)
                    .build());
        }
        if (requiresDenseVector(config)) {
            schema.addField(AddFieldReq.builder()
                    .fieldName(vectorField.getVectorField())
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());
        }
        schema.addField(AddFieldReq.builder()
                .fieldName(metadataField)
                .dataType(DataType.JSON)
                .build());
        return schema;
    }

    private List<IndexParam> buildIndexParams(IndexConfig config) {
        List<IndexParam> params = new ArrayList<>();
        params.add(IndexParam.builder()
                .fieldName(docIdField)
                .indexName(docIdField)
                .indexType(IndexParam.IndexType.INVERTED)
                .build());
        params.add(IndexParam.builder()
                .fieldName("chunk_id")
                .indexName("chunk_id")
                .indexType(IndexParam.IndexType.INVERTED)
                .build());
        if (isBm25Enabled(config)) {
            params.add(IndexParam.builder()
                    .fieldName(sparseVectorField)
                    .indexName(sparseVectorField)
                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                    .metricType(IndexParam.MetricType.BM25)
                    .build());
        }
        if (requiresDenseVector(config)) {
            params.add(IndexParam.builder()
                    .fieldName(vectorField.getVectorField())
                    .indexName(vectorField.getVectorField())
                    .indexType(resolveIndexType(vectorField))
                    .metricType(IndexParam.MetricType.valueOf(distanceMetric))
                    .extraParams(constructConfigWithoutMetric())
                    .build());
        }
        return params;
    }

    private Map<String, Object> buildCreateCollectionOptions() {
        Map<String, Object> options = new LinkedHashMap<>(constructConfig);
        options.put("metric_type", distanceMetric);
        options.put("database_name", databaseName);
        options.put("milvus_alias", milvusAlias);
        return options;
    }

    private List<Map<String, Object>> toMilvusRows(List<TextChunk> chunks) {
        List<Map<String, Object>> rows = new ArrayList<>(chunks.size());
        for (TextChunk chunk : chunks) {
            Map<String, Object> metadata = chunk.getMetadata();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunk_id", metadata.getOrDefault("chunk_id", chunk.getId_()));
            row.put(docIdField, chunk.getDocId());
            row.put(textField, chunk.getText());
            row.put(metadataField, metadata);
            if (chunk.getEmbedding() != null) {
                row.put(vectorField.getVectorField(), chunk.getEmbedding());
            }
            rows.add(row);
        }
        return rows;
    }

    private CompletionException wrapBuildIndexFailure(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BaseError baseError) {
            return new CompletionException(baseError);
        }
        LOGGER.log(Level.SEVERE, "Failed to build index: " + cause.getMessage(), cause);
        return new CompletionException(buildAddDocError(cause));
    }

    private BaseError buildAddDocError(Throwable error) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR,
                null,
                null,
                error,
                Map.of("error_msg", error.getMessage() == null ? String.valueOf(error) : error.getMessage())
        );
    }

    private Map<String, Object> constructConfigWithoutMetric() {
        Map<String, Object> result = new LinkedHashMap<>(constructConfig);
        result.remove("metric_type");
        return result;
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

    private static IndexParam.IndexType resolveIndexType(MilvusVectorField vectorField) {
        String indexType = vectorField.getIndexType();
        if ("auto".equals(indexType)) {
            return IndexParam.IndexType.AUTOINDEX;
        }
        String value = indexType.toUpperCase(Locale.ROOT);
        String variant = vectorField.getVariant();
        if (variant != null && !variant.isBlank()) {
            value = value + "_" + variant.toUpperCase(Locale.ROOT);
        }
        return IndexParam.IndexType.valueOf(value);
    }

    private static String normalizeDistanceMetric(String distanceMetric) {
        String value = distanceMetric == null ? "cosine" : distanceMetric;
        return value.replace("dot", "ip")
                .replace("euclidean", "l2")
                .toUpperCase(Locale.ROOT);
    }

    private static boolean requiresDenseVector(IndexConfig config) {
        return config != null && VECTOR_INDEX_TYPES.contains(config.getIndexType());
    }

    private static boolean isBm25Enabled(IndexConfig config) {
        return config != null && ("bm25".equals(config.getIndexType()) || "hybrid".equals(config.getIndexType()));
    }

    private static String quoteArray(Set<String> values) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            first = false;
        }
        return builder.append(']').toString();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Mirrors Python's Milvus client boundary in
     * {@code openjiuwen/core/retrieval/indexing/indexer/milvus_indexer.py}.
     */
    public interface MilvusClientFacade extends AutoCloseable {
        CompletableFuture<Boolean> hasCollection(String collectionName);

        CompletableFuture<Void> createCollection(
                String collectionName,
                CreateCollectionReq.CollectionSchema schema,
                List<IndexParam> indexParams,
                Map<String, Object> options
        );

        CompletableFuture<List<Map<String, Object>>> query(
                String collectionName,
                String filter,
                List<String> outputFields
        );

        CompletableFuture<Void> insert(String collectionName, List<Map<String, Object>> rows, int batchSize);

        CompletableFuture<Long> delete(String collectionName, String filter);

        CompletableFuture<Void> flush(String collectionName);

        CompletableFuture<Long> count(String collectionName);

        CompletableFuture<DescribeCollectionResp> describeCollection(String collectionName);

        @Override
        void close();
    }

    /**
     * Mirrors Python's default Milvus client created by
     * {@code openjiuwen/core/retrieval/indexing/indexer/milvus_indexer.py}.
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
            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
            long timeoutMs = Math.max(1L, Math.round(doubleValue(safeKwargs.get("timeout"), 3.0d) * 1000.0d));
            ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                    .uri(Objects.requireNonNull(milvusUri, "milvusUri"))
                    .connectTimeoutMs(timeoutMs)
                    .rpcDeadlineMs(timeoutMs)
                    .enablePrecheck(false);
            if (milvusToken != null && !milvusToken.isBlank()) {
                builder.token(milvusToken);
            }
            String dbName = databaseName == null || databaseName.isBlank() ? "default" : databaseName;
            this.databaseName = dbName;
            if (!"default".equals(dbName)) {
                builder.dbName(dbName);
            }
            this.delegate = new MilvusClientV2(builder.build());
            if (!"default".equals(dbName)) {
                if (!delegate.listDatabases().getDatabaseNames().contains(dbName)) {
                    delegate.createDatabase(CreateDatabaseReq.builder().databaseName(dbName).build());
                }
                try {
                    delegate.useDatabase(dbName);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while switching Milvus database", error);
                }
            }
        }

        @Override
        public CompletableFuture<Boolean> hasCollection(String collectionName) {
            return CompletableFuture.supplyAsync(() -> delegate.hasCollection(HasCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build()), IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<Void> createCollection(
                String collectionName,
                CreateCollectionReq.CollectionSchema schema,
                List<IndexParam> indexParams,
                Map<String, Object> options
        ) {
            return CompletableFuture.runAsync(() -> delegate.createCollection(CreateCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .primaryFieldName("pk")
                    .idType(DataType.Int64)
                    .autoID(true)
                    .enableDynamicField(false)
                    .build()), IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> query(
                String collectionName,
                String filter,
                List<String> outputFields
        ) {
            return CompletableFuture.supplyAsync(() -> {
                QueryResp response = delegate.query(QueryReq.builder()
                        .databaseName(databaseName)
                        .collectionName(collectionName)
                        .filter(filter)
                        .outputFields(outputFields)
                        .build());
                return response.getQueryResults().stream()
                        .map(QueryResp.QueryResult::getEntity)
                        .map(LinkedHashMap::new)
                        .map(row -> (Map<String, Object>) row)
                        .toList();
            }, IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<Void> insert(String collectionName, List<Map<String, Object>> rows, int batchSize) {
            return CompletableFuture.runAsync(() -> {
                int size = Math.max(1, batchSize);
                for (int start = 0; start < rows.size(); start += size) {
                    List<JsonObject> payload = rows.subList(start, Math.min(start + size, rows.size()))
                            .stream()
                            .map(row -> GSON.toJsonTree(row).getAsJsonObject())
                            .toList();
                    delegate.insert(InsertReq.builder()
                            .databaseName(databaseName)
                            .collectionName(collectionName)
                            .data(payload)
                            .build());
                }
                delegate.flush(FlushReq.builder()
                        .databaseName(databaseName)
                        .collectionNames(List.of(collectionName))
                        .build());
            }, IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<Long> delete(String collectionName, String filter) {
            return CompletableFuture.supplyAsync(() -> {
                DeleteResp response = delegate.delete(DeleteReq.builder()
                        .databaseName(databaseName)
                        .collectionName(collectionName)
                        .filter(filter)
                        .build());
                long count = response.getDeleteCnt();
                delegate.flush(FlushReq.builder()
                        .databaseName(databaseName)
                        .collectionNames(List.of(collectionName))
                        .build());
                return count;
            }, IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<Void> flush(String collectionName) {
            return CompletableFuture.runAsync(() -> delegate.flush(FlushReq.builder()
                    .databaseName(databaseName)
                    .collectionNames(List.of(collectionName))
                    .build()), IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<Long> count(String collectionName) {
            return CompletableFuture.supplyAsync(() -> {
                Long count = delegate.getCollectionStats(GetCollectionStatsReq.builder()
                        .databaseName(databaseName)
                        .collectionName(collectionName)
                        .build()).getNumOfEntities();
                return count == null ? 0L : count;
            }, IO_EXECUTOR);
        }

        @Override
        public CompletableFuture<DescribeCollectionResp> describeCollection(String collectionName) {
            return CompletableFuture.supplyAsync(() -> delegate.describeCollection(DescribeCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build()), IO_EXECUTOR);
        }

        @Override
        public void close() {
            delegate.close();
        }

        private double doubleValue(Object value, double defaultValue) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value == null) {
                return defaultValue;
            }
            return Double.parseDouble(String.valueOf(value));
        }
    }
}
