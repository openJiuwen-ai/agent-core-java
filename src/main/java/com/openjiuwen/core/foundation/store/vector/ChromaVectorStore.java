/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * ChromaDB vector store implementation.
 *
 * <p>Mirrors Python's {@code ChromaVectorStore} in
 * {@code openjiuwen/core/foundation/store/vector/chroma_vector_store.py}.</p>
 */
public class ChromaVectorStore extends BaseVectorStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Logger LOGGER = Logger.getLogger(ChromaVectorStore.class.getName());

    private final ChromaClientAdapter client;
    private final Map<String, ChromaCollectionAdapter> collections = new LinkedHashMap<>();

    public ChromaVectorStore() {
        this((String) null);
    }

    public ChromaVectorStore(String persistDirectory) {
        this(new InMemoryChromaClientAdapter(persistDirectory));
    }

    public ChromaVectorStore(Map<String, Object> kwargs) {
        this(kwargs == null ? null : Objects.toString(kwargs.get("persist_directory"), null));
    }

    public ChromaVectorStore(ChromaClientAdapter client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
            String distanceMetric = Objects.toString(safeKwargs.getOrDefault("distance_metric", "cosine"));
            String chromaMetric = distanceMetric.replace("dot", "ip").replace("euclidean", "l2");
            CollectionSchema collectionSchema = normalizeSchema(schema);
            FieldSchema primaryField = collectionSchema.getPrimaryKeyField();
            if (primaryField == null) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "schema must contain a primary key field (is_primary=True)"
                );
            }
            List<FieldSchema> vectorFields = collectionSchema.getVectorFields();
            if (vectorFields.isEmpty()) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "schema must contain at least one FLOAT_VECTOR field"
                );
            }
            FieldSchema vectorField = vectorFields.get(0);
            Map<String, Object> fieldMapping = buildFieldMapping(collectionSchema, primaryField, vectorField);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema", writeJson(collectionSchema.toDict()));
            metadata.put("fields", writeJson(collectionSchema.toDict()));
            metadata.put("field_mapping", writeJson(fieldMapping));
            metadata.put("vector_field", vectorField.getName());
            metadata.put("distance_metric", chromaMetric);

            Map<String, Object> configuration = Map.of("hnsw", Map.of("space", chromaMetric));
            ChromaCollectionAdapter collection = client.getOrCreateCollection(collectionName, metadata, configuration);
            collections.put(collectionName, collection);
        });
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            client.deleteCollection(collectionName);
            collections.remove(collectionName);
        });
    }

    @Override
    public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                client.getCollection(collectionName);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            ChromaCollectionAdapter collection = getCollection(collectionName);
            Map<String, Object> metadata = collection.metadata();
            try {
                if (metadata.containsKey("schema")) {
                    return CollectionSchema.fromDict(readMap(String.valueOf(metadata.get("schema"))));
                }
                if (metadata.containsKey("fields")) {
                    return CollectionSchema.fromDict(readMap(String.valueOf(metadata.get("fields"))));
                }
                return defaultSchema(collectionName, metadata);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Could not get schema from collection " + collectionName, exception);
                return defaultSchema(collectionName, Map.of());
            }
        });
    }

    @Override
    public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
            int batchSize = intValue(safeKwargs.get("batch_size"), 128);
            if (batchSize <= 0) {
                batchSize = 128;
            }
            ChromaCollectionAdapter collection = getCollection(collectionName);
            FieldMapping fieldMapping = fieldMapping(collection);

            int total = docs == null ? 0 : docs.size();
            for (int index = 0; index < total; index += batchSize) {
                List<Map<String, Object>> batch = docs.subList(index, Math.min(index + batchSize, total));
                addBatch(collection, fieldMapping, batch);
            }
        });
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> search(String collectionName, List<Double> queryVector,
            String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            ChromaCollectionAdapter collection = getCollection(collectionName);
            Map<String, Object> metadata = collection.metadata();
            FieldMapping fieldMapping = fieldMapping(collection);
            Map<String, Object> result = collection.query(queryVector, topK, filters);
            return buildSearchResults(result, metadata, fieldMapping);
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> getCollection(collectionName).deleteByIds(ids));
    }

    @Override
    public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> getCollection(collectionName).deleteByWhere(filters));
    }

    @Override
    public CompletableFuture<List<String>> listCollectionNames() {
        return CompletableFuture.supplyAsync(client::listCollectionNames);
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
            if (metadata != null && metadata.containsKey("schema_version")) {
                Object version = metadata.get("schema_version");
                if (!(version instanceof Number number) || number.intValue() < 0) {
                    throw buildError(
                            StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                            "error_msg",
                            "schema_version must be a non-negative integer, got " + version
                    );
                }
            }
            ChromaCollectionAdapter collection = getCollection(collectionName);
            Map<String, Object> currentMetadata = new LinkedHashMap<>(collection.metadata());
            if (metadata != null) {
                currentMetadata.putAll(metadata);
            }
            collection.modify(collectionName, currentMetadata);
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metadata = new LinkedHashMap<>(getCollection(collectionName).metadata());
            metadata.putIfAbsent("distance_metric", "cosine");
            metadata.putIfAbsent("schema_version", 0);
            return metadata;
        });
    }

    public CompletableFuture<List<Map<String, Object>>> getAllDocuments(String collectionName) {
        return CompletableFuture.supplyAsync(() -> {
            ChromaCollectionAdapter collection = getCollection(collectionName);
            FieldMapping fieldMapping = fieldMapping(collection);
            Map<String, Object> result = collection.get(List.of("documents", "metadatas", "embeddings", "uris"));
            List<String> ids = stringList(result.get("ids"));
            List<String> documents = stringList(result.get("documents"));
            List<Map<String, Object>> metadatas = mapList(result.get("metadatas"));
            List<List<Double>> embeddings = doubleListList(result.get("embeddings"));

            List<Map<String, Object>> output = new ArrayList<>();
            for (int index = 0; index < ids.size(); index++) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put(fieldMapping.primaryKey(), ids.get(index));
                doc.put(fieldMapping.textField(), index < documents.size() ? documents.get(index) : "");
                doc.put(fieldMapping.vectorField(), index < embeddings.size() ? embeddings.get(index) : List.of());
                if (index < metadatas.size() && metadatas.get(index) != null) {
                    doc.putAll(metadatas.get(index));
                }
                output.add(doc);
            }
            return output;
        });
    }

    private ChromaCollectionAdapter getCollection(String collectionName) {
        ChromaCollectionAdapter cached = collections.get(collectionName);
        if (cached != null) {
            return cached;
        }
        try {
            ChromaCollectionAdapter collection = client.getCollection(collectionName);
            collections.put(collectionName, collection);
            return collection;
        } catch (RuntimeException exception) {
            throw buildError(
                    StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND,
                    "collection_name",
                    collectionName,
                    "error_msg",
                    "collection doesn't exist"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private CollectionSchema normalizeSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> schemaMap) {
            return CollectionSchema.fromDict((Map<String, Object>) schemaMap);
        }
        throw buildError(
                StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                "error_msg",
                "schema must be CollectionSchema or dict"
        );
    }

    private Map<String, Object> buildFieldMapping(CollectionSchema schema, FieldSchema primaryField,
            FieldSchema vectorField) {
        Map<String, Object> fieldMapping = new LinkedHashMap<>();
        fieldMapping.put("primary_key", primaryField.getName());
        fieldMapping.put("vector_field", vectorField.getName());
        fieldMapping.put("text_field", null);
        for (FieldSchema field : schema.getFields()) {
            if (field.getDtype() == VectorDataType.VARCHAR && !field.isPrimary()) {
                fieldMapping.put("text_field", field.getName());
                break;
            }
        }
        return fieldMapping;
    }

    private void addBatch(ChromaCollectionAdapter collection, FieldMapping fieldMapping, List<Map<String, Object>> batch) {
        List<String> ids = new ArrayList<>();
        List<List<Double>> embeddings = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        boolean hasMetadata = true;

        for (Map<String, Object> doc : batch) {
            Object rawId = doc.get(fieldMapping.primaryKey());
            if (rawId == null) {
                throw buildError(
                        StatusCode.STORE_VECTOR_DOC_INVALID,
                        "error_msg",
                        "document must have primary field '" + fieldMapping.primaryKey() + "'"
                );
            }
            ids.add(String.valueOf(rawId));

            Object rawEmbedding = doc.get(fieldMapping.vectorField());
            if (rawEmbedding == null) {
                throw buildError(
                        StatusCode.STORE_VECTOR_DOC_INVALID,
                        "error_msg",
                        "document must have vector field '" + fieldMapping.vectorField() + "'"
                );
            }
            embeddings.add(doubleList(rawEmbedding));

            Object rawText = doc.getOrDefault(fieldMapping.textField(), "");
            documents.add(rawText == null ? "" : String.valueOf(rawText));

            Map<String, Object> metadata = new LinkedHashMap<>();
            Set<String> builtInFields = new LinkedHashSet<>();
            builtInFields.add(fieldMapping.primaryKey());
            builtInFields.add(fieldMapping.vectorField());
            if (fieldMapping.textField() != null) {
                builtInFields.add(fieldMapping.textField());
            }
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                if (builtInFields.contains(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (isPrimitiveMetadata(value)) {
                    metadata.put(entry.getKey(), value);
                } else if (value instanceof List<?> || value instanceof Map<?, ?>) {
                    metadata.put(entry.getKey(), writeJson(value));
                } else {
                    metadata.put(entry.getKey(), String.valueOf(value));
                }
            }
            if (metadata.isEmpty()) {
                hasMetadata = false;
            }
            metadatas.add(metadata);
        }

        collection.add(ids, embeddings, documents, hasMetadata ? metadatas : null);
    }

    private List<VectorSearchResult> buildSearchResults(Map<String, Object> results, Map<String, Object> metadata,
            FieldMapping fieldMapping) {
        List<List<String>> idsNested = stringListList(results.get("ids"));
        if (idsNested.isEmpty()) {
            return List.of();
        }
        List<String> ids = idsNested.get(0);
        List<List<String>> documentsNested = stringListList(results.get("documents"));
        List<String> documents = documentsNested.isEmpty() ? List.of() : documentsNested.get(0);
        List<List<Map<String, Object>>> metadatasNested = mapListList(results.get("metadatas"));
        List<Map<String, Object>> metadatas = metadatasNested.isEmpty() ? List.of() : metadatasNested.get(0);
        List<List<Double>> distancesNested = doubleListListNested(results.get("distances"));
        List<Double> distances = distancesNested.isEmpty() ? List.of() : distancesNested.get(0);
        String metric = Objects.toString(metadata.getOrDefault("distance_metric", "cosine"));

        List<VectorSearchResult> searchResults = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            double score = 0.0d;
            if (index < distances.size()) {
                score = convertDistance(metric, distances.get(index));
            }
            Map<String, Object> rawMetadata = index < metadatas.size() && metadatas.get(index) != null
                    ? metadatas.get(index)
                    : Map.of();
            Map<String, Object> parsedMetadata = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawMetadata.entrySet()) {
                parsedMetadata.put(entry.getKey(), parseMetadataValue(entry.getValue()));
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put(fieldMapping.primaryKey(), ids.get(index));
            fields.putAll(parsedMetadata);
            if (fieldMapping.textField() != null) {
                fields.put(fieldMapping.textField(), index < documents.size() ? documents.get(index) : "");
            }
            searchResults.add(new VectorSearchResult(score, fields));
        }
        return searchResults;
    }

    private FieldMapping fieldMapping(ChromaCollectionAdapter collection) {
        Map<String, Object> metadata = collection.metadata();
        Map<String, Object> mapping = readMap(Objects.toString(metadata.getOrDefault("field_mapping", "{}")));
        return new FieldMapping(
                Objects.toString(mapping.getOrDefault("primary_key", "id")),
                Objects.toString(mapping.getOrDefault("vector_field", "embedding")),
                mapping.get("text_field") == null ? null : String.valueOf(mapping.get("text_field"))
        );
    }

    private CollectionSchema defaultSchema(String collectionName, Map<String, Object> metadata) {
        Map<String, Object> mapping = readMap(Objects.toString(metadata.getOrDefault("field_mapping", "{}")));
        String primaryKey = Objects.toString(mapping.getOrDefault("primary_key", "id"));
        String vectorField = Objects.toString(mapping.getOrDefault("vector_field", "embedding"));
        String textField = Objects.toString(mapping.getOrDefault("text_field", "text"));

        CollectionSchema schema = new CollectionSchema(List.of(), "Collection '" + collectionName + "'", true);
        schema.addField(new FieldSchema(primaryKey, VectorDataType.VARCHAR, true, false, 256, null,
                null, null, null, null));
        schema.addField(new FieldSchema(vectorField, VectorDataType.FLOAT_VECTOR, false, false, 65535, 1,
                null, null, null, null));
        schema.addField(new FieldSchema(textField, VectorDataType.VARCHAR, false, false, 65535, null,
                null, null, null, null));
        schema.addField(new FieldSchema("metadata", VectorDataType.JSON, false, false, null, null,
                null, null, null, null));
        return schema;
    }

    private void executeMigration(String collectionName, CollectionSchema newSchema,
            Function<Map<String, Object>, Map<String, Object>> transformFunc, Map<String, Object> newCollectionKwargs) {
        String tempCollectionName = collectionName + "_migration_" + Instant.now().getEpochSecond();
        try {
            createCollection(tempCollectionName, newSchema,
                    Map.of("distance_metric", newCollectionKwargs.getOrDefault("distance_metric", "cosine"))).join();
            List<Map<String, Object>> oldData = getAllDocuments(collectionName).join();
            if (!oldData.isEmpty()) {
                List<Map<String, Object>> transformed = new ArrayList<>();
                for (Map<String, Object> doc : oldData) {
                    transformed.add(transformFunc.apply(new LinkedHashMap<>(doc)));
                }
                addDocs(tempCollectionName, transformed, Map.of()).join();
            }
            deleteCollection(collectionName, Map.of()).join();
            List<Map<String, Object>> tempData = getAllDocuments(tempCollectionName).join();
            createCollection(collectionName, newSchema,
                    Map.of("distance_metric", newCollectionKwargs.getOrDefault("distance_metric", "cosine"))).join();
            if (!tempData.isEmpty()) {
                addDocs(collectionName, tempData, Map.of()).join();
            }
            deleteCollection(tempCollectionName, Map.of()).join();
        } catch (RuntimeException exception) {
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
        return switch (type == null ? "" : type.toLowerCase().strip()) {
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
        return value == null || value.isEmpty() ? "" : value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private List<Double> zeroVector(int dimension) {
        List<Double> vector = new ArrayList<>();
        for (int index = 0; index < dimension; index++) {
            vector.add(0.0d);
        }
        return vector;
    }

    private double convertDistance(String metric, double distance) {
        return switch (metric) {
            case "l2" -> Math.max(0.0d, (4.0d - distance) / 4.0d);
            case "ip" -> Math.max(0.0d, Math.min(1.0d, (2.0d - distance) / 2.0d));
            default -> (2.0d - distance) / 2.0d;
        };
    }

    private boolean isPrimitiveMetadata(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private Object parseMetadataValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        try {
            return OBJECT_MAPPER.readValue(text, Object.class);
        } catch (JsonProcessingException exception) {
            return value;
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to write JSON", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
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

    private List<Double> doubleList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            }
        }
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private List<List<String>> stringListList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<List<String>> result = new ArrayList<>();
        for (Object item : rawList) {
            result.add(stringList(item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private List<List<Map<String, Object>>> mapListList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<List<Map<String, Object>>> result = new ArrayList<>();
        for (Object item : rawList) {
            result.add(mapList(item));
        }
        return result;
    }

    private List<List<Double>> doubleListList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<List<Double>> result = new ArrayList<>();
        for (Object item : rawList) {
            result.add(doubleList(item));
        }
        return result;
    }

    private List<List<Double>> doubleListListNested(Object value) {
        return doubleListList(value);
    }

    private record FieldMapping(String primaryKey, String vectorField, String textField) {
    }

    public interface ChromaClientAdapter {
        ChromaCollectionAdapter getCollection(String name);

        ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> metadata,
                Map<String, Object> configuration);

        void deleteCollection(String name);

        List<String> listCollectionNames();
    }

    public interface ChromaCollectionAdapter {
        String name();

        Map<String, Object> metadata();

        void add(List<String> ids, List<List<Double>> embeddings, List<String> documents,
                List<Map<String, Object>> metadatas);

        Map<String, Object> query(List<Double> queryEmbedding, int nResults, Map<String, Object> where);

        void deleteByIds(List<String> ids);

        void deleteByWhere(Map<String, Object> where);

        Map<String, Object> get(List<String> include);

        void modify(String name, Map<String, Object> metadata);
    }

    static final class InMemoryChromaClientAdapter implements ChromaClientAdapter {
        private final Map<String, InMemoryChromaCollectionAdapter> collections = new LinkedHashMap<>();

        InMemoryChromaClientAdapter(String persistDirectory) {
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
        public ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> metadata,
                Map<String, Object> configuration) {
            return collections.computeIfAbsent(name,
                    key -> new InMemoryChromaCollectionAdapter(key, new LinkedHashMap<>(metadata)));
        }

        @Override
        public void deleteCollection(String name) {
            collections.remove(name);
        }

        @Override
        public List<String> listCollectionNames() {
            return new ArrayList<>(collections.keySet());
        }
    }

    static final class InMemoryChromaCollectionAdapter implements ChromaCollectionAdapter {
        private String name;
        private Map<String, Object> metadata;
        private final Map<String, StoredDocument> documentsById = new LinkedHashMap<>();

        InMemoryChromaCollectionAdapter(String name, Map<String, Object> metadata) {
            this.name = name;
            this.metadata = metadata;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Map<String, Object> metadata() {
            return metadata;
        }

        @Override
        public void add(List<String> ids, List<List<Double>> embeddings, List<String> documents,
                List<Map<String, Object>> metadatas) {
            for (int index = 0; index < ids.size(); index++) {
                Map<String, Object> metadataValue = metadatas == null ? new LinkedHashMap<>() : metadatas.get(index);
                documentsById.put(ids.get(index), new StoredDocument(
                        ids.get(index),
                        embeddings.get(index),
                        index < documents.size() ? documents.get(index) : "",
                        new LinkedHashMap<>(metadataValue)
                ));
            }
        }

        @Override
        public Map<String, Object> query(List<Double> queryEmbedding, int nResults, Map<String, Object> where) {
            String metric = Objects.toString(metadata.getOrDefault("distance_metric", "cosine"));
            List<StoredDocument> matchingDocs = documentsById.values().stream()
                    .filter(document -> matchesWhere(document.metadata(), where))
                    .sorted(Comparator.comparingDouble(document -> distance(metric, queryEmbedding, document.embedding())))
                    .limit(nResults)
                    .toList();

            List<String> ids = new ArrayList<>();
            List<String> documents = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            for (StoredDocument doc : matchingDocs) {
                ids.add(doc.id());
                documents.add(doc.document());
                metadatas.add(doc.metadata());
                distances.add(distance(metric, queryEmbedding, doc.embedding()));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ids", List.of(ids));
            result.put("documents", List.of(documents));
            result.put("metadatas", List.of(metadatas));
            result.put("distances", List.of(distances));
            return result;
        }

        @Override
        public void deleteByIds(List<String> ids) {
            if (ids == null) {
                return;
            }
            for (String id : ids) {
                documentsById.remove(id);
            }
        }

        @Override
        public void deleteByWhere(Map<String, Object> where) {
            List<String> deleteIds = documentsById.values().stream()
                    .filter(document -> matchesWhere(document.metadata(), where))
                    .map(StoredDocument::id)
                    .toList();
            deleteByIds(deleteIds);
        }

        @Override
        public Map<String, Object> get(List<String> include) {
            List<String> ids = new ArrayList<>();
            List<String> documents = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            List<List<Double>> embeddings = new ArrayList<>();
            for (StoredDocument doc : documentsById.values()) {
                ids.add(doc.id());
                documents.add(doc.document());
                metadatas.add(new LinkedHashMap<>(doc.metadata()));
                embeddings.add(new ArrayList<>(doc.embedding()));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ids", ids);
            result.put("documents", documents);
            result.put("metadatas", metadatas);
            result.put("embeddings", embeddings);
            return result;
        }

        @Override
        public void modify(String name, Map<String, Object> metadata) {
            this.name = name;
            this.metadata = new LinkedHashMap<>(metadata);
        }

        private boolean matchesWhere(Map<String, Object> metadata, Map<String, Object> where) {
            if (where == null || where.isEmpty()) {
                return true;
            }
            for (Map.Entry<String, Object> entry : where.entrySet()) {
                if (!Objects.equals(metadata.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private double distance(String metric, List<Double> query, List<Double> embedding) {
            if ("l2".equals(metric)) {
                double sum = 0.0d;
                for (int index = 0; index < Math.min(query.size(), embedding.size()); index++) {
                    double delta = query.get(index) - embedding.get(index);
                    sum += delta * delta;
                }
                return sum;
            }
            double dot = 0.0d;
            double queryNorm = 0.0d;
            double embeddingNorm = 0.0d;
            for (int index = 0; index < Math.min(query.size(), embedding.size()); index++) {
                double q = query.get(index);
                double e = embedding.get(index);
                dot += q * e;
                queryNorm += q * q;
                embeddingNorm += e * e;
            }
            if ("ip".equals(metric)) {
                return 1.0d - dot;
            }
            if (queryNorm == 0.0d || embeddingNorm == 0.0d) {
                return 2.0d;
            }
            return 1.0d - dot / (Math.sqrt(queryNorm) * Math.sqrt(embeddingNorm));
        }

        private record StoredDocument(String id, List<Double> embedding, String document,
                                      Map<String, Object> metadata) {
        }
    }
}
