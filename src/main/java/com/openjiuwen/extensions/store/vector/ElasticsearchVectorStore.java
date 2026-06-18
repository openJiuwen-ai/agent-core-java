/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.vector.VectorStoreUtils;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * Elasticsearch-based vector store implementation.
 *
 * <p>Mirrors Python's {@code ElasticsearchVectorStore} in
 * {@code openjiuwen/extensions/store/vector/es_vector_store.py}.</p>
 */
public class ElasticsearchVectorStore extends BaseVectorStore {
    public static final String METADATA_DOC_ID = "__collection_metadata__";

    private static final Logger LOGGER = Logger.getLogger(ElasticsearchVectorStore.class.getName());
    private static final int DEFAULT_VECTOR_DIM = 768;
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final Map<String, String> ES_SIMILARITY_MAP = Map.of(
            "COSINE", "cosine",
            "L2", "l2_norm",
            "IP", "dot_product"
    );

    private final ElasticsearchClientAdapter es;
    private final String indexPrefix;
    private final Map<String, Map<String, Object>> metadataCache = new LinkedHashMap<>();

    public ElasticsearchVectorStore(ElasticsearchClientAdapter es) {
        this(es, "agent_vector");
    }

    public ElasticsearchVectorStore(ElasticsearchClientAdapter es, String indexPrefix) {
        this.es = Objects.requireNonNull(es, "es");
        this.indexPrefix = indexPrefix == null || indexPrefix.isBlank() ? "agent_vector" : indexPrefix;
    }

    public void close() {
        try {
            es.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to close Elasticsearch connection", exception);
        }
        metadataCache.clear();
        LOGGER.info("Elasticsearch connection closed");
    }

    public String indexName(String collectionName) {
        return indexPrefix + "__" + collectionName;
    }

