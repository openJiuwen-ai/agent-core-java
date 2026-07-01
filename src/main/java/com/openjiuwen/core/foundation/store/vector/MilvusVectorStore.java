/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.AlterCollectionPropertiesReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.request.RenameCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * Milvus vector store implementation.
 *
 * <p>Mirrors Python's {@code MilvusVectorStore} in
 * {@code openjiuwen/core/foundation/store/vector/milvus_vector_store.py}.</p>
 */
public class MilvusVectorStore extends BaseVectorStore {

    public static final String PYTHON_MODULE =
            "openjiuwen/core/foundation/store/vector/milvus_vector_store.py";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Logger LOGGER = Logger.getLogger(MilvusVectorStore.class.getName());

    private final String milvusUri;
    private final String milvusToken;
    private final String databaseName;
    private final Map<String, Object> clientKwargs;
    private final Map<String, CollectionMetadata> collectionMetadata = new LinkedHashMap<>();
    private final Set<String> collectionsLoaded = new LinkedHashSet<>();
    private final boolean closeClientOnClose;
    private MilvusClientAdapter client;

    public MilvusVectorStore(String milvusUri) {
        this(milvusUri, null, "default", Map.of());
    }

    public MilvusVectorStore(String milvusUri, String milvusToken, String databaseName) {
        this(milvusUri, milvusToken, databaseName, Map.of());
    }

    public MilvusVectorStore(String milvusUri, String milvusToken, String databaseName,
            Map<String, Object> clientKwargs) {
        this.milvusUri = Objects.requireNonNull(milvusUri, "milvusUri");
        this.milvusToken = milvusToken;
        this.databaseName = databaseName == null || databaseName.isBlank() ? "default" : databaseName;
        this.clientKwargs = clientKwargs == null ? Map.of() : new LinkedHashMap<>(clientKwargs);
        this.closeClientOnClose = true;
    }

    public MilvusVectorStore(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
        this.milvusUri = Objects.toString(firstValue(safeKwargs, "milvus_uri", "uri", "path_or_uri"),
                "http://localhost:19530");
        Object token = firstValue(safeKwargs, "milvus_token", "token");
        this.milvusToken = token == null ? null : String.valueOf(token);
        Object database = firstValue(safeKwargs, "database_name", "database", "db_name");
        this.databaseName = database == null || String.valueOf(database).isBlank() ? "default" : String.valueOf(database);
        this.clientKwargs = new LinkedHashMap<>(safeKwargs);
        this.closeClientOnClose = true;
    }

    MilvusVectorStore(MilvusClientAdapter client) {
        this.milvusUri = "";
        this.milvusToken = null;
        this.databaseName = "default";
        this.clientKwargs = Map.of();
        this.client = Objects.requireNonNull(client, "client");
        this.closeClientOnClose = false;
    }

    public String getMilvusUri() {
        return milvusUri;
    }

