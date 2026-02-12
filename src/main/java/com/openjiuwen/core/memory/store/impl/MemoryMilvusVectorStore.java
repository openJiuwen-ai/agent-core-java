/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.store.impl;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vectorstore.VectorStore;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.openjiuwen.core.memory.store.impl.MilvusUtils.STORE_TYPE;

/**
 * Milvus vector store implementation for memory module.
 * Provides vector storage and search capabilities using Milvus.
 */
public class MemoryMilvusVectorStore implements VectorStore {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final int DEFAULT_TIMEOUT = 3;

    private final String milvusHost;
    private final String milvusPort;
    private final String token;
    private final int embeddingDims;
    private final int timeout;
    private final Map<String, Object> collections;
    
    private MilvusClient client;
    private List<MockSearchHit> mockSearchHits; // For testing
    
    /**
     * Mock search hit for testing.
     */
    public record MockSearchHit(String id, float score) {}

    /**
     * Create a new MemoryMilvusVectorStore.
     *
     * @param milvusHost Milvus server host
     * @param milvusPort Milvus server port
     * @param token authentication token (nullable)
     * @param embeddingDims embedding vector dimensions
     */
    public MemoryMilvusVectorStore(String milvusHost, String milvusPort, String token, int embeddingDims) {
        this.milvusHost = milvusHost;
        this.milvusPort = milvusPort;
        this.token = token;
        this.embeddingDims = embeddingDims;
        this.timeout = DEFAULT_TIMEOUT;
        this.collections = new ConcurrentHashMap<>();
    }

    /**
     * Create client (not implemented for MemoryMilvusVectorStore).
     * This static method matches the Python interface but is not supported.
     *
     * @param databaseName database name
     * @param pathOrUri path or URI
     * @param token authentication token
     * @return null (not implemented)
     */
    public static Object createClient(String databaseName, String pathOrUri, String token) {
        logger.error("create_client not implemented in MemoryMilvusVectorStore");
        return null;
    }

    // Getters for testing
    public String getMilvusHost() {
        return milvusHost;
    }

    public String getMilvusPort() {
        return milvusPort;
    }

    public String getToken() {
        return token;
    }

    public int getEmbeddingDims() {
        return embeddingDims;
    }

    public Map<String, Object> getCollections() {
        return collections;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Create Milvus client instance.
     * Can be overridden for testing.
     *
     * @return MilvusClient instance
     */
    protected MilvusClient createMilvusClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusHost)
                .withPort(Integer.parseInt(milvusPort))
                .withConnectTimeout(timeout, TimeUnit.SECONDS);

        if (token != null && !token.isEmpty()) {
            builder.withToken(token);
        }