    @Override
    public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            String indexName = indexName(collectionName);
            Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
            String distanceMetric = Objects.toString(safeKwargs.getOrDefault("distance_metric", "COSINE"))
                    .toUpperCase(Locale.ROOT);
            CollectionSchema collectionSchema = normalizeSchema(schema);
            List<FieldSchema> vectorFields = collectionSchema.getVectorFields();
            if (vectorFields.isEmpty()) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "schema must contain at least one FLOAT_VECTOR field"
                );
            }
            FieldSchema vectorField = vectorFields.get(0);
            int vectorDim = vectorField.getDim() == null ? DEFAULT_VECTOR_DIM : vectorField.getDim();

            try {
                if (es.indices().exists(indexName)) {
                    LOGGER.info("Collection index already exists, skipping creation");
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.log(Level.FINE, "Failed to check if collection index exists", exception);
            }

            try {
                es.indices().create(indexName, Map.of("mappings", buildMappings(collectionSchema, distanceMetric)));
            } catch (RuntimeException exception) {
                if (exception.getMessage() != null
                        && exception.getMessage().contains("resource_already_exists_exception")) {
                    LOGGER.info("Collection index already exists (race)");
                    return;
                }
                LOGGER.log(Level.SEVERE, "Failed to create collection index", exception);
                throw exception;
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema", collectionSchema.toDict());
            metadata.put("distance_metric", distanceMetric);
            metadata.put("vector_field", vectorField.getName());
            metadata.put("vector_dim", vectorDim);
            metadata.put("schema_version", 0);
            metadata.put("collection_name", collectionName);
            storeMetadata(indexName, metadata);
            metadataCache.put(indexName, metadata);
            LOGGER.info("Created collection with " + collectionSchema.getFields().size() + " fields");
        });
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            String indexName = indexName(collectionName);
            try {
                if (!es.indices().exists(indexName)) {
                    LOGGER.warning("Collection does not exist");
                    return;
                }
                es.indices().delete(indexName);
                metadataCache.remove(indexName);
                LOGGER.info("Deleted collection");
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Failed to delete collection", exception);
                throw exception;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return es.indices().exists(indexName(collectionName));
            } catch (RuntimeException exception) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            String indexName = indexName(collectionName);
            Map<String, Object> metadata = loadMetadata(indexName);
            Object schema = metadata.get("schema");
            if (schema instanceof Map<?, ?> schemaMap) {
                return CollectionSchema.fromDict(castMap(schemaMap));
            }

            try {
                Map<String, Object> response = es.indices().getMapping(indexName);
                Map<String, Object> indexData = mapAt(response, indexName);
                Map<String, Object> mappings = mapAt(indexData, "mappings");
                Map<String, Object> properties = mapAt(mappings, "properties");
                List<FieldSchema> fields = new ArrayList<>();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    if ("_meta".equals(entry.getKey()) || !(entry.getValue() instanceof Map<?, ?> fieldMap)) {
                        continue;
                    }
                    Map<String, Object> fieldDef = castMap(fieldMap);
                    VectorDataType dtype = mapEsTypeToOurType(Objects.toString(fieldDef.get("type"), "keyword"));
                    Integer dim = dtype == VectorDataType.FLOAT_VECTOR ? integerOrNull(fieldDef.get("dims")) : null;
                    fields.add(new FieldSchema(entry.getKey(), dtype, false, false, null, dim, null, null, null, null));
                }

                String primaryKeyField = Objects.toString(safeKwargs(kwargs).getOrDefault("primary_key_field", "id"));
                for (FieldSchema field : fields) {
                    if (field.getName().equals(primaryKeyField)) {
                        field.setPrimary(true);
                        break;
                    }
                }
                return new CollectionSchema(fields, "Collection '" + collectionName + "'", false);
            } catch (RuntimeException exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND,
                        null,
                        null,
                        exception,
                        Map.of("collection_name", collectionName, "error_msg", exception.getMessage())
                );
            }
        });
    }

    @Override
    public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (docs == null || docs.isEmpty()) {
                return;
            }
            String indexName = indexName(collectionName);
            Map<String, Object> metadata = loadMetadata(indexName);
            String primaryKeyField = primaryKeyField(mapAt(metadata, "schema"));
            int batchSize = intValue(safeKwargs(kwargs).get("batch_size"), DEFAULT_BATCH_SIZE);
            if (batchSize <= 0) {
                batchSize = DEFAULT_BATCH_SIZE;
            }

            List<Map<String, Object>> actions = new ArrayList<>();
            for (Map<String, Object> doc : docs) {
                Object docId = primaryKeyField == null ? null : doc.get(primaryKeyField);
                Map<String, Object> source = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : doc.entrySet()) {
                    if (entry.getValue() != null) {
                        source.put(entry.getKey(), entry.getValue());
                    }
                }
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("_index", indexName);
                action.put("_source", source);
                if (docId != null) {
                    action.put("_id", String.valueOf(docId));
                }
                actions.add(action);
            }

            for (int index = 0; index < actions.size(); index += batchSize) {
                List<Map<String, Object>> chunk = actions.subList(index, Math.min(index + batchSize, actions.size()));
                BulkResponse response = es.bulk(chunk, false, false);
                if (!response.errors().isEmpty()) {
                    LOGGER.warning("Bulk insert had " + response.errors().size() + " errors");
                }
            }
            try {
                es.indices().refresh(indexName);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.FINE, "Failed to refresh index after bulk insert", exception);
            }
            LOGGER.info("Successfully added documents to collection");
        });
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> search(String collectionName, List<Double> queryVector,
            String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            String indexName = indexName(collectionName);
            Map<String, Object> metadata = loadMetadata(indexName);
            Map<String, Object> safeKwargs = safeKwargs(kwargs);
            String distanceMetric = Objects.toString(
                    safeKwargs.getOrDefault("metric_type", metadata.getOrDefault("distance_metric", "COSINE")));
            int numCandidates = intValue(safeKwargs.get("num_candidates"), Math.max(topK * 10, 100));

            Map<String, Object> knnClause = new LinkedHashMap<>();
            knnClause.put("field", vectorField);
            knnClause.put("query_vector", queryVector);
            knnClause.put("k", topK);
            knnClause.put("num_candidates", numCandidates);
            if (filters != null && !filters.isEmpty()) {
                knnClause.put("filter", boolFilter(filters));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("knn", knnClause);
            body.put("size", topK);
            if (safeKwargs.get("output_fields") != null) {
                body.put("_source", Map.of("excludes", List.of("_meta")));
            }

            Map<String, Object> response;
            try {
                response = es.search(indexName, body);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Vector search failed", exception);
                throw exception;
            }

            List<VectorSearchResult> searchResults = new ArrayList<>();
            for (Map<String, Object> hit : listOfMaps(mapAt(response, "hits").get("hits"))) {
                double score = doubleValue(hit.get("_score"), 0.0d);
                Map<String, Object> source = new LinkedHashMap<>(mapAt(hit, "_source"));
                source.remove("_meta");
                if (!source.containsKey("id") && hit.containsKey("_id")) {
                    source.put("id", hit.get("_id"));
                }
                searchResults.add(new VectorSearchResult(score, source));
            }
            return searchResults;
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (ids == null || ids.isEmpty()) {
                return;
            }
            String indexName = indexName(collectionName);
            List<Map<String, Object>> actions = new ArrayList<>();
            for (String id : ids) {
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("_op_type", "delete");
                action.put("_index", indexName);
                action.put("_id", id);
                actions.add(action);
            }
            for (int index = 0; index < actions.size(); index += DEFAULT_BATCH_SIZE) {
                List<Map<String, Object>> chunk = actions.subList(index, Math.min(index + DEFAULT_BATCH_SIZE,
                        actions.size()));
                es.bulk(chunk, true, false);
            }
            LOGGER.info("Deleted documents from collection");
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (filters == null || filters.isEmpty()) {
                return;
            }
            Map<String, Object> response = es.deleteByQuery(indexName(collectionName),
                    Map.of("query", boolFilter(filters)), true);
            LOGGER.info("Deleted documents matching filters, count=" + response.getOrDefault("deleted", 0));
        });
    }

    @Override
    public CompletableFuture<List<String>> listCollectionNames() {
        return CompletableFuture.supplyAsync(() -> {
            String prefix = indexPrefix + "__";
            Map<String, Object> response = es.indices().get(prefix + "*");
            List<String> names = new ArrayList<>();
            for (String indexName : response.keySet()) {
                if (indexName.startsWith(prefix)) {
                    names.add(indexName.substring(prefix.length()));
                }
            }
            return names;
        });
    }

    @Override
    public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
        return CompletableFuture.runAsync(() -> {
            if (operations == null || operations.isEmpty()) {
                return;
            }
            CollectionSchema oldSchema = getSchema(collectionName, Map.of()).join();
            CollectionSchema newSchema = VectorStoreUtils.computeNewSchema(oldSchema, operations);
            Function<Map<String, Object>, Map<String, Object>> transform =
                    VectorStoreUtils.buildTransformFuncForOperations(operations);
            Map<String, Object> metadata = getCollectionMetadata(collectionName).join();
            String tempCollectionName = collectionName + "_migration_" + Instant.now().getEpochSecond();
            LOGGER.info("Starting migration for '" + collectionName + "'. Temp collection: '" + tempCollectionName + "'.");

            try {
                createCollection(tempCollectionName, newSchema,
                        Map.of("distance_metric", metadata.getOrDefault("distance_metric", "COSINE"))).join();

                String indexName = indexName(collectionName);
                Map<String, Object> meta = loadMetadata(indexName);
                String primaryKeyField = Objects.toString(primaryKeyField(mapAt(meta, "schema")), "id");
                Map<String, Object> oldBody = Map.of(
                        "query", Map.of("bool", Map.of("must_not",
                                List.of(Map.of("term", Map.of("_id", METADATA_DOC_ID))))),
                        "size", 10000
                );
                List<Map<String, Object>> oldDocs = docsFromSearch(es.search(indexName, oldBody), primaryKeyField);
                if (!oldDocs.isEmpty()) {
                    List<Map<String, Object>> transformed = new ArrayList<>();
                    for (Map<String, Object> doc : oldDocs) {
                        transformed.add(new LinkedHashMap<>(transform.apply(doc)));
                    }
                    addDocs(tempCollectionName, transformed, Map.of()).join();
                }

                deleteCollection(collectionName, Map.of()).join();

                String tempIndexName = indexName(tempCollectionName);
                List<Map<String, Object>> tempDocs = docsFromSearch(es.search(tempIndexName,
                        Map.of("query", Map.of("match_all", Map.of()), "size", 10000)), primaryKeyField);

                createCollection(collectionName, newSchema,
                        Map.of("distance_metric", metadata.getOrDefault("distance_metric", "COSINE"))).join();
                if (!tempDocs.isEmpty()) {
                    addDocs(collectionName, tempDocs, Map.of()).join();
                }
                deleteCollection(tempCollectionName, Map.of()).join();
                LOGGER.info("Migration for '" + collectionName + "' completed successfully.");
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Migration for '" + collectionName + "' failed", exception);
                if (collectionExists(tempCollectionName, Map.of()).join()) {
                    deleteCollection(tempCollectionName, Map.of()).join();
                }
                throw exception;
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        return CompletableFuture.runAsync(() -> {
            if (metadata == null || metadata.isEmpty()) {
                return;
            }
            Object version = metadata.get("schema_version");
            if (version != null && (!(version instanceof Number number) || number.intValue() < 0)) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "schema_version must be a non-negative integer, got " + version
                );
            }

            String indexName = indexName(collectionName);
            Map<String, Object> current = new LinkedHashMap<>(loadMetadata(indexName));
            current.putAll(metadata);
            storeMetadata(indexName, current);
            metadataCache.put(indexName, current);
            LOGGER.fine("Updated collection metadata for '" + collectionName + "'");
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metadata = new LinkedHashMap<>(loadMetadata(indexName(collectionName)));
            metadata.putIfAbsent("distance_metric", "COSINE");
            metadata.putIfAbsent("schema_version", 0);
            return metadata;
        });
    }

    private Map<String, Object> buildMappings(CollectionSchema schema, String distanceMetric) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (FieldSchema field : schema.getFields()) {
            if (field.getDtype() == VectorDataType.FLOAT_VECTOR) {
                int dim = field.getDim() == null ? DEFAULT_VECTOR_DIM : field.getDim();
                Map<String, Object> vectorDef = new LinkedHashMap<>();
                vectorDef.put("type", "dense_vector");
                vectorDef.put("dims", dim);
                vectorDef.put("index", true);
                vectorDef.put("similarity", ES_SIMILARITY_MAP.getOrDefault(distanceMetric.toUpperCase(Locale.ROOT),
                        "cosine"));
                properties.put(field.getName(), vectorDef);
            } else {
                properties.put(field.getName(), mapEsType(field));
            }
        }
        properties.put("_meta", Map.of("type", "object", "enabled", false));
        return Map.of("dynamic", "strict", "properties", properties);
    }

    private Map<String, Object> mapEsType(FieldSchema field) {
        return switch (field.getDtype()) {
            case FLOAT_VECTOR -> Map.of("type", "dense_vector", "dims",
                    field.getDim() == null ? DEFAULT_VECTOR_DIM : field.getDim(), "index", true, "similarity",
                    "cosine");
            case VARCHAR -> Map.of("type", "keyword");
            case INT64 -> Map.of("type", "long");
            case INT32, INT16, INT8 -> Map.of("type", "integer");
            case FLOAT -> Map.of("type", "float");
            case DOUBLE -> Map.of("type", "double");
            case BOOL -> Map.of("type", "boolean");
            case JSON, ARRAY -> Map.of("type", "object", "enabled", true);
        };
    }

    private VectorDataType mapEsTypeToOurType(String esType) {
        return switch (esType.toLowerCase(Locale.ROOT)) {
            case "keyword", "text" -> VectorDataType.VARCHAR;
            case "dense_vector" -> VectorDataType.FLOAT_VECTOR;
            case "long" -> VectorDataType.INT64;
            case "integer" -> VectorDataType.INT32;
            case "short" -> VectorDataType.INT16;
            case "byte" -> VectorDataType.INT8;
            case "float" -> VectorDataType.FLOAT;
            case "double" -> VectorDataType.DOUBLE;
            case "boolean" -> VectorDataType.BOOL;
            case "object" -> VectorDataType.JSON;
            default -> {
                LOGGER.warning("Unsupported ES data type: " + esType + ", defaulting to VARCHAR");
                yield VectorDataType.VARCHAR;
            }
        };
    }

    private void storeMetadata(String indexName, Map<String, Object> metadata) {
        try {
            es.index(indexName, METADATA_DOC_ID, Map.of("_meta", new LinkedHashMap<>(metadata)), true);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to store collection metadata", exception);
        }
    }

    private Map<String, Object> loadMetadata(String indexName) {
        if (metadataCache.containsKey(indexName)) {
            return metadataCache.get(indexName);
        }
        try {
            Map<String, Object> response = es.get(indexName, METADATA_DOC_ID);
            Object found = response.get("found");
            if (!Boolean.FALSE.equals(found)) {
                Map<String, Object> source = mapAt(response, "_source");
                Map<String, Object> metadata = mapAt(source, "_meta");
                metadataCache.put(indexName, metadata);
                return metadata;
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to load collection metadata", exception);
        }
        return new LinkedHashMap<>();
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

    private Map<String, Object> boolFilter(Map<String, Object> filters) {
        List<Map<String, Object>> must = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Map<String, Object> clause = new LinkedHashMap<>();
            if (entry.getValue() instanceof Collection<?> collection) {
                clause.put("terms", Map.of(entry.getKey(), new ArrayList<>(collection)));
            } else {
                clause.put("term", Map.of(entry.getKey(), entry.getValue()));
            }
            must.add(clause);
        }
        return Map.of("bool", Map.of("filter", must));
    }

    private List<Map<String, Object>> docsFromSearch(Map<String, Object> response, String primaryKeyField) {
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Map<String, Object> hit : listOfMaps(mapAt(response, "hits").get("hits"))) {
            Map<String, Object> source = new LinkedHashMap<>(mapAt(hit, "_source"));
            source.remove("_meta");
            if (!source.containsKey(primaryKeyField) && hit.containsKey("_id")) {
                source.put(primaryKeyField, hit.get("_id"));
            }
            docs.add(source);
        }
        return docs;
    }

    private String primaryKeyField(Map<String, Object> schemaDict) {
        Object fields = schemaDict.get("fields");
        if (fields instanceof List<?> fieldList) {
            for (Object field : fieldList) {
                if (field instanceof Map<?, ?> fieldMap && Boolean.TRUE.equals(fieldMap.get("is_primary"))) {
                    Object name = fieldMap.get("name");
                    return name == null ? null : String.valueOf(name);
                }
            }
        }
        return null;
    }

    private Map<String, Object> safeKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? Map.of() : kwargs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapAt(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
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

    private Integer integerOrNull(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
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

    public interface ElasticsearchClientAdapter extends AutoCloseable {
        ElasticsearchIndicesAdapter indices();

        Map<String, Object> get(String index, String id);

        void index(String index, String id, Map<String, Object> body, boolean refresh);

        BulkResponse bulk(List<Map<String, Object>> actions, boolean refresh, boolean raiseOnError);

        Map<String, Object> search(String index, Map<String, Object> body);

        Map<String, Object> deleteByQuery(String index, Map<String, Object> body, boolean refresh);

        @Override
        void close();
    }

    public interface ElasticsearchIndicesAdapter {
        boolean exists(String index);

        void create(String index, Map<String, Object> body);

        void delete(String index);

        Map<String, Object> getMapping(String index);

        void refresh(String index);

        Map<String, Object> get(String indexPattern);
    }

    public record BulkResponse(int success, List<Object> errors) {
    }
}