    public String getMilvusToken() {
        return milvusToken;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public CompletableFuture<MilvusClientAdapter> client() {
        return CompletableFuture.supplyAsync(this::clientSync);
    }

    public void close() {
        if (client == null) {
            return;
        }
        if (closeClientOnClose) {
            client.close();
        }
        client = null;
        collectionsLoaded.clear();
        LOGGER.info("Milvus client connection closed");
    }

    @Override
    public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            MilvusClientAdapter adapter = clientSync();
            if (adapter.hasCollection(collectionName)) {
                LOGGER.info("Collection already exists, skipping creation");
                return;
            }

            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
            String distanceMetric = Objects.toString(safeKwargs.getOrDefault("distance_metric", "COSINE"))
                    .toUpperCase(Locale.ROOT);
            String indexType = Objects.toString(safeKwargs.getOrDefault("index_type", "AUTOINDEX"));
            CollectionSchema collectionSchema = normalizeSchema(schema);
            FieldSchema vectorField = null;
            for (FieldSchema field : collectionSchema.getFields()) {
                if (mapFieldType(field.getDtype()) != DataType.FloatVector) {
                    continue;
                }
                if (field.getDim() == null || field.getDim() <= 0) {
                    throw buildError(
                            StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                            "error_msg",
                            "dim of vector field is missing, field=" + field.getName() + ", dim=" + field.getDim()
                    );
                }
                vectorField = field;
                break;
            }
            if (vectorField == null) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "schema must contain at least one FLOAT_VECTOR field"
                );
            }

            adapter.createCollection(collectionName, collectionSchema, distanceMetric, indexType);
            collectionMetadata.put(collectionName, new CollectionMetadata(distanceMetric, vectorField.getName(),
                    vectorField.getDim(), null));
            LOGGER.info("Created collection with " + collectionSchema.getFields().size() + " fields");
        });
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            MilvusClientAdapter adapter = clientSync();
            if (!adapter.hasCollection(collectionName)) {
                LOGGER.warning("Collection does not exist");
                return;
            }
            adapter.dropCollection(collectionName);
            collectionMetadata.remove(collectionName);
            collectionsLoaded.remove(collectionName);
            LOGGER.info("Deleted collection");
        });
    }

    @Override
    public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> clientSync().hasCollection(collectionName));
    }

    @Override
    public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            MilvusClientAdapter adapter = clientSync();
            if (!adapter.hasCollection(collectionName)) {
                throw buildError(StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND, "collection_name", collectionName);
            }
            CollectionDescription description = adapter.describeCollection(collectionName);
            CollectionSchema schema = new CollectionSchema(new ArrayList<>(),
                    description.description(), description.enableDynamicField());
            for (FieldDescription field : description.fields()) {
                schema.addField(new FieldSchema(
                        field.name(),
                        field.dtype(),
                        field.primary(),
                        field.autoId(),
                        field.maxLength(),
                        field.dim(),
                        null,
                        null,
                        field.description(),
                        null
                ));
            }
            return schema;
        });
    }

    @Override
    public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            ensureLoaded(collectionName);
            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
            int batchSize = intValue(safeKwargs.get("batch_size"), 128);
            if (batchSize <= 0) {
                batchSize = 128;
            }
            if (!collectionMetadata.containsKey(collectionName)) {
                cacheVectorFieldFromDescription(collectionName);
            }

            int total = docs.size();
            int processed = 0;
            for (int index = 0; index < total; index += batchSize) {
                List<Map<String, Object>> batch = docs.subList(index, Math.min(index + batchSize, total));
                clientSync().insert(collectionName, batch);
                processed += batch.size();
                if (processed % 100 == 0) {
                    LOGGER.info("Added " + processed + "/" + total + " documents to collection");
                }
            }
            clientSync().flush(collectionName);
            LOGGER.info("Successfully added documents collection");
        });
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> search(String collectionName, List<Double> queryVector,
            String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            ensureLoaded(collectionName);
            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
            CollectionMetadata metadata = collectionMetadata.get(collectionName);
            String distanceMetric = Objects.toString(
                    safeKwargs.getOrDefault("metric_type", metadata == null || metadata.distanceMetric() == null
                            ? "COSINE" : metadata.distanceMetric()));
            List<String> outputFields = toStringList(safeKwargs.get("output_fields"));
            if (outputFields.isEmpty()) {
                outputFields = outputFieldsFromSchema(collectionName);
            }
            String filterExpr = filters == null || filters.isEmpty() ? null : buildFilterExpr(filters);
            List<SearchHit> hits = clientSync().search(collectionName, queryVector, vectorField, topK,
                    outputFields, Map.of("metric_type", distanceMetric), filterExpr);
            List<VectorSearchResult> searchResults = new ArrayList<>();
            for (SearchHit hit : hits) {
                double finalScore = finalScore(hit, distanceMetric);
                Map<String, Object> fields = new LinkedHashMap<>();
                Map<String, Object> entity = hit.entity() == null ? Map.of() : hit.entity();
                for (Map.Entry<String, Object> entry : entity.entrySet()) {
                    fields.put(entry.getKey(), parseJsonString(entry.getValue()));
                }
                if (hit.id() != null) {
                    fields.put("id", hit.id());
                } else if (hit.primaryKey() != null) {
                    fields.put("id", String.valueOf(hit.primaryKey()));
                }
                searchResults.add(new VectorSearchResult(finalScore, fields));
            }
            return searchResults;
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (ids == null || ids.isEmpty()) {
                LOGGER.warning("No IDs provided for deletion");
                return;
            }
            ensureLoaded(collectionName);
            Map<String, Object> result = clientSync().deleteByIds(collectionName, ids);
            clientSync().flush(collectionName);
            Object count = result.getOrDefault("delete_count", ids.size());
            LOGGER.info("Deleted documents from collection, count=" + count);
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (filters == null || filters.isEmpty()) {
                LOGGER.warning("No filters provided for deletion");
                return;
            }
            ensureLoaded(collectionName);
            Map<String, Object> result = clientSync().deleteByFilter(collectionName, buildFilterExpr(filters));
            clientSync().flush(collectionName);
            LOGGER.info("Deleted documents matching filters from collection, count="
                    + result.getOrDefault("delete_count", 0));
        });
    }

    @Override
    public CompletableFuture<List<String>> listCollectionNames() {
        return CompletableFuture.supplyAsync(() -> clientSync().listCollections());
    }

    @Override
    public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
        return CompletableFuture.runAsync(() -> {
            if (operations == null || operations.isEmpty()) {
                return;
            }
            CollectionSchema oldSchema = getSchema(collectionName, Map.of()).join();
            CollectionSchema newSchema = computeNewSchema(oldSchema, operations);
            Function<Map<String, Object>, Map<String, Object>> transform =
                    buildTransformFunctionForOperations(operations);
            Map<String, Object> metadata = getCollectionMetadata(collectionName).join();
            executeMigration(collectionName, newSchema, transform, metadata);
        });
    }

    @Override
    public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        return CompletableFuture.runAsync(() -> {
            if (metadata == null || metadata.isEmpty()) {
                return;
            }
            Object schemaVersion = metadata.get("schema_version");
            if (schemaVersion != null && (!(schemaVersion instanceof Number number) || number.intValue() < 0)) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "schema_version must be a non-negative integer, got " + schemaVersion
                );
            }
            try {
                clientSync().describeCollection(collectionName);
            } catch (RuntimeException exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND,
                        "collection_name",
                        collectionName,
                        "error_msg",
                        exception.getMessage()
                );
            }
            Map<String, String> properties = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                properties.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            clientSync().alterCollectionProperties(collectionName, properties);
            CollectionMetadata cached = collectionMetadata.get(collectionName);
            if (cached != null) {
                collectionMetadata.put(collectionName, cached.withValues(metadata));
            }
            LOGGER.fine("Updated collection metadata for '" + collectionName + "': " + metadata);
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
        return CompletableFuture.supplyAsync(() -> {
            CollectionMetadata cached = collectionMetadata.get(collectionName);
            if (cached != null) {
                Map<String, Object> result = cached.toMap();
                result.putIfAbsent("schema_version", schemaVersionOrZero(collectionName));
                return result;
            }
            try {
                CollectionDescription description = clientSync().describeCollection(collectionName);
                String vectorFieldName = null;
                for (FieldDescription field : description.fields()) {
                    if (field.dtype() == VectorDataType.FLOAT_VECTOR) {
                        vectorFieldName = field.name();
                        break;
                    }
                }
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (vectorFieldName == null) {
                    metadata.put("distance_metric", "COSINE");
                    metadata.put("schema_version", 0);
                    return metadata;
                }
                String metric = clientSync().describeIndexMetric(collectionName, vectorFieldName);
                metadata.put("distance_metric", metric);
                metadata.put("vector_field", vectorFieldName);
                metadata.put("schema_version", schemaVersionOrZero(description));
                collectionMetadata.put(collectionName, CollectionMetadata.fromMap(metadata));
                return metadata;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING,
                        "Could not describe index for collection '" + collectionName + "'. Falling back to defaults.",
                        exception);
                return new LinkedHashMap<>(Map.of("distance_metric", "COSINE", "schema_version", 0));
            }
        });
    }

    String buildFilterExpr(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                parts.add(entry.getKey() + " == \"" + text + "\"");
            } else if (value instanceof Boolean bool) {
                parts.add(entry.getKey() + " == " + (bool ? "True" : "False"));
            } else if (value == null) {
                parts.add(entry.getKey() + " == None");
            } else {
                parts.add(entry.getKey() + " == " + value);
            }
        }
        return String.join(" && ", parts);
    }

    private MilvusClientAdapter clientSync() {
        if (client == null) {
            client = new DefaultMilvusClientAdapter(milvusUri, milvusToken, databaseName, clientKwargs);
            LOGGER.info("Successfully connected to AsyncMilvus");
        }
        return client;
    }

    @SuppressWarnings("unchecked")
    private CollectionSchema normalizeSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> map) {
            return CollectionSchema.fromDict((Map<String, Object>) map);
        }
        throw buildError(
                StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                "error_msg",
                "schema must be CollectionSchema or dict"
        );
    }

    private void ensureLoaded(String collectionName) {
        if (collectionsLoaded.contains(collectionName)) {
            return;
        }
        MilvusClientAdapter adapter = clientSync();
        if (adapter.hasCollection(collectionName)) {
            LOGGER.info("MilvusVectorStore: loading collection " + collectionName);
            adapter.loadCollection(collectionName);
            collectionsLoaded.add(collectionName);
        }
    }

    private void cacheVectorFieldFromDescription(String collectionName) {
        try {
            CollectionDescription description = clientSync().describeCollection(collectionName);
            for (FieldDescription field : description.fields()) {
                if (field.dtype() == VectorDataType.FLOAT_VECTOR) {
                    collectionMetadata.put(collectionName, new CollectionMetadata(null, field.name(), field.dim(), null));
                    return;
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Could not get collection metadata", exception);
        }
    }

    private List<String> outputFieldsFromSchema(String collectionName) {
        try {
            CollectionDescription description = clientSync().describeCollection(collectionName);
            List<String> fields = new ArrayList<>();
            for (FieldDescription field : description.fields()) {
                fields.add(field.name());
            }
            return fields;
        } catch (RuntimeException exception) {
            return List.of("id", "text", "metadata");
        }
    }

    private double finalScore(SearchHit hit, String distanceMetric) {
        if (hit.score() != null) {
            return hit.score();
        }
        if (hit.distance() == null) {
            return 0.0d;
        }
        double distance = hit.distance();
        if ("COSINE".equals(distanceMetric)) {
            return (distance + 1.0d) / 2.0d;
        }
        if ("L2".equals(distanceMetric)) {
            return Math.max(0.0d, (4.0d - distance) / 4.0d);
        }
        return Math.max(0.0d, Math.min(1.0d, (distance + 1.0d) / 2.0d));
    }

    private Object parseJsonString(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        try {
            return OBJECT_MAPPER.readValue(text, Object.class);
        } catch (JsonProcessingException exception) {
            return value;
        }
    }

    private int schemaVersionOrZero(String collectionName) {
        try {
            return schemaVersionOrZero(clientSync().describeCollection(collectionName));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private int schemaVersionOrZero(CollectionDescription description) {
        try {
            return Integer.parseInt(description.properties().getOrDefault("schema_version", "0"));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private void executeMigration(String collectionName, CollectionSchema newSchema,
            Function<Map<String, Object>, Map<String, Object>> transform,
            Map<String, Object> newCollectionKwargs) {
        String tempCollectionName = collectionName + "_migration_" + Instant.now().getEpochSecond();
        LOGGER.info("Starting migration for '" + collectionName + "'. New collection: '" + tempCollectionName + "'.");
        try {
            createCollection(tempCollectionName, newSchema, newCollectionKwargs).join();
            clientSync().loadCollection(collectionName);
            List<Map<String, Object>> docs = clientSync().queryAll(collectionName);
            List<Map<String, Object>> batch = new ArrayList<>();
            int batchSize = 100;
            int totalDocs = 0;
            for (Map<String, Object> doc : docs) {
                batch.add(transform.apply(new LinkedHashMap<>(doc)));
                if (batch.size() >= batchSize) {
                    addDocs(tempCollectionName, new ArrayList<>(batch), Map.of()).join();
                    totalDocs += batch.size();
                    LOGGER.fine("Migrated " + totalDocs + " documents to '" + tempCollectionName + "'.");
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                addDocs(tempCollectionName, new ArrayList<>(batch), Map.of()).join();
                totalDocs += batch.size();
            }
            LOGGER.info("Finished copying " + totalDocs + " documents to '" + tempCollectionName + "'.");
            clientSync().releaseCollection(collectionName);
            deleteCollection(collectionName, Map.of()).join();
            clientSync().renameCollection(tempCollectionName, collectionName);
            collectionMetadata.remove(collectionName);
            LOGGER.info("Migration for '" + collectionName + "' completed successfully.");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Migration for '" + collectionName
                    + "' failed. Cleaning up temporary collection.", exception);
            if (collectionExists(tempCollectionName, Map.of()).join()) {
                deleteCollection(tempCollectionName, Map.of()).join();
            }
            throw exception;
        }
    }

    private CollectionSchema computeNewSchema(CollectionSchema oldSchema, List<BaseOperation> operations) {
        CollectionSchema newSchema = CollectionSchema.fromDict(oldSchema.toDict());
        for (BaseOperation operation : operations) {
            String kind = operation.getClass().getSimpleName();
            if ("AddScalarFieldOperation".equals(kind)) {
                newSchema.addField(new FieldSchema(
                        stringProperty(operation, "fieldName", "field_name"),
                        mapStringToVectorDataType(stringProperty(operation, "fieldType", "field_type")),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        property(operation, "defaultValue", "default_value")
                ));
                continue;
            }
            if ("RenameScalarFieldOperation".equals(kind)) {
                renameField(newSchema, stringProperty(operation, "oldFieldName", "old_field_name"),
                        stringProperty(operation, "newFieldName", "new_field_name"));
                continue;
            }
            if ("UpdateScalarFieldTypeOperation".equals(kind)) {
                updateFieldType(newSchema, stringProperty(operation, "fieldName", "field_name"),
                        mapStringToVectorDataType(stringProperty(operation, "newFieldType", "new_field_type")));
                continue;
            }
            if ("UpdateEmbeddingDimensionOperation".equals(kind)) {
                updateVectorDim(newSchema, stringProperty(operation, "fieldName", "field_name"),
                        intValue(property(operation, "newDimension", "new_dimension"), 0));
                continue;
            }
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg",
                    "Unsupported operation type: " + kind
            );
        }
        return newSchema;
    }

    private Function<Map<String, Object>, Map<String, Object>> buildTransformFunctionForOperations(
            List<BaseOperation> operations) {
        return doc -> {
            Map<String, Object> transformed = new LinkedHashMap<>(doc);
            for (BaseOperation operation : operations) {
                String kind = operation.getClass().getSimpleName();
                if ("AddScalarFieldOperation".equals(kind)) {
                    String fieldName = stringProperty(operation, "fieldName", "field_name");
                    Object defaultValue = property(operation, "defaultValue", "default_value");
                    if (!transformed.containsKey(fieldName) && defaultValue != null) {
                        transformed.put(fieldName, defaultValue);
                    }
                } else if ("RenameScalarFieldOperation".equals(kind)) {
                    String oldFieldName = stringProperty(operation, "oldFieldName", "old_field_name");
                    String newFieldName = stringProperty(operation, "newFieldName", "new_field_name");
                    if (transformed.containsKey(oldFieldName)) {
                        transformed.put(newFieldName, transformed.remove(oldFieldName));
                    }
                } else if ("UpdateEmbeddingDimensionOperation".equals(kind)) {
                    String fieldName = stringProperty(operation, "fieldName", "field_name");
                    int newDimension = intValue(property(operation, "newDimension", "new_dimension"), 0);
                    transformed.put(fieldName, zeroVector(newDimension));
                }
            }
            return transformed;
        };
    }

    private void renameField(CollectionSchema schema, String oldFieldName, String newFieldName) {
        FieldSchema existing = schema.getField(oldFieldName);
        if (existing == null) {
            throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Old field '" + oldFieldName + "' does not exist");
        }
        if (schema.hasField(newFieldName)) {
            throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "New field '" + newFieldName + "' already exists");
        }
        existing.setName(newFieldName);
    }

    private void updateFieldType(CollectionSchema schema, String fieldName, VectorDataType newType) {
        FieldSchema existing = schema.getField(fieldName);
        if (existing == null) {
            throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Field '" + fieldName + "' does not exist");
        }
        if (existing.getDtype() == VectorDataType.FLOAT_VECTOR) {
            throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Cannot update type of vector field '" + fieldName + "'");
        }
        existing.setDtype(newType);
    }

    private void updateVectorDim(CollectionSchema schema, String fieldName, int newDimension) {
        FieldSchema existing = schema.getField(fieldName);
        if (existing == null) {
            throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Field '" + fieldName + "' does not exist");
        }
        if (existing.getDtype() != VectorDataType.FLOAT_VECTOR) {
            throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Field '" + fieldName + "' is not a vector field");
        }
        existing.setDim(newDimension);
    }

    private VectorDataType mapStringToVectorDataType(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT).strip()) {
            case "string", "str", "varchar" -> VectorDataType.VARCHAR;
            case "int", "integer", "int32" -> VectorDataType.INT32;
            case "int64", "long" -> VectorDataType.INT64;
            case "float", "float32" -> VectorDataType.FLOAT;
            case "double", "float64" -> VectorDataType.DOUBLE;
            case "bool", "boolean" -> VectorDataType.BOOL;
            case "json" -> VectorDataType.JSON;
            case "vector", "float_vector" -> VectorDataType.FLOAT_VECTOR;
            default -> throw buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Unknown type string: '" + type + "'");
        };
    }

    private Object property(Object target, String camelName, String snakeName) {
        for (String methodName : List.of("get" + capitalize(camelName), camelName, snakeName)) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next Python/Java accessor shape.
            }
            try {
                Method method = target.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next Python/Java accessor shape.
            }
        }
        return null;
    }

    private String stringProperty(Object target, String camelName, String snakeName) {
        Object value = property(target, camelName, snakeName);
        return value == null ? "" : String.valueOf(value);
    }

    private String capitalize(String value) {
        return value == null || value.isEmpty() ? "" : value.substring(0, 1).toUpperCase(Locale.ROOT)
                + value.substring(1);
    }

    private List<Double> zeroVector(int dimension) {
        List<Double> vector = new ArrayList<>();
        for (int index = 0; index < dimension; index++) {
            vector.add(0.0d);
        }
        return vector;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Object firstValue(Map<String, Object> kwargs, String... keys) {
        for (String key : keys) {
            if (kwargs.containsKey(key)) {
                return kwargs.get(key);
            }
        }
        return null;
    }

    private static DataType mapFieldType(VectorDataType fieldType) {
        return switch (fieldType) {
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

    private static VectorDataType mapMilvusType(DataType dataType) {
        if (dataType == null) {
            return VectorDataType.VARCHAR;
        }
        return switch (dataType) {
            case VarChar, String -> VectorDataType.VARCHAR;
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

    public interface MilvusClientAdapter extends AutoCloseable {
        boolean hasCollection(String collectionName);

        void createCollection(String collectionName, CollectionSchema schema, String distanceMetric, String indexType);

        void dropCollection(String collectionName);

        CollectionDescription describeCollection(String collectionName);

        void insert(String collectionName, List<Map<String, Object>> rows);

        void flush(String collectionName);

        List<SearchHit> search(String collectionName, List<Double> queryVector, String vectorField, int limit,
                List<String> outputFields, Map<String, Object> searchParams, String filter);

        Map<String, Object> deleteByIds(String collectionName, List<String> ids);

        Map<String, Object> deleteByFilter(String collectionName, String filter);

        void loadCollection(String collectionName);

        String describeIndexMetric(String collectionName, String vectorField);

        List<Map<String, Object>> queryAll(String collectionName);

        void releaseCollection(String collectionName);

        void renameCollection(String oldName, String newName);

        void alterCollectionProperties(String collectionName, Map<String, String> properties);

        List<String> listCollections();

        @Override
        void close();
    }

    public record CollectionDescription(String description, boolean enableDynamicField,
                                        List<FieldDescription> fields, Map<String, String> properties) {
    }

    public record FieldDescription(String name, VectorDataType dtype, boolean primary, boolean autoId,
                                   Integer maxLength, Integer dim, String description) {
    }

    public record SearchHit(Object id, Object primaryKey, Double distance, Double score, Map<String, Object> entity) {
    }

    private record CollectionMetadata(String distanceMetric, String vectorField, Integer vectorDim,
                                      Integer schemaVersion) {
        private static CollectionMetadata fromMap(Map<String, Object> map) {
            Object version = map.get("schema_version");
            return new CollectionMetadata(
                    Objects.toString(map.get("distance_metric"), null),
                    Objects.toString(map.get("vector_field"), null),
                    map.get("vector_dim") instanceof Number dim ? dim.intValue() : null,
                    version instanceof Number number ? number.intValue() : null
            );
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            if (distanceMetric != null) {
                result.put("distance_metric", distanceMetric);
            }
            if (vectorField != null) {
                result.put("vector_field", vectorField);
            }
            if (vectorDim != null) {
                result.put("vector_dim", vectorDim);
            }
            if (schemaVersion != null) {
                result.put("schema_version", schemaVersion);
            }
            return result;
        }

        private CollectionMetadata withValues(Map<String, Object> values) {
            Map<String, Object> merged = toMap();
            merged.putAll(values);
            return fromMap(merged);
        }
    }

    private static final class DefaultMilvusClientAdapter implements MilvusClientAdapter {
        private static final Gson GSON = new Gson();

        private final String databaseName;
        private final MilvusClientV2 client;

        private DefaultMilvusClientAdapter(String uri, String token, String databaseName, Map<String, Object> kwargs) {
            this.databaseName = databaseName == null || databaseName.isBlank() ? "default" : databaseName;
            long timeoutMs = Math.max(1L, Math.round(doubleValue(kwargs.get("timeout"), 3.0d) * 1000.0d));
            ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                    .uri(uri)
                    .connectTimeoutMs(timeoutMs)
                    .rpcDeadlineMs(timeoutMs)
                    .enablePrecheck(false);
            if (token != null && !token.isBlank()) {
                builder.token(token);
            }
            if (!"default".equals(this.databaseName)) {
                builder.dbName(this.databaseName);
            }
            this.client = new MilvusClientV2(builder.build());
            if (!"default".equals(this.databaseName)) {
                if (!client.listDatabases().getDatabaseNames().contains(this.databaseName)) {
                    client.createDatabase(CreateDatabaseReq.builder().databaseName(this.databaseName).build());
                }
                try {
                    client.useDatabase(this.databaseName);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while switching Milvus database", exception);
                }
            }
        }

        @Override
        public boolean hasCollection(String collectionName) {
            return client.hasCollection(HasCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
        }

        @Override
        public void createCollection(String collectionName, CollectionSchema schema, String distanceMetric,
                String indexType) {
            FieldSchema primaryField = schema.getPrimaryKeyField();
            FieldSchema vectorField = schema.getVectorFields().isEmpty() ? null : schema.getVectorFields().get(0);
            CreateCollectionReq.CollectionSchema sdkSchema = CreateCollectionReq.CollectionSchema.builder()
                    .enableDynamicField(schema.isEnableDynamicField())
                    .build();
            for (FieldSchema field : schema.getFields()) {
                AddFieldReq.AddFieldReqBuilder<?> builder = AddFieldReq.builder()
                        .fieldName(field.getName())
                        .dataType(mapFieldType(field.getDtype()))
                        .isPrimaryKey(field.isPrimary())
                        .autoID(field.isAutoId());
                if (field.getDescription() != null) {
                    builder.description(field.getDescription());
                }
                if (field.getMaxLength() != null && mapFieldType(field.getDtype()) == DataType.VarChar) {
                    builder.maxLength(field.getMaxLength());
                }
                if (field.getDim() != null && mapFieldType(field.getDtype()) == DataType.FloatVector) {
                    builder.dimension(field.getDim());
                }
                sdkSchema.addField(builder.build());
            }

            List<IndexParam> indexes = new ArrayList<>();
            for (FieldSchema field : schema.getFields()) {
                DataType dataType = mapFieldType(field.getDtype());
                if (dataType == DataType.FloatVector) {
                    indexes.add(IndexParam.builder()
                            .fieldName(field.getName())
                            .indexName(field.getName())
                            .indexType(indexType(indexType))
                            .metricType(IndexParam.MetricType.valueOf(distanceMetric))
                            .build());
                } else if (!field.isPrimary() && (dataType == DataType.VarChar
                        || dataType == DataType.Int64 || dataType == DataType.Int32)) {
                    indexes.add(IndexParam.builder()
                            .fieldName(field.getName())
                            .indexName(field.getName())
                            .indexType(IndexParam.IndexType.INVERTED)
                            .build());
                }
            }

            CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .description(schema.getDescription() == null ? "" : schema.getDescription())
                    .collectionSchema(sdkSchema)
                    .indexParams(indexes);
            if (primaryField != null) {
                builder.primaryFieldName(primaryField.getName())
                        .idType(mapFieldType(primaryField.getDtype()))
                        .autoID(primaryField.isAutoId());
            }
            if (vectorField != null) {
                builder.vectorFieldName(vectorField.getName())
                        .dimension(vectorField.getDim())
                        .metricType(distanceMetric);
            }
            client.createCollection(builder.build());
        }

        @Override
        public void dropCollection(String collectionName) {
            client.dropCollection(DropCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
        }

        @Override
        public CollectionDescription describeCollection(String collectionName) {
            DescribeCollectionResp response = client.describeCollection(DescribeCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
            List<FieldDescription> fields = new ArrayList<>();
            if (response.getCollectionSchema() != null) {
                for (CreateCollectionReq.FieldSchema field : response.getCollectionSchema().getFieldSchemaList()) {
                    fields.add(new FieldDescription(
                            field.getName(),
                            mapMilvusType(field.getDataType()),
                            Boolean.TRUE.equals(field.getIsPrimaryKey()),
                            Boolean.TRUE.equals(field.getAutoID()),
                            field.getMaxLength(),
                            field.getDimension(),
                            field.getDescription()
                    ));
                }
            }
            return new CollectionDescription(
                    response.getDescription() == null ? "" : response.getDescription(),
                    Boolean.TRUE.equals(response.getEnableDynamicField()),
                    fields,
                    response.getProperties() == null ? Map.of() : new LinkedHashMap<>(response.getProperties())
            );
        }

        @Override
        public void insert(String collectionName, List<Map<String, Object>> rows) {
            List<JsonObject> payload = rows.stream()
                    .map(row -> GSON.toJsonTree(row).getAsJsonObject())
                    .toList();
            client.insert(InsertReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .data(payload)
                    .build());
        }

        @Override
        public void flush(String collectionName) {
            client.flush(FlushReq.builder()
                    .databaseName(databaseName)
                    .collectionNames(List.of(collectionName))
                    .build());
        }

        @Override
        public List<SearchHit> search(String collectionName, List<Double> queryVector, String vectorField, int limit,
                List<String> outputFields, Map<String, Object> searchParams, String filter) {
            String metricType = Objects.toString(searchParams.getOrDefault("metric_type", "COSINE"))
                    .toUpperCase(Locale.ROOT);
            SearchReq.SearchReqBuilder builder = SearchReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .annsField(vectorField)
                    .topK(limit)
                    .outputFields(outputFields)
                    .searchParams(searchParams)
                    .metricType(IndexParam.MetricType.valueOf(metricType))
                    .data(List.of(new FloatVec(queryVector.stream().map(Double::floatValue).toList())));
            if (filter != null) {
                builder.filter(filter);
            }
            SearchResp response = client.search(builder.build());
            List<SearchHit> hits = new ArrayList<>();
            if (response.getSearchResults().isEmpty()) {
                return hits;
            }
            for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
                Map<String, Object> entity = result.getEntity() == null ? Map.of() : new LinkedHashMap<>(result.getEntity());
                hits.add(new SearchHit(result.getId(), result.getPrimaryKey(),
                        result.getScore() == null ? null : result.getScore().doubleValue(), null, entity));
            }
            return hits;
        }

        @Override
        public Map<String, Object> deleteByIds(String collectionName, List<String> ids) {
            DeleteResp response = client.delete(DeleteReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .ids(new ArrayList<>(ids))
                    .build());
            return Map.of("delete_count", response.getDeleteCnt());
        }

        @Override
        public Map<String, Object> deleteByFilter(String collectionName, String filter) {
            DeleteResp response = client.delete(DeleteReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .filter(filter)
                    .build());
            return Map.of("delete_count", response.getDeleteCnt());
        }

        @Override
        public void loadCollection(String collectionName) {
            client.loadCollection(LoadCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .sync(true)
                    .build());
        }

        @Override
        public String describeIndexMetric(String collectionName, String vectorField) {
            DescribeIndexResp response = client.describeIndex(DescribeIndexReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .indexName(vectorField)
                    .build());
            DescribeIndexResp.IndexDesc desc = response.getIndexDescByFieldName(vectorField);
            if (desc == null && !response.getIndexDescriptions().isEmpty()) {
                desc = response.getIndexDescriptions().get(0);
            }
            return desc == null || desc.getMetricType() == null ? "COSINE" : desc.getMetricType().name();
        }

        @Override
        public List<Map<String, Object>> queryAll(String collectionName) {
            QueryResp response = client.query(QueryReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .filter("")
                    .outputFields(List.of("*"))
                    .build());
            return response.getQueryResults().stream()
                    .map(QueryResp.QueryResult::getEntity)
                    .map(LinkedHashMap::new)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
        }

        @Override
        public void releaseCollection(String collectionName) {
            client.releaseCollection(ReleaseCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
        }

        @Override
        public void renameCollection(String oldName, String newName) {
            client.renameCollection(RenameCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(oldName)
                    .newCollectionName(newName)
                    .build());
        }

        @Override
        public void alterCollectionProperties(String collectionName, Map<String, String> properties) {
            client.alterCollectionProperties(AlterCollectionPropertiesReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .properties(properties)
                    .build());
        }

        @Override
        public List<String> listCollections() {
            return client.listCollections().getCollectionNames();
        }

        @Override
        public void close() {
            client.close();
        }

        private static IndexParam.IndexType indexType(String value) {
            return IndexParam.IndexType.valueOf(value.toUpperCase(Locale.ROOT));
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
    }
}
