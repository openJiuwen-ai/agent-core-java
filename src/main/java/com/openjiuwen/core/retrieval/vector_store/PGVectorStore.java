/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.PGVectorField;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import com.pgvector.PGvector;
import org.postgresql.util.PGobject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * PostgreSQL vector store implementation using pgvector.
 *
 * <p>Mirrors Python's {@code PGVectorStore} in
 * {@code openjiuwen/core/retrieval/vector_store/pg_store.py}.</p>
 */
public class PGVectorStore implements VectorStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final int DEFAULT_BATCH_SIZE = 128;
    private static final int MAX_VECTOR_DIMENSION = 2000;
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("pg-vector-store-io");

    private final VectorStoreConfig config;
    private final DataSource dataSource;
    private final String pgUri;
    private final String collectionName;
    private final String textField;
    private final String sparseVectorField;
    private final String metadataField;
    private final String docIdField;
    private final String databaseName;
    private final String distanceMetric;
    private final PGVectorField vectorFieldConfig;
    private final String vectorColName;
    private final String vectorField;

    public PGVectorStore(VectorStoreConfig config, String pgUri) {
        this(config, pgUri, "content", "embedding", "sparse_vector", "metadata", "document_id");
    }

    public PGVectorStore(VectorStoreConfig config,
                         String pgUri,
                         String textField,
                         String vectorField,
                         String sparseVectorField,
                         String metadataField,
                         String docIdField) {
        this(config, null, pgUri, textField, createFieldConfig(vectorField), sparseVectorField, metadataField, docIdField);
    }

    public PGVectorStore(VectorStoreConfig config,
                         String pgUri,
                         String textField,
                         PGVectorField vectorFieldConfig,
                         String sparseVectorField,
                         String metadataField,
                         String docIdField) {
        this(config, null, pgUri, textField, vectorFieldConfig, sparseVectorField, metadataField, docIdField);
    }

    public PGVectorStore(VectorStoreConfig config, DataSource dataSource) {
        this(config, dataSource, "content", createFieldConfig("embedding"), "sparse_vector", "metadata", "document_id");
    }

    public PGVectorStore(VectorStoreConfig config,
                         DataSource dataSource,
                         String textField,
                         PGVectorField vectorFieldConfig,
                         String sparseVectorField,
                         String metadataField,
                         String docIdField) {
        this(config, dataSource, null, textField, vectorFieldConfig, sparseVectorField, metadataField, docIdField);
    }

    private PGVectorStore(VectorStoreConfig config,
                          DataSource dataSource,
                          String pgUri,
                          String textField,
                          PGVectorField vectorFieldConfig,
                          String sparseVectorField,
                          String metadataField,
                          String docIdField) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataSource = dataSource;
        this.pgUri = pgUri;
        this.collectionName = requireIdentifier(config.getCollectionName(), "collection_name");
        this.textField = requireIdentifier(textField == null ? "content" : textField, "text_field");
        this.sparseVectorField = requireIdentifier(
                sparseVectorField == null ? "sparse_vector" : sparseVectorField,
                "sparse_vector_field"
        );
        this.metadataField = requireIdentifier(metadataField == null ? "metadata" : metadataField, "metadata_field");
        this.docIdField = requireIdentifier(docIdField == null ? "document_id" : docIdField, "doc_id_field");
        this.databaseName = config.getDatabaseName() == null ? "" : config.getDatabaseName();
        this.distanceMetric = normalizeMetric(config.getDistanceMetric());
        this.vectorFieldConfig = Objects.requireNonNull(vectorFieldConfig, "vectorFieldConfig");
        this.vectorColName = requireIdentifier(this.vectorFieldConfig.getVectorField(), "vector_field");
        this.vectorField = vectorColName;
    }

    public VectorStoreConfig getConfig() {
        return config;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getPgUri() {
        return pgUri;
    }

    public String getTextField() {
        return textField;
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

    public PGVectorField getVectorFieldConfig() {
        return vectorFieldConfig;
    }

    public String getVectorColName() {
        return vectorColName;
    }

    public String getVectorField() {
        return vectorField;
    }

    @Override
    public void checkVectorField() {
        // Python implementation intentionally performs no strict synchronous schema check.
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data,
                                       Integer batchSize,
                                       Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> addSync(data, batchSize), IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(List<Double> queryVector,
                                                           int topK,
                                                           VectorStoreFilter filters,
                                                           Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> searchSync(queryVector, topK, filtersToMap(filters)), IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> sparseSearch(String queryText,
                                                                 int topK,
                                                                 VectorStoreFilter filters,
                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> sparseSearchSync(queryText, topK, filtersToMap(filters)), IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> hybridSearch(String queryText,
                                                                 List<Double> queryVector,
                                                                 int topK,
                                                                 double alpha,
                                                                 VectorStoreFilter filters,
                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            List<RetrievalResult> vectorResults = queryVector == null || queryVector.isEmpty()
                    ? List.of()
                    : search(queryVector, topK * 2, filters, kwargs).join();
            List<RetrievalResult> sparseResults = sparseSearch(queryText, topK * 2, filters, kwargs).join();
            List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(List.of(vectorResults, sparseResults), 60);
            List<RetrievalResult> finalResults = new ArrayList<>();
            int count = Math.min(topK, fused.size());
            for (int index = 0; index < count; index++) {
                RetrievalResult result = fused.get(index);
                Map<String, Object> metadata = new LinkedHashMap<>(result.getMetadata());
                Object id = metadata.remove("id");
                String resultId = id == null ? Integer.toString(Objects.hashCode(result.getText())) : String.valueOf(id);
                Object docId = metadata.get(docIdField);
                finalResults.add(new RetrievalResult(
                        result.getText(),
                        result.getScore(),
                        metadata,
                        docId == null ? null : String.valueOf(docId),
                        resultId
                ));
            }
            return List.copyOf(finalResults);
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> delete(List<String> ids,
                                             DeleteFilter filterExpr,
                                             Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> deleteSync(ids, filterExpr), IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            String checkedTable = requireIdentifier(tableName == null ? collectionName : tableName, "table_name");
            try (Connection connection = openConnection()) {
                return tableExists(connection, checkedTable);
            } catch (SQLException exception) {
                throw sqlError("failed to check PGVector table existence", exception);
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> deleteTable(String tableName) {
        return CompletableFuture.runAsync(() -> {
            String targetTable = requireIdentifier(tableName == null ? collectionName : tableName, "table_name");
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + quoteIdentifier(targetTable));
            } catch (SQLException exception) {
                throw sqlError("failed to drop PGVector table", exception);
            }
        }, IO_EXECUTOR);
    }

    protected Connection openConnection() throws SQLException {
        Connection connection = dataSource == null
                ? DriverManager.getConnection(toJdbcUrl(pgUri))
                : dataSource.getConnection();
        registerVectorTypes(connection);
        return connection;
    }

    protected void registerVectorTypes(Connection connection) throws SQLException {
        PGvector.registerTypes(connection);
    }

    private void addSync(List<Map<String, Object>> data, Integer batchSize) {
        if (data == null || data.isEmpty()) {
            return;
        }
        int dimension = inferDimension(data);
        try (Connection connection = openConnection()) {
            if (!getOrCreateTable(connection, dimension)) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "error_msg",
                        "Failed to create or retrieve table"
                );
            }

            int safeBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(upsertSql(collectionName))) {
                int pending = 0;
                for (Map<String, Object> item : data) {
                    bindUpsert(statement, normalizeRow(item));
                    statement.addBatch();
                    pending++;
                    if (pending >= safeBatchSize) {
                        statement.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw sqlError("failed to upsert PGVector rows", exception);
        }
    }

    private List<RetrievalResult> searchSync(List<Double> queryVector, int topK, Map<String, Object> filters) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }
        try (Connection connection = openConnection()) {
            if (!tableExists(connection, collectionName)) {
                return List.of();
            }
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append(quoteIdentifier("id")).append(", ")
                    .append(quoteIdentifier(textField)).append(", ")
                    .append(quoteIdentifier(metadataField)).append(", ")
                    .append(quoteIdentifier(vectorColName)).append(" ").append(distanceOperator()).append(" ? AS raw_score ")
                    .append("FROM ").append(quoteIdentifier(collectionName))
                    .append(" WHERE ").append(quoteIdentifier(vectorColName)).append(" IS NOT NULL");
            List<Object> parameters = new ArrayList<>();
            appendFilterSql(sql, filters, parameters);
            sql.append(" ORDER BY raw_score ASC LIMIT ?");
            parameters.add(topK);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setObject(1, toPgVector(queryVector));
                bindValues(statement, parameters, 2);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return readSearchResults(resultSet, true);
                }
            }
        } catch (SQLException exception) {
            throw sqlError("failed to search PGVector rows", exception);
        }
    }

    private List<RetrievalResult> sparseSearchSync(String queryText, int topK, Map<String, Object> filters) {
        if (queryText == null || queryText.isBlank() || topK <= 0) {
            return List.of();
        }
        try (Connection connection = openConnection()) {
            if (!tableExists(connection, collectionName)) {
                return List.of();
            }
            String tsVector = "to_tsvector('english', " + quoteIdentifier(textField) + ")";
            String tsQuery = "websearch_to_tsquery('english', ?)";
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append(quoteIdentifier("id")).append(", ")
                    .append(quoteIdentifier(textField)).append(", ")
                    .append(quoteIdentifier(metadataField)).append(", ")
                    .append("ts_rank(").append(tsVector).append(", ").append(tsQuery).append(") AS raw_score ")
                    .append("FROM ").append(quoteIdentifier(collectionName))
                    .append(" WHERE ").append(tsVector).append(" @@ ").append(tsQuery);
            List<Object> parameters = new ArrayList<>();
            appendFilterSql(sql, filters, parameters);
            sql.append(" ORDER BY raw_score DESC LIMIT ?");
            parameters.add(topK);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setString(1, queryText);
                statement.setString(2, queryText);
                bindValues(statement, parameters, 3);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return readSearchResults(resultSet, false);
                }
            }
        } catch (SQLException exception) {
            throw sqlError("failed to run PGVector sparse search", exception);
        }
    }

    private boolean deleteSync(List<String> ids, DeleteFilter filterExpr) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        try (Connection connection = openConnection()) {
            if (!tableExists(connection, collectionName)) {
                return false;
            }
            String sql = "DELETE FROM " + quoteIdentifier(collectionName)
                    + " WHERE " + quoteIdentifier("id") + " IN (" + parameterMarkers(ids.size()) + ")";
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindValues(statement, new ArrayList<>(ids));
                boolean deleted = statement.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw sqlError("failed to delete PGVector rows", exception);
        }
    }

    private boolean getOrCreateTable(Connection connection, int dimension) throws SQLException {
        if (tableExists(connection, collectionName)) {
            return true;
        }
        if (dimension <= 0) {
            return false;
        }
        if (dimension > MAX_VECTOR_DIMENSION) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "pgvector only supports vector dimensions up to " + MAX_VECTOR_DIMENSION + ". Got " + dimension + "."
            );
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute(createTableSql(collectionName, dimension));
            if ("hnsw".equals(vectorFieldConfig.getIndexType())) {
                statement.execute(hnswIndexSql(collectionName));
            }
        }
        return true;
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private String createTableSql(String tableName, int dimension) {
        return "CREATE TABLE IF NOT EXISTS " + quoteIdentifier(tableName) + " ("
                + quoteIdentifier("id") + " TEXT PRIMARY KEY, "
                + quoteIdentifier(textField) + " TEXT, "
                + quoteIdentifier(metadataField) + " JSONB, "
                + quoteIdentifier(vectorColName) + " vector(" + dimension + ")"
                + ")";
    }

    private String hnswIndexSql(String tableName) {
        return "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, vectorColName))
                + " ON " + quoteIdentifier(tableName)
                + " USING hnsw (" + quoteIdentifier(vectorColName) + " " + operatorClass() + ") "
                + "WITH (m = " + vectorFieldConfig.getM()
                + ", ef_construction = " + vectorFieldConfig.getEfConstruction() + ")";
    }

    private String upsertSql(String tableName) {
        return "INSERT INTO " + quoteIdentifier(tableName) + " ("
                + quoteIdentifier("id") + ", "
                + quoteIdentifier(textField) + ", "
                + quoteIdentifier(metadataField) + ", "
                + quoteIdentifier(vectorColName)
                + ") VALUES (?, ?, ?::jsonb, ?) "
                + "ON CONFLICT (" + quoteIdentifier("id") + ") DO UPDATE SET "
                + quoteIdentifier(textField) + " = EXCLUDED." + quoteIdentifier(textField) + ", "
                + quoteIdentifier(metadataField) + " = EXCLUDED." + quoteIdentifier(metadataField) + ", "
                + quoteIdentifier(vectorColName) + " = EXCLUDED." + quoteIdentifier(vectorColName);
    }

    private void bindUpsert(PreparedStatement statement, StoredRow row) throws SQLException {
        statement.setString(1, row.id());
        statement.setString(2, row.text());
        statement.setString(3, toJson(row.metadata()));
        if (row.vector() == null || row.vector().isEmpty()) {
            statement.setNull(4, Types.OTHER);
        } else {
            statement.setObject(4, toPgVector(row.vector()));
        }
    }

    private StoredRow normalizeRow(Map<String, Object> item) {
        Map<String, Object> source = item == null ? Map.of() : item;
        Map<String, Object> metadata = parseMetadata(source.get(metadataField));
        if (source.containsKey(docIdField)) {
            metadata.put(docIdField, pythonString(source.get(docIdField)));
        }
        if (source.containsKey("chunk_id")) {
            metadata.put("chunk_id", pythonString(source.get("chunk_id")));
        }

        Object idValue = source.containsKey("id") ? source.get("id") : source.get("pk");
        String id = pythonString(idValue);
        String text = source.containsKey(textField) ? pythonString(source.get(textField)) : "";
        return new StoredRow(id, text, castDoubleList(source.get(vectorColName)), metadata);
    }

    private int inferDimension(List<Map<String, Object>> data) {
        Object firstVector = data.get(0).get(vectorColName);
        List<Double> vector = castDoubleList(firstVector);
        return vector == null || vector.isEmpty() ? 0 : vector.size();
    }

    private void appendFilterSql(StringBuilder sql, Map<String, Object> filters, List<Object> parameters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (isColumnName(key)) {
                sql.append(" AND ").append(quoteIdentifier(key)).append(" = ?");
                parameters.add(value);
            } else {
                sql.append(" AND ").append(quoteIdentifier(metadataField)).append(" @> ?::jsonb");
                parameters.add(toJson(Map.of(key, value)));
            }
        }
    }

    private boolean isColumnName(String key) {
        return "id".equals(key) || textField.equals(key) || metadataField.equals(key) || vectorColName.equals(key);
    }

    private List<RetrievalResult> readSearchResults(ResultSet resultSet, boolean vectorMode) throws SQLException {
        List<RetrievalResult> results = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> metadata = readMetadata(resultSet.getObject(metadataField));
            double rawScore = resultSet.getDouble("raw_score");
            double score = vectorMode ? normalizeVectorScore(rawScore) : rawScore;
            metadata.put("raw_score", rawScore);
            Object docId = metadata.get(docIdField);
            Object chunkId = metadata.get("chunk_id");
            results.add(new RetrievalResult(
                    resultSet.getString(textField),
                    score,
                    metadata,
                    docId == null ? null : String.valueOf(docId),
                    chunkId == null ? resultSet.getString("id") : String.valueOf(chunkId)
            ));
        }
        return List.copyOf(results);
    }

    private double normalizeVectorScore(double distance) {
        if ("cosine".equals(distanceMetric)) {
            return 1.0d - distance;
        }
        if ("dot".equals(distanceMetric) || "ip".equals(distanceMetric)) {
            return -distance;
        }
        return Math.max(0.0d, 1.0d - distance);
    }

    private Map<String, Object> filtersToMap(VectorStoreFilter filters) {
        if (filters == null || filters.mapping() == null || filters.mapping().isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(filters.mapping());
    }

    private void bindValues(PreparedStatement statement, List<?> values) throws SQLException {
        bindValues(statement, values, 1);
    }

    private void bindValues(PreparedStatement statement, List<?> values, int startIndex) throws SQLException {
        int index = startIndex;
        for (Object value : values) {
            statement.setObject(index++, value);
        }
    }

    private PGvector toPgVector(List<Double> vector) {
        float[] values = new float[vector.size()];
        for (int index = 0; index < vector.size(); index++) {
            values[index] = vector.get(index).floatValue();
        }
        return new PGvector(values);
    }

    private Map<String, Object> readMetadata(Object raw) {
        if (raw instanceof PGobject pgObject) {
            return parseMetadata(pgObject.getValue());
        }
        return parseMetadata(raw);
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
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, LinkedHashMap.class);
                return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
            } catch (JsonProcessingException exception) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private List<Double> castDoubleList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return null;
        }
        List<Double> result = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String distanceOperator() {
        if ("l2".equals(distanceMetric) || "euclidean".equals(distanceMetric)) {
            return "<->";
        }
        if ("dot".equals(distanceMetric) || "ip".equals(distanceMetric)) {
            return "<#>";
        }
        return "<=>";
    }

    private String operatorClass() {
        if ("l2".equals(distanceMetric) || "euclidean".equals(distanceMetric)) {
            return "vector_l2_ops";
        }
        if ("dot".equals(distanceMetric) || "ip".equals(distanceMetric)) {
            return "vector_ip_ops";
        }
        return "vector_cosine_ops";
    }

    private String parameterMarkers(int size) {
        List<String> markers = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            markers.add("?");
        }
        return String.join(", ", markers);
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + requireIdentifier(identifier, "identifier") + "\"";
    }

    private String requireIdentifier(String identifier, String field) {
        if (identifier == null || identifier.isBlank() || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    field + " must match " + IDENTIFIER_PATTERN.pattern()
            );
        }
        return identifier;
    }

    private String indexName(String tableName, String columnName) {
        String raw = "idx_" + tableName + "_" + columnName;
        if (raw.length() <= 63) {
            return raw;
        }
        return raw.substring(0, 54) + "_" + Integer.toHexString(raw.hashCode());
    }

    private String toJdbcUrl(String uri) {
        if (uri == null || uri.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "pg_uri is required"
            );
        }
        if (uri.startsWith("jdbc:postgresql://")) {
            return uri;
        }
        if (uri.startsWith("postgresql+asyncpg://")) {
            return "jdbc:postgresql://" + uri.substring("postgresql+asyncpg://".length());
        }
        return uri;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "failed to serialize PGVector JSON payload"
            );
        }
    }

    private String pythonString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }

    private RuntimeException sqlError(String message, SQLException exception) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                "error_msg",
                message + ": " + exception.getMessage()
        );
    }

    private static PGVectorField createFieldConfig(String vectorField) {
        PGVectorField field = new PGVectorField();
        field.setVectorField(vectorField == null ? "embedding" : vectorField);
        return field;
    }

    private static String normalizeMetric(String metric) {
        return metric == null || metric.isBlank() ? "cosine" : metric;
    }

    private record StoredRow(String id,
                             String text,
                             List<Double> vector,
                             Map<String, Object> metadata) {
    }
}
