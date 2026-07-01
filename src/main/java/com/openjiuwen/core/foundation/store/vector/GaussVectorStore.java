/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * GaussVector store implementation.
 *
 * <p>Mirrors Python's {@code GaussVectorStore} in
 * {@code openjiuwen/core/foundation/store/vector/gauss_vector_store.py}.</p>
 */
public class GaussVectorStore extends BaseVectorStore implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final java.util.concurrent.Executor IO_EXECUTOR = VirtualThreadSupport.newThreadPerTaskExecutor("gauss-vector-store-io");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Logger LOGGER = Logger.getLogger(GaussVectorStore.class.getName());
    private static final Pattern VARCHAR_PATTERN = Pattern.compile("varchar\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT_VECTOR_PATTERN =
            Pattern.compile("floatvector\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final Map<String, Object> kwargs;
    private final SqlConnectionFactory connectionFactory;
    private final Map<String, Map<String, Object>> collectionMetadata = new LinkedHashMap<>();

    private SqlConnection connection;

    public GaussVectorStore() {
        this("localhost", 5432, "postgres", "postgres", "", Map.of());
    }

    public GaussVectorStore(Map<String, Object> kwargs) {
        this(
                stringValue(kwargs, "host", "localhost"),
                intValue(kwargs == null ? null : kwargs.get("port"), 5432),
                stringValue(kwargs, "database", "postgres"),
                stringValue(kwargs, "user", "postgres"),
                stringValue(kwargs, "password", ""),
                extraKwargs(kwargs)
        );
    }

    public GaussVectorStore(String host, int port, String database, String user, String password,
            Map<String, Object> kwargs) {
        this(host, port, database, user, password, kwargs, GaussVectorStore::createJdbcConnection);
    }

    GaussVectorStore(String host, int port, String database, String user, String password,
            Map<String, Object> kwargs, SqlConnectionFactory connectionFactory) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
        this.kwargs = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    SqlConnection connection() {
        if (connection == null) {
            connection = connectionFactory.connect(host, port, database, user, password, kwargs);
            connection.setAutoCommit(true);
            LOGGER.info("Successfully connected to GaussVector");
        }
        return connection;
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
            connection = null;
            LOGGER.info("GaussVector connection closed");
        }
    }

    @Override
    public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            Map<String, Object> safeKwargs = safeMap(kwargs);
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(
                        "SELECT EXISTS (SELECT table_name FROM information_schema.tables WHERE table_name = ?);",
                        collectionName
                );
                Object exists = firstValue(cursor.fetchOne());
                if (Boolean.TRUE.equals(exists)) {
                    LOGGER.info("Collection already exists, skipping creation: " + collectionName);
                    return;
                }

                String distanceMetric = stringValue(safeKwargs, "distance_metric", "cosine").toUpperCase();
                String indexType = stringValue(safeKwargs, "index_type", "diskann").toLowerCase();
                CollectionSchema collectionSchema = normalizeSchema(schema);
                String vectorFieldName = null;
                Integer vectorDim = null;
                List<String> columns = new ArrayList<>();

                for (FieldSchema field : collectionSchema.getFields()) {
                    String colName = field.getName();
                    String colType = mapFieldTypeToPg(field.getDtype());
                    if (field.getDtype() == VectorDataType.FLOAT_VECTOR) {
                        vectorFieldName = field.getName();
                        vectorDim = field.getDim();
                        if (vectorDim == null || vectorDim <= 0) {
                            throw buildError(
                                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                                    "error_msg",
                                    "dim of vector field is missing, field=" + colName + ", dim=" + vectorDim
                            );
                        }
                        colType = "floatvector(" + vectorDim + ")";
                    }

                    if (field.isPrimary()) {
                        if (field.isAutoId()) {
                            columns.add(colName + " SERIAL PRIMARY KEY");
                        } else {
                            columns.add(colName + " " + colType + " PRIMARY KEY");
                        }
                    } else if (field.getDtype() == VectorDataType.VARCHAR) {
                        int maxLength = field.getMaxLength() == null ? 65535 : field.getMaxLength();
                        columns.add(colName + " VARCHAR(" + maxLength + ")");
                    } else {
                        columns.add(colName + " " + colType);
                    }
                }

                if (vectorFieldName == null) {
                    throw buildError(
                            StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                            "error_msg",
                            "schema must contain at least one FLOAT_VECTOR field"
                    );
                }

                cursor.execute("CREATE TABLE " + collectionName + " (" + String.join(", ", columns) + ");");
                cursor.execute(buildIndexSql(collectionName, vectorFieldName, distanceMetric, indexType, safeKwargs));
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("distance_metric", distanceMetric);
                metadata.put("vector_field", vectorFieldName);
                metadata.put("vector_dim", vectorDim);
                collectionMetadata.put(collectionName, metadata);
                LOGGER.info("Created collection with " + collectionSchema.getFields().size() + " fields");
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Failed to create collection: " + exception.getMessage())
                );
            } finally {
                cursor.close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(
                        "SELECT EXISTS (SELECT table_name FROM information_schema.tables WHERE table_name = ?);",
                        collectionName
                );
                Object exists = firstValue(cursor.fetchOne());
                if (!Boolean.TRUE.equals(exists)) {
                    LOGGER.warning("Collection does not exist: " + collectionName);
                    return;
                }
                cursor.execute("DROP TABLE IF EXISTS " + collectionName + " CASCADE;");
                collectionMetadata.remove(collectionName);
                LOGGER.info("Deleted collection: " + collectionName);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Failed to delete collection: " + exception.getMessage())
                );
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(
                        "SELECT EXISTS (SELECT table_name FROM information_schema.tables WHERE table_name = ?);",
                        collectionName
                );
                return Boolean.TRUE.equals(firstValue(cursor.fetchOne()));
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(
                        "SELECT EXISTS (SELECT table_name FROM information_schema.tables WHERE table_name = ?);",
                        collectionName
                );
                if (!Boolean.TRUE.equals(firstValue(cursor.fetchOne()))) {
                    throw buildError(
                            StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND,
                            "collection_name",
                            collectionName
                    );
                }

                cursor.execute("""
                        SELECT column_name, data_type, is_nullable, column_default
                        FROM information_schema.columns
                        WHERE table_name = ?
                        ORDER BY ordinal_position;
                        """, collectionName);
                List<List<Object>> columns = cursor.fetchAll();

                cursor.execute("""
                        SELECT kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                        WHERE tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY';
                        """, collectionName);
                List<String> primaryKeys = cursor.fetchAll().stream()
                        .map(row -> String.valueOf(row.get(0)))
                        .toList();

                CollectionSchema schema = new CollectionSchema(List.of(), "Table " + collectionName, false);
                for (List<Object> column : columns) {
                    String colName = String.valueOf(column.get(0));
                    String dataType = String.valueOf(column.get(1));
                    boolean primary = primaryKeys.contains(colName);
                    Integer maxLength = varcharLength(dataType);
                    Integer dim = floatVectorDim(dataType);
                    schema.addField(new FieldSchema(
                            colName,
                            mapPgTypeToOurType(dataType),
                            primary,
                            false,
                            maxLength,
                            dim,
                            null,
                            null,
                            null,
                            null
                    ));
                }
                return schema;
            } finally {
                cursor.close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            Map<String, Object> safeKwargs = safeMap(kwargs);
            int batchSize = intValue(safeKwargs.get("batch_size"), 128);
            if (batchSize <= 0) {
                batchSize = 128;
            }
            if (docs == null || docs.isEmpty()) {
                return;
            }
            List<String> columns = new ArrayList<>(docs.get(0).keySet());
            if (columns.isEmpty()) {
                return;
            }
            String placeholders = String.join(", ", repeat("?", columns.size()));
            String insertSql = "INSERT INTO " + collectionName + " (" + String.join(", ", columns)
                    + ") VALUES (" + placeholders + ")";

            SqlCursor cursor = connection().cursor();
            try {
                int processed = 0;
                for (int index = 0; index < docs.size(); index += batchSize) {
                    List<Map<String, Object>> batch = docs.subList(index, Math.min(index + batchSize, docs.size()));
                    List<List<Object>> valuesList = new ArrayList<>();
                    for (Map<String, Object> doc : batch) {
                        List<Object> rowValues = new ArrayList<>();
                        for (String column : columns) {
                            Object value = doc.get(column);
                            if (value instanceof Map<?, ?>) {
                                value = writeJson(value);
                            } else if (value instanceof List<?> && !column.equals(columns.get(0))) {
                                value = writeJson(value);
                            }
                            rowValues.add(value);
                        }
                        valuesList.add(rowValues);
                    }
                    cursor.executeMany(insertSql, valuesList);
                    processed += batch.size();
                    if (processed % 100 == 0) {
                        LOGGER.info("Added " + processed + "/" + docs.size() + " documents to collection");
                    }
                }
                LOGGER.info("Successfully added documents to collection: " + docs.size());
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_DOC_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Failed to add documents: " + exception.getMessage())
                );
            } finally {
                cursor.close();
            }
        });
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> search(String collectionName, List<Double> queryVector,
            String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> collectionMeta = collectionMetadata.getOrDefault(collectionName, Map.of());
            String distanceMetric = String.valueOf(
                    safeMap(kwargs).getOrDefault("metric_type", collectionMeta.getOrDefault("distance_metric", "COSINE"))
            );
            String whereClause = "";
            String filterClause = buildFilterClause(filters);
            if (filterClause != null && !filterClause.isBlank()) {
                whereClause = "WHERE " + filterClause;
            }
            Object outputFields = safeMap(kwargs).get("output_fields");
            String selectFields = selectFields(outputFields, vectorField);
            String searchSql = """
                    SELECT %s,
                           %s <-> '%s'::floatvector AS distance
                    FROM %s
                    %s
                    ORDER BY distance
                    LIMIT %d;
                    """.formatted(selectFields, vectorField, buildVectorLiteral(queryVector), collectionName,
                    whereClause, topK);

            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(searchSql);
                List<List<Object>> rows = cursor.fetchAll();
                List<String> columns = cursor.columns();
                List<VectorSearchResult> results = new ArrayList<>();
                for (List<Object> row : rows) {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    Double distance = null;
                    for (int index = 0; index < columns.size(); index++) {
                        String colName = columns.get(index);
                        Object value = index < row.size() ? row.get(index) : null;
                        if ("distance".equals(colName)) {
                            distance = value instanceof Number number ? number.doubleValue() : null;
                        } else {
                            fields.put(colName, parseMaybeJson(value));
                        }
                    }
                    double score = distance == null ? 0.0d : score(distanceMetric, distance);
                    results.add(new VectorSearchResult(score, fields));
                }
                return results;
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_DOC_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Failed to search: " + exception.getMessage())
                );
            } finally {
                cursor.close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (ids == null || ids.isEmpty()) {
                LOGGER.warning("No IDs provided for deletion");
                return;
            }
            String idColumn = stringValue(safeMap(kwargs), "id_column", "id");
            String placeholders = String.join(", ", repeat("?", ids.size()));
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute("DELETE FROM " + collectionName + " WHERE " + idColumn + " IN (" + placeholders + ")",
                        ids.toArray());
                LOGGER.info("Deleted " + ids.size() + " documents from collection");
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_DOC_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Failed to delete documents: " + exception.getMessage())
                );
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters,
            Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            if (filters == null || filters.isEmpty()) {
                LOGGER.warning("No filters provided for deletion");
                return;
            }
            String filterClause = buildFilterClause(filters);
            if (filterClause == null || filterClause.isBlank()) {
                return;
            }
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute("SELECT COUNT(*) FROM " + collectionName + " WHERE " + filterClause);
                Object count = firstValue(cursor.fetchOne());
                cursor.execute("DELETE FROM " + collectionName + " WHERE " + filterClause);
                LOGGER.info("Deleted " + count + " documents matching filters from collection");
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw buildError(
                        StatusCode.STORE_VECTOR_DOC_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Failed to delete documents by filters: " + exception.getMessage())
                );
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<String>> listCollectionNames() {
        return CompletableFuture.supplyAsync(() -> {
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                        AND table_type = 'BASE TABLE';
                        """);
                return cursor.fetchAll().stream()
                        .map(row -> String.valueOf(row.get(0)))
                        .toList();
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
        return CompletableFuture.runAsync(() -> {
            if (operations == null || operations.isEmpty()) {
                return;
            }
            CollectionSchema oldSchema = getSchema(collectionName, Map.of()).join();
            CollectionSchema newSchema = computeNewSchema(oldSchema, operations);
            Function<Map<String, Object>, Map<String, Object>> transformFunc =
                    buildTransformFunctionForOperations(operations);
            String tempCollectionName = collectionName + "_migration_" + Instant.now().toEpochMilli();
            try {
                Map<String, Object> metadata = getCollectionMetadata(collectionName).join();
                createCollection(tempCollectionName, newSchema,
                        Map.of("distance_metric", metadata.getOrDefault("distance_metric", "COSINE"))).join();
                List<Map<String, Object>> docs = getAllDocuments(collectionName).join();
                List<Map<String, Object>> transformed = new ArrayList<>();
                for (Map<String, Object> doc : docs) {
                    transformed.add(transformFunc.apply(new LinkedHashMap<>(doc)));
                }
                if (!transformed.isEmpty()) {
                    addDocs(tempCollectionName, transformed, Map.of()).join();
                }
                deleteCollection(collectionName, Map.of()).join();
                renameTable(tempCollectionName, collectionName);
                collectionMetadata.remove(collectionName);
                LOGGER.info("Migration for '" + collectionName + "' completed successfully.");
            } catch (RuntimeException exception) {
                if (collectionExists(tempCollectionName, Map.of()).join()) {
                    deleteCollection(tempCollectionName, Map.of()).join();
                }
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "Migration for '" + collectionName + "' failed: " + exception.getMessage())
                );
            }
        }, IO_EXECUTOR);
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
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(
                        "SELECT EXISTS (SELECT table_name FROM information_schema.tables WHERE table_name = ?);",
                        collectionName
                );
                if (!Boolean.TRUE.equals(firstValue(cursor.fetchOne()))) {
                    throw buildError(
                            StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND,
                            "error_msg",
                            "'" + collectionName + "' does not exist."
                    );
                }
                collectionMetadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>()).putAll(metadata);
                LOGGER.info("Updated collection metadata for '" + collectionName + "': " + metadata);
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
        return CompletableFuture.supplyAsync(() -> {
            if (collectionMetadata.containsKey(collectionName)) {
                return new LinkedHashMap<>(collectionMetadata.get(collectionName));
            }
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute(
                        "SELECT EXISTS (SELECT table_name FROM information_schema.tables WHERE table_name = ?);",
                        collectionName
                );
                if (!Boolean.TRUE.equals(firstValue(cursor.fetchOne()))) {
                    return new LinkedHashMap<>(Map.of("distance_metric", "COSINE", "schema_version", 0));
                }
                cursor.execute("""
                        SELECT column_name, data_type
                        FROM information_schema.columns
                        WHERE table_name = ? AND data_type LIKE 'floatvector%%';
                        """, collectionName);
                List<List<Object>> vectorFields = cursor.fetchAll();
                Object vectorField = vectorFields.isEmpty() ? null : vectorFields.get(0).get(0);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("distance_metric", "COSINE");
                metadata.put("vector_field", vectorField);
                metadata.put("schema_version", 0);
                collectionMetadata.put(collectionName, metadata);
                return new LinkedHashMap<>(metadata);
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    public CompletableFuture<List<Map<String, Object>>> getAllDocuments(String collectionName) {
        return CompletableFuture.supplyAsync(() -> {
            SqlCursor cursor = connection().cursor();
            try {
                cursor.execute("SELECT * FROM " + collectionName + ";");
                List<String> columns = cursor.columns();
                List<Map<String, Object>> docs = new ArrayList<>();
                for (List<Object> row : cursor.fetchAll()) {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    for (int index = 0; index < columns.size(); index++) {
                        doc.put(columns.get(index), index < row.size() ? parseMaybeJson(row.get(index)) : null);
                    }
                    docs.add(doc);
                }
                return docs;
            } finally {
                cursor.close();
            }
        }, IO_EXECUTOR);
    }

    String mapFieldTypeToPg(VectorDataType fieldType) {
        return switch (fieldType) {
            case VARCHAR -> "VARCHAR";
            case FLOAT_VECTOR -> "floatvector";
            case INT64 -> "BIGINT";
            case INT32 -> "INTEGER";
            case FLOAT -> "REAL";
            case DOUBLE -> "DOUBLE PRECISION";
            case BOOL -> "BOOLEAN";
            case JSON -> "JSONB";
            default -> throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg",
                    "unsupported field type, field_type=" + fieldType
            );
        };
    }

    VectorDataType mapPgTypeToOurType(String pgType) {
        String normalized = pgType == null ? "" : pgType.toLowerCase();
        return switch (normalized) {
            case "varchar", "text", "character varying" -> VectorDataType.VARCHAR;
            case "floatvector" -> VectorDataType.FLOAT_VECTOR;
            case "bigint" -> VectorDataType.INT64;
            case "integer", "int" -> VectorDataType.INT32;
            case "real" -> VectorDataType.FLOAT;
            case "double precision" -> VectorDataType.DOUBLE;
            case "boolean" -> VectorDataType.BOOL;
            case "jsonb" -> VectorDataType.JSON;
            default -> {
                if (normalized.startsWith("varchar")) {
                    yield VectorDataType.VARCHAR;
                }
                if (normalized.startsWith("floatvector")) {
                    yield VectorDataType.FLOAT_VECTOR;
                }
                LOGGER.warning("Unsupported data type: " + pgType + ", defaulting to VARCHAR");
                yield VectorDataType.VARCHAR;
            }
        };
    }

    String buildFilterClause(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                parts.add(entry.getKey() + " = '" + text + "'");
            } else if (value instanceof Boolean bool) {
                parts.add(entry.getKey() + " = " + (bool ? "TRUE" : "FALSE"));
            } else {
                parts.add(entry.getKey() + " = " + value);
            }
        }
        return parts.isEmpty() ? null : String.join(" AND ", parts);
    }

    double score(String metric, double distance) {
        String normalized = metric == null ? "" : metric.toUpperCase();
        if ("L2".equals(normalized)) {
            return Math.max(0.0d, (4.0d - distance) / 4.0d);
        }
        if ("COSINE".equals(normalized)) {
            return (2.0d - distance) / 2.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, (distance + 1.0d) / 2.0d));
    }

    private String buildIndexSql(String collectionName, String vectorFieldName, String distanceMetric,
            String indexType, Map<String, Object> kwargs) {
        if (!"diskann".equals(indexType)) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg",
                    "index_type only support DiskANN"
            );
        }
        String pgMetric = "L2".equals(distanceMetric) ? "l2" : "cosine";
        int pgNseg = intValue(kwargs.get("pg_nseg"), 128);
        int pgNclus = intValue(kwargs.get("pg_nclus"), 16);
        int numParallels = intValue(kwargs.get("num_parallels"), 32);
        return """
                CREATE INDEX %s_%s_idx
                ON %s
                USING GSDISKANN (%s %s)
                WITH (enable_pq = true, pg_nseg = %d, pg_nclus = %d,
                num_parallels = %d, quantization_type = 'lvq', subgraph_count = 1);
                """.formatted(collectionName, vectorFieldName, collectionName, vectorFieldName, pgMetric,
                pgNseg, pgNclus, numParallels);
    }

    private void renameTable(String oldName, String newName) {
        SqlCursor cursor = connection().cursor();
        try {
            cursor.execute("ALTER TABLE " + oldName + " RENAME TO " + newName + ";");
        } finally {
            cursor.close();
        }
    }

    private CollectionSchema normalizeSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return CollectionSchema.fromDict(converted);
        }
        throw buildError(
                StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                "error_msg",
                "schema must be CollectionSchema or dict"
        );
    }

    private String selectFields(Object outputFields, String vectorField) {
        if (!(outputFields instanceof Collection<?> collection) || collection.isEmpty()) {
            return "*";
        }
        List<String> fields = collection.stream().map(String::valueOf).collect(ArrayList::new, ArrayList::add,
                ArrayList::addAll);
        if (!fields.contains(vectorField)) {
            fields.add(vectorField);
        }
        return String.join(", ", fields);
    }

    private String buildVectorLiteral(List<Double> queryVector) {
        List<String> values = new ArrayList<>();
        for (Double value : queryVector == null ? List.<Double>of() : queryVector) {
            values.add(String.valueOf(value));
        }
        return "[" + String.join(",", values) + "]";
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
            } else if ("RenameScalarFieldOperation".equals(kind)) {
                renameField(newSchema, stringProperty(operation, "oldFieldName", "old_field_name"),
                        stringProperty(operation, "newFieldName", "new_field_name"));
            } else if ("UpdateScalarFieldTypeOperation".equals(kind)) {
                updateFieldType(newSchema, stringProperty(operation, "fieldName", "field_name"),
                        mapStringToVectorDataType(stringProperty(operation, "newFieldType", "new_field_type")));
            } else if ("UpdateEmbeddingDimensionOperation".equals(kind)) {
                updateVectorDim(newSchema, stringProperty(operation, "fieldName", "field_name"),
                        intValue(property(operation, "newDimension", "new_dimension"), 0));
            } else {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "Unsupported operation type: " + kind
                );
            }
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

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? "" : value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private List<Double> zeroVector(int dimension) {
        List<Double> vector = new ArrayList<>();
        for (int index = 0; index < dimension; index++) {
            vector.add(0.0d);
        }
        return vector;
    }

    private Object parseMaybeJson(Object value) {
        if (!(value instanceof String text) || !text.startsWith("{")) {
            return value;
        }
        try {
            return OBJECT_MAPPER.readValue(text.replace("'", "\""), MAP_TYPE);
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.WARNING, "Failed to parse JSON value", exception);
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

    private static Map<String, Object> safeMap(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private static String stringValue(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Integer varcharLength(String dataType) {
        Matcher matcher = VARCHAR_PATTERN.matcher(dataType == null ? "" : dataType);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static Integer floatVectorDim(String dataType) {
        Matcher matcher = FLOAT_VECTOR_PATTERN.matcher(dataType == null ? "" : dataType);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static Object firstValue(List<Object> row) {
        return row == null || row.isEmpty() ? null : row.get(0);
    }

    private static List<String> repeat(String value, int count) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(value);
        }
        return values;
    }

    private static Map<String, Object> extraKwargs(Map<String, Object> kwargs) {
        Map<String, Object> extra = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        extra.remove("host");
        extra.remove("port");
        extra.remove("database");
        extra.remove("user");
        extra.remove("password");
        return extra;
    }

    private static SqlConnection createJdbcConnection(String host, int port, String database, String user,
            String password, Map<String, Object> kwargs) {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        Properties properties = new Properties();
        properties.put("user", user);
        properties.put("password", password);
        for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
            if (entry.getValue() != null) {
                properties.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        try {
            return new JdbcSqlConnection(DriverManager.getConnection(url, properties));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to connect to GaussVector", exception);
        }
    }

    @FunctionalInterface
    interface SqlConnectionFactory {
        SqlConnection connect(String host, int port, String database, String user, String password,
                Map<String, Object> kwargs);
    }

    interface SqlConnection {
        SqlCursor cursor();

        void setAutoCommit(boolean autoCommit);

        void close();
    }

    interface SqlCursor {
        void execute(String sql, Object... params);

        void executeMany(String sql, List<List<Object>> values);

        List<Object> fetchOne();

        List<List<Object>> fetchAll();

        List<String> columns();

        void close();
    }

    private static final class JdbcSqlConnection implements SqlConnection {
        private final Connection connection;

        private JdbcSqlConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public SqlCursor cursor() {
            return new JdbcSqlCursor(connection);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to set autocommit", exception);
            }
        }

        @Override
        public void close() {
            try {
                connection.close();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to close connection", exception);
            }
        }
    }

    private static final class JdbcSqlCursor implements SqlCursor {
        private final Connection connection;
        private ResultSet resultSet;
        private Statement statement;

        private JdbcSqlCursor(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void execute(String sql, Object... params) {
            closeStatement();
            try {
                if (params == null || params.length == 0) {
                    statement = connection.createStatement();
                    boolean hasResult = statement.execute(sql);
                    resultSet = hasResult ? statement.getResultSet() : null;
                    return;
                }
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                for (int index = 0; index < params.length; index++) {
                    preparedStatement.setObject(index + 1, params[index]);
                }
                statement = preparedStatement;
                boolean hasResult = preparedStatement.execute();
                resultSet = hasResult ? preparedStatement.getResultSet() : null;
            } catch (SQLException exception) {
                throw new IllegalStateException("SQL execution failed: " + sql, exception);
            }
        }

        @Override
        public void executeMany(String sql, List<List<Object>> values) {
            closeStatement();
            try {
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                statement = preparedStatement;
                for (List<Object> row : values) {
                    for (int index = 0; index < row.size(); index++) {
                        preparedStatement.setObject(index + 1, row.get(index));
                    }
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                resultSet = null;
            } catch (SQLException exception) {
                throw new IllegalStateException("Batch SQL execution failed: " + sql, exception);
            }
        }

        @Override
        public List<Object> fetchOne() {
            List<List<Object>> rows = fetchAll();
            return rows.isEmpty() ? List.of() : rows.get(0);
        }

        @Override
        public List<List<Object>> fetchAll() {
            if (resultSet == null) {
                return List.of();
            }
            try {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int columnCount = metadata.getColumnCount();
                List<List<Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int index = 1; index <= columnCount; index++) {
                        row.add(resultSet.getObject(index));
                    }
                    rows.add(row);
                }
                return rows;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to fetch rows", exception);
            }
        }

        @Override
        public List<String> columns() {
            if (resultSet == null) {
                return List.of();
            }
            try {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    columns.add(metadata.getColumnLabel(index));
                }
                return columns;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to read columns", exception);
            }
        }

        @Override
        public void close() {
            closeStatement();
        }

        private void closeStatement() {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                    // Best effort close.
                }
                resultSet = null;
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ignored) {
                    // Best effort close.
                }
                statement = null;
            }
        }
    }
}