        return new MilvusServiceClient(builder.build());
    }

    /**
     * Ensure connection to Milvus server.
     *
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Void> ensureConnection() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (client == null) {
                    client = createMilvusClient();
                }
            } catch (Exception e) {
                throw ErrorBuilder.build(
                        StatusCode.MEMORY_CONNECT_STORE_EXECUTION_ERROR,
                        "milvus connect error: " + e.getMessage(),
                        null, e, Map.of("store_type", STORE_TYPE));
            }
        });
    }

    private CompletableFuture<Void> getOrCreateCollection(String collectionName) {
        return ensureConnection().thenRunAsync(() -> {
            if (collections.containsKey(collectionName)) {
                return;
            }

            R<Boolean> hasCollection = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());

            if (hasCollection.getData() == null || !hasCollection.getData()) {
                logger.info("Collection {} not found, creating...", collectionName);

                // Create collection schema
                FieldType idField = FieldType.newBuilder()
                        .withName("id")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MilvusUtils.MEMORY_ID_LENGTH)
                        .withPrimaryKey(true)
                        .build();

                FieldType embeddingField = FieldType.newBuilder()
                        .withName("embedding")
                        .withDataType(DataType.FloatVector)
                        .withDimension(embeddingDims)
                        .build();

                CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withDescription("embedding collection")
                        .addFieldType(idField)
                        .addFieldType(embeddingField)
                        .build();

                R<RpcStatus> createResult = client.createCollection(createParam);
                if (createResult.getException() != null) {
                    throw new RuntimeException("Failed to create collection: " + createResult.getException().getMessage());
                }

                // Create index
                CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName("embedding")
                        .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                        .withMetricType(io.milvus.param.MetricType.IP)
                        .withExtraParam("{\"nlist\": 128}")
                        .build();

                R<RpcStatus> indexResult = client.createIndex(indexParam);
                if (indexResult.getException() != null) {
                    throw new RuntimeException("Failed to create index: " + indexResult.getException().getMessage());
                }
                logger.info("Index created for collection {}", collectionName);
            } else {
                logger.info("Milvus collection already exists: {}", collectionName);
            }

            // Load collection
            R<RpcStatus> loadResult = client.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());

            if (loadResult.getException() != null) {
                throw new RuntimeException("Failed to load collection: " + loadResult.getException().getMessage());
            }

            collections.put(collectionName, collectionName);
        });
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize) {
        // table_name is required
        return CompletableFuture.failedFuture(
                ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "table_name is required for add operation",
                        null, null, Map.of("store_type", STORE_TYPE)));
    }

    /**
     * Add vectors to a specific table/collection.
     *
     * @param data list of records with "id" and "embedding" fields
     * @param tableName target collection name
     * @return CompletableFuture that completes when insertion is done
     */
    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return CompletableFuture.failedFuture(
                    ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                            "table_name is required for add operation",
                            null, null, Map.of("store_type", STORE_TYPE)));
        }

        return getOrCreateCollection(tableName).thenRunAsync(() -> {
            List<String> ids = new ArrayList<>();
            List<List<Float>> embeddings = new ArrayList<>();

            for (Map<String, Object> item : data) {
                ids.add((String) item.get("id"));
                @SuppressWarnings("unchecked")
                List<? extends Number> embedding = (List<? extends Number>) item.get("embedding");
                List<Float> floatEmbedding = new ArrayList<>();
                for (Number n : embedding) {
                    floatEmbedding.add(n.floatValue());
                }
                embeddings.add(floatEmbedding);
            }

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", ids));
            fields.add(new InsertParam.Field("embedding", embeddings));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(tableName)
                    .withFields(fields)
                    .build();

            R<MutationResult> result = client.insert(insertParam);
            if (result.getException() != null) {
                throw new RuntimeException("Failed to insert: " + result.getException().getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<List<SearchResult>> search(List<Double> queryVector, int topK, Map<String, Object> filters) {
        // table_name is required
        return CompletableFuture.failedFuture(
                ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "table_name is required for search operation",
                        null, null, Map.of("store_type", STORE_TYPE)));
    }

    @Override
    public CompletableFuture<List<SearchResult>> search(List<Double> queryVector, int topK, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return CompletableFuture.failedFuture(
                    ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                            "table_name is required for search operation",
                            null, null, Map.of("store_type", STORE_TYPE)));
        }

        return getOrCreateCollection(tableName).thenApplyAsync(v -> {
            // For testing with mock search hits
            if (mockSearchHits != null) {
                List<SearchResult> results = new ArrayList<>();
                for (var hit : mockSearchHits) {
                    results.add(SearchResult.builder()
                            .id(hit.id())
                            .score(hit.score())
                            .text("")
                            .build());
                }
                return results;
            }

            List<Float> floatVector = new ArrayList<>();
            for (Double d : queryVector) {
                floatVector.add(d.floatValue());
            }

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(tableName)
                    .withVectors(List.of(floatVector))
                    .withVectorFieldName("embedding")
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.IP)
                    .withParams("{\"nprobe\": 10}")
                    .build();

            R<SearchResults> result = client.search(searchParam);
            if (result.getException() != null) {
                throw new RuntimeException("Search failed: " + result.getException().getMessage());
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

            return MilvusUtils.convertMilvusResult(idScores);
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(List<String> ids, String filterExpr) {
        // table_name is required
        return CompletableFuture.failedFuture(
                ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "table_name is required for delete operation",
                        null, null, Map.of("store_type", STORE_TYPE)));
    }

    @Override
    public CompletableFuture<Boolean> deleteFromTable(List<String> ids, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return CompletableFuture.failedFuture(
                    ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                            "table_name is required for delete operation",
                            null, null, Map.of("store_type", STORE_TYPE)));
        }

        return ensureConnection().thenApplyAsync(v -> {
            R<Boolean> hasCollection = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(tableName)
                            .build());

            if (hasCollection.getData() == null || !hasCollection.getData()) {
                logger.debug("Milvus Collection {} does not exist, skip delete vector", tableName);
                return true;
            }

            // Load collection first
            client.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(tableName)
                    .build());

            String idsStr = String.join(", ", ids.stream().map(i -> "\"" + i + "\"").toList());
            String expr = "id in [" + idsStr + "]";

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(tableName)
                    .withExpr(expr)
                    .build();

            R<MutationResult> result = client.delete(deleteParam);
            if (result.getException() != null) {
                throw new RuntimeException("Delete failed: " + result.getException().getMessage());
            }

            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteTable(String tableName) {
        return ensureConnection().thenApplyAsync(v -> {
            R<Boolean> hasCollection = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(tableName)
                            .build());

            if (hasCollection.getData() == null || !hasCollection.getData()) {
                logger.debug("Milvus Collection {} does not exist, skip delete collection", tableName);
                return true;
            }

            R<RpcStatus> result = client.dropCollection(
                    DropCollectionParam.newBuilder()
                            .withCollectionName(tableName)
                            .build());

            if (result.getException() != null) {
                throw new RuntimeException("Drop collection failed: " + result.getException().getMessage());
            }

            collections.remove(tableName);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return ensureConnection().thenApplyAsync(v -> {
            R<Boolean> hasCollection = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(tableName)
                            .build());

            return hasCollection.getData() != null && hasCollection.getData();
        });
    }

    @Override
    public CompletableFuture<List<SearchResult>> sparseSearch(String queryText, int topK, Map<String, Object> filters) {
        // Not implemented for Milvus
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<SearchResult>> hybridSearch(String queryText, List<Double> queryVector,
                                                               int topK, double alpha, Map<String, Object> filters) {
        // Not implemented for Milvus
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public void checkVectorField() {
        logger.error("check_vector_field not implemented in MemoryMilvusVectorStore");
    }

    // For testing
    public void setMockSearchHits(List<MockSearchHit> mockSearchHits) {
        this.mockSearchHits = mockSearchHits;
    }
}

