/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.retrieval.vector_store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.common.WeightedRankConfig;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreSQL/pgvector-backed vector store for retrieval.
 */
public class PGVectorStore implements VectorStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("^jdbc:postgresql://[^/]+/([^?;]+).*$");
    private static final String PUBLIC_SCHEMA = "public";
    private static final int DEFAULT_BATCH_SIZE = 128;
    private static final int MAX_VECTOR_DIMENSION = 2000;

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String databaseName;
    private final String distanceMetric;
    private final String indexType;
    private final String textField;
    private final String vectorField;
    private final String docIdField;
    private final String chunkIdField;
    private final String metadataField;
    private final String sparseVectorField;

    private String collectionName;

    public PGVectorStore(VectorStoreConfig config) {
        this(config, null, null, null, "hybrid", Map.of());
    }

    public PGVectorStore(VectorStoreConfig config, String indexType) {
        this(config, null, null, null, indexType, Map.of());
    }

    public PGVectorStore(VectorStoreConfig config,
                         String jdbcUrl,
                         String username,
                         String password,
                         String indexType) {
        this(config, jdbcUrl, username, password, indexType, Map.of());
    }

    public PGVectorStore(VectorStoreConfig config,
                         String jdbcUrl,
                         String username,
                         String password,
                         String indexType,
                         Map<String, Object> options) {
        this(config, null, jdbcUrl, username, password, indexType, options);
    }

    public PGVectorStore(VectorStoreConfig config, DataSource dataSource, String indexType) {
        this(config, dataSource, indexType, Map.of());
    }

    public PGVectorStore(VectorStoreConfig config,
                         DataSource dataSource,
                         String indexType,
                         Map<String, Object> options) {
        this(config, dataSource, null, null, null, indexType, options);
    }

    private PGVectorStore(VectorStoreConfig config,
                          DataSource dataSource,
                          String jdbcUrl,
                          String username,
                          String password,
                          String indexType,
                          Map<String, Object> options) {
        Objects.requireNonNull(config, "config");
        config.validate();
        if (dataSource == null && (jdbcUrl == null || jdbcUrl.isBlank())) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "jdbcUrl or dataSource is required for PGVectorStore");
        }
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "PGVectorStore jdbcUrl must start with jdbc:postgresql://");
        }
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.databaseName = resolveDatabaseName(config.getDatabaseName(), jdbcUrl);
        this.distanceMetric = config.getDistanceMetric();
        this.indexType = RetrievalValidation.validateIndexType(indexType == null ? "hybrid" : indexType, "PGVectorStore.indexType");
        this.collectionName = requireIdentifier(config.getCollectionName(), "collectionName");
        this.textField = requireIdentifier("text", "textField");
        this.vectorField = options != null && options.containsKey("vector_field")
                ? requireIdentifier(String.valueOf(options.get("vector_field")), "vectorField")
                : "vector";
        this.docIdField = requireIdentifier("doc_id", "docIdField");
        this.chunkIdField = requireIdentifier("chunk_id", "chunkIdField");
        this.metadataField = requireIdentifier("metadata", "metadataField");
        this.sparseVectorField = requireIdentifier("sparse_vector", "sparseVectorField");
    }

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public void setCollectionName(String collectionName) {
        this.collectionName = requireIdentifier(collectionName, "collectionName");
    }

    @Override
    public VectorStore withCollection(String collectionName) {
        VectorStoreConfig scopedConfig = new VectorStoreConfig("pgvector", databaseName, collectionName, distanceMetric);
        if (dataSource != null) {
            return new PGVectorStore(scopedConfig, dataSource, indexType, Map.of("vector_field", vectorField));
        }
        return new PGVectorStore(scopedConfig, jdbcUrl, username, password, indexType, Map.of("vector_field", vectorField));
    }

    @Override
    public void ensureCollection(String collectionName,
                                 String indexType,
                                 Integer dimension,
                                 Map<String, Object> options) {
        String targetTable = requireIdentifier(collectionName == null ? this.collectionName : collectionName, "collectionName");
        int safeDimension = requireVectorDimension(dimension);
        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
                statement.execute(createTableSql(targetTable, safeDimension));
                statement.execute(docIndexSql(targetTable));
                statement.execute(chunkIndexSql(targetTable));
                statement.execute(annIndexSql(targetTable, options));
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw sqlError("failed to ensure PGVector collection", ex);
        }
    }

    @Override
    public void checkVectorField() {
        try (Connection connection = openConnection()) {
            if (!tableExists(connection, collectionName)) {
                return;
            }
            Map<String, String> columnTypes = loadColumnTypes(connection, collectionName);
            for (String requiredColumn : List.of("id", textField, vectorField, metadataField, docIdField, chunkIdField)) {
                if (!columnTypes.containsKey(requiredColumn)) {
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                            "PGVector table " + collectionName + " is missing required column " + requiredColumn);
                }
            }
            String vectorType = columnTypes.get(vectorField);
            if (vectorType == null || !vectorType.toLowerCase().startsWith("vector(")) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "PGVector column " + vectorField + " must use vector(n), got " + vectorType);
            }
        } catch (SQLException ex) {
            throw sqlError("failed to validate PGVector schema", ex);
        }
    }

    @Override
    public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        if (data == null || data.isEmpty()) {
            return;
        }
        ensureCollection(collectionName, indexType, inferDimension(data), options == null ? Map.of() : options);

        int safeBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        try (Connection connection = openConnection()) {
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
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw sqlError("failed to upsert PGVector rows", ex);
        }
    }

    @Override
    public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options) {
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
                    .append(quoteIdentifier(docIdField)).append(", ")
                    .append(quoteIdentifier(chunkIdField)).append(", ")
                    .append(quoteIdentifier(vectorField)).append(" ").append(distanceOperator()).append(" ? AS raw_score ")
                    .append("FROM ").append(qualifiedTableName(collectionName))
                    .append(" WHERE ").append(quoteIdentifier(vectorField)).append(" IS NOT NULL");
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
        } catch (SQLException ex) {
            throw sqlError("failed to search PGVector rows", ex);
        }
    }

    @Override
    public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options) {
        if (queryText == null || queryText.isBlank() || topK <= 0) {
            return List.of();
        }
        try (Connection connection = openConnection()) {
            if (!tableExists(connection, collectionName)) {
                return List.of();
            }
            String tsvector = "to_tsvector('english', COALESCE(" + quoteIdentifier(textField) + ", ''))";
            String tsquery = "websearch_to_tsquery('english', ?)";
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append(quoteIdentifier("id")).append(", ")
                    .append(quoteIdentifier(textField)).append(", ")
                    .append(quoteIdentifier(metadataField)).append(", ")
                    .append(quoteIdentifier(docIdField)).append(", ")
                    .append(quoteIdentifier(chunkIdField)).append(", ")
                    .append("ts_rank(").append(tsvector).append(", ").append(tsquery).append(") AS raw_score ")
                    .append("FROM ").append(qualifiedTableName(collectionName))
                    .append(" WHERE ").append(tsvector).append(" @@ ").append(tsquery);
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
        } catch (SQLException ex) {
            throw sqlError("failed to run PGVector sparse search", ex);
        }
    }

    @Override
    public List<SearchResult> hybridSearch(String queryText,
                                           List<Float> queryVector,
                                           int topK,
                                           double alpha,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        if (queryVector == null || queryVector.isEmpty()) {
            return sparseSearch(queryText, topK, filters, options);
        }
        if (queryText == null || queryText.isBlank()) {
            return search(queryVector, topK, filters, options);
        }
        Object rankConfig = options == null ? null : options.get("rank_config");
        List<SearchResult> dense = search(queryVector, topK * 2, filters, options);
        List<SearchResult> sparse = sparseSearch(queryText, topK * 2, filters, options);
        if (rankConfig instanceof RRFRankConfig rrf) {
            List<SearchResult> fused = FusionUtils.rrfFusionSearch(List.of(dense, sparse), rrf);
            return fused.size() <= topK ? fused : fused.subList(0, topK);
        }
        if (rankConfig instanceof WeightedRankConfig weighted) {
            double denseWeight = weighted.getDenseContent() > 0.0 ? weighted.getDenseContent() : weighted.getDenseName();
            double sparseWeight = weighted.getSparseContent();
            return weightedFusion(dense, sparse, topK, denseWeight, sparseWeight);
        }
        return weightedFusion(dense, sparse, topK, alpha, 1.0 - alpha);
    }

    @Override
    public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
        if ((ids == null || ids.isEmpty()) && (filterExpr == null || filterExpr.isEmpty())) {
            return false;
        }

        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            String placeholders = placeholders(ids.size());
            clauses.add("("
                    + quoteIdentifier("id") + " IN (" + placeholders + ") OR "
                    + quoteIdentifier(chunkIdField) + " IN (" + placeholders + "))");
            parameters.addAll(ids);
            parameters.addAll(ids);
        }
        if (filterExpr != null) {
            for (Map.Entry<String, Object> entry : filterExpr.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if ("id".equals(key) || docIdField.equals(key) || chunkIdField.equals(key) || textField.equals(key)) {
                    if (value instanceof Collection<?> collection) {
                        if (collection.isEmpty()) {
                            clauses.add("1 = 0");
                            continue;
                        }
                        clauses.add(quoteIdentifier(key) + " IN (" + placeholders(collection.size()) + ")");
                        parameters.addAll(collection);
                    } else {
                        clauses.add(quoteIdentifier(key) + " = ?");
                        parameters.add(value);
                    }
                } else {
                    clauses.add(quoteIdentifier(metadataField) + " @> ?::jsonb");
                    parameters.add(toJson(Map.of(key, value)));
                }
            }
        }

        try (Connection connection = openConnection()) {
            if (!tableExists(connection, collectionName)) {
                return false;
            }
            String sql = "DELETE FROM " + qualifiedTableName(collectionName) + " WHERE " + String.join(" AND ", clauses);
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindValues(statement, parameters);
                boolean deleted = statement.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw sqlError("failed to delete PGVector rows", ex);
        }
    }

    @Override
    public boolean tableExists(String tableName) {
        try (Connection connection = openConnection()) {
            return tableExists(connection, requireIdentifier(tableName == null ? collectionName : tableName, "tableName"));
        } catch (SQLException ex) {
            throw sqlError("failed to check PGVector table existence", ex);
        }
    }

    @Override
    public void deleteTable(String tableName) {
        String targetTable = requireIdentifier(tableName == null ? collectionName : tableName, "tableName");
        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + qualifiedTableName(targetTable));
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw sqlError("failed to drop PGVector table", ex);
        }
    }

    @Override
    public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
        if (limit <= 0) {
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
                    .append(quoteIdentifier(docIdField)).append(", ")
                    .append(quoteIdentifier(chunkIdField))
                    .append(" FROM ").append(qualifiedTableName(collectionName))
                    .append(" WHERE 1 = 1");
            List<Object> parameters = new ArrayList<>();
            appendFilterSql(sql, filters, parameters);
            sql.append(" ORDER BY ").append(quoteIdentifier("id")).append(" LIMIT ?");
            parameters.add(limit);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bindValues(statement, parameters, 1);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return readSearchResults(resultSet, null);
                }
            }
        } catch (SQLException ex) {
            throw sqlError("failed to query PGVector rows", ex);
        }
    }

    @Override
    public long count(String tableName) {
        String targetTable = requireIdentifier(tableName == null ? collectionName : tableName, "tableName");
        try (Connection connection = openConnection()) {
            if (!tableExists(connection, targetTable)) {
                return 0L;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + qualifiedTableName(targetTable));
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw sqlError("failed to count PGVector rows", ex);
        }
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String getDistanceMetric() {
        return distanceMetric;
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    @Override
    public String getTextField() {
        return textField;
    }

    @Override
    public String getVectorField() {
        return vectorField;
    }

    @Override
    public String getSparseVectorField() {
        return sparseVectorField;
    }

    @Override
    public String getMetadataField() {
        return metadataField;
    }

    @Override
    public String getDocIdField() {
        return docIdField;
    }

    protected Connection openConnection() throws SQLException {
        if (dataSource != null) {
            Connection connection = dataSource.getConnection();
            registerVectorTypes(connection);
            return connection;
        }
        Connection connection = username == null
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, username, password);
        registerVectorTypes(connection);
        return connection;
    }

    protected void registerVectorTypes(Connection connection) throws SQLException {
        PGvector.registerTypes(connection);
    }

    private String distanceOperator() {
        return switch (distanceMetric) {
            case "euclidean" -> "<->";
            case "dot" -> "<#>";
            default -> "<=>";
        };
    }

    private void appendFilterSql(StringBuilder sql, Map<String, Object> filters, List<Object> parameters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if ("id".equals(key) || docIdField.equals(key) || chunkIdField.equals(key) || textField.equals(key)) {
                if (value instanceof Collection<?> collection) {
                    if (collection.isEmpty()) {
                        sql.append(" AND 1 = 0");
                        continue;
                    }
                    sql.append(" AND ").append(quoteIdentifier(key)).append(" IN (").append(placeholders(collection.size())).append(")");
                    parameters.addAll(collection);
                } else {
                    sql.append(" AND ").append(quoteIdentifier(key)).append(" = ?");
                    parameters.add(value);
                }
            } else {
                sql.append(" AND ").append(quoteIdentifier(metadataField)).append(" @> ?::jsonb");
                parameters.add(toJson(Map.of(key, value)));
            }
        }
    }

    private void bindValues(PreparedStatement statement, List<Object> values, int startIndex) throws SQLException {
        int index = startIndex;
        for (Object value : values) {
            statement.setObject(index++, value);
        }
    }

    private List<SearchResult> readSearchResults(ResultSet resultSet, Boolean denseMode) throws SQLException {
        List<SearchResult> results = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> metadata = readMetadata(resultSet.getObject(metadataField));
            String chunkId = resultSet.getString(chunkIdField);
            String id = chunkId == null || chunkId.isBlank() ? resultSet.getString("id") : chunkId;
            String docId = resultSet.getString(docIdField);
            if (docId != null && !docId.isBlank()) {
                metadata.putIfAbsent("doc_id", docId);
            }
            if (chunkId != null && !chunkId.isBlank()) {
                metadata.putIfAbsent("chunk_id", chunkId);
            }

            double rawScore = 0.0;
            double score = 0.0;
            if (denseMode != null) {
                rawScore = resultSet.getDouble("raw_score");
                metadata.put("raw_score", rawScore);
                if (denseMode) {
                    score = normalizeDenseScore(rawScore);
                    metadata.put("raw_score_scaled", score);
                } else {
                    score = rawScore;
                    metadata.put("raw_score_scaled", rawScore);
                }
            }

            results.add(new SearchResult(id, resultSet.getString(textField), score, metadata));
        }
        return results;
    }

    private Map<String, Object> readMetadata(Object raw) {
        if (raw instanceof PGobject pgObject) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(pgObject.getValue(), LinkedHashMap.class);
                return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
            } catch (JsonProcessingException ex) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "failed to parse PGVector metadata");
            }
        }
        return castMap(raw);
    }

    private double normalizeDenseScore(double rawScore) {
        return switch (distanceMetric) {
            case "euclidean" -> 1.0 / (1.0 + Math.max(rawScore, 0.0));
            case "dot" -> {
                double innerProduct = -rawScore;
                yield Math.max(0.0, Math.min(1.0, (innerProduct + 1.0) / 2.0));
            }
            default -> 1.0 - rawScore;
        };
    }

    private List<SearchResult> weightedFusion(List<SearchResult> dense,
                                              List<SearchResult> sparse,
                                              int topK,
                                              double denseWeight,
                                              double sparseWeight) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, SearchResult> resultsByText = new LinkedHashMap<>();
        mergeWeighted(scores, resultsByText, dense, denseWeight);
        mergeWeighted(scores, resultsByText, sparse, sparseWeight);
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));
        List<SearchResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : ordered) {
            SearchResult result = resultsByText.get(entry.getKey());
            fused.add(new SearchResult(result.getId(), result.getText(), entry.getValue(), result.getMetadata()));
            if (fused.size() >= topK) {
                break;
            }
        }
        return fused;
    }

    private void mergeWeighted(Map<String, Double> scores,
                               Map<String, SearchResult> resultsByText,
                               List<SearchResult> results,
                               double weight) {
        if (results == null || results.isEmpty() || weight <= 0.0) {
            return;
        }
        for (SearchResult result : results) {
            scores.merge(result.getText(), result.getScore() * weight, Double::sum);
            resultsByText.putIfAbsent(result.getText(), result);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)")) {
            statement.setString(1, PUBLIC_SCHEMA);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private Map<String, String> loadColumnTypes(Connection connection, String tableName) throws SQLException {
        Map<String, String> columnTypes = new LinkedHashMap<>();
        String sql = "SELECT a.attname AS column_name, "
                + "format_type(a.atttypid, a.atttypmod) AS column_type "
                + "FROM pg_attribute a "
                + "JOIN pg_class c ON a.attrelid = c.oid "
                + "JOIN pg_namespace n ON c.relnamespace = n.oid "
                + "WHERE n.nspname = ? AND c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, PUBLIC_SCHEMA);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columnTypes.put(resultSet.getString("column_name"), resultSet.getString("column_type"));
                }
            }
        }
        return columnTypes;
    }

    private String createTableSql(String tableName, int dimension) {
        return "CREATE TABLE IF NOT EXISTS " + qualifiedTableName(tableName) + " ("
                + quoteIdentifier("id") + " TEXT PRIMARY KEY, "
                + quoteIdentifier(textField) + " TEXT NOT NULL DEFAULT '', "
                + quoteIdentifier(vectorField) + " vector(" + dimension + "), "
                + quoteIdentifier(metadataField) + " JSONB NOT NULL DEFAULT '{}'::jsonb, "
                + quoteIdentifier(docIdField) + " TEXT, "
                + quoteIdentifier(chunkIdField) + " TEXT"
                + ")";
    }

    private String upsertSql(String tableName) {
        return "INSERT INTO " + qualifiedTableName(tableName) + " ("
                + quoteIdentifier("id") + ", "
                + quoteIdentifier(textField) + ", "
                + quoteIdentifier(vectorField) + ", "
                + quoteIdentifier(metadataField) + ", "
                + quoteIdentifier(docIdField) + ", "
                + quoteIdentifier(chunkIdField)
                + ") VALUES (?, ?, ?, ?::jsonb, ?, ?) "
                + "ON CONFLICT (" + quoteIdentifier("id") + ") DO UPDATE SET "
                + quoteIdentifier(textField) + " = EXCLUDED." + quoteIdentifier(textField) + ", "
                + quoteIdentifier(vectorField) + " = EXCLUDED." + quoteIdentifier(vectorField) + ", "
                + quoteIdentifier(metadataField) + " = EXCLUDED." + quoteIdentifier(metadataField) + ", "
                + quoteIdentifier(docIdField) + " = EXCLUDED." + quoteIdentifier(docIdField) + ", "
                + quoteIdentifier(chunkIdField) + " = EXCLUDED." + quoteIdentifier(chunkIdField);
    }

    private void bindUpsert(PreparedStatement statement, StoredRow row) throws SQLException {
        statement.setString(1, row.id());
        statement.setString(2, row.text());
        if (row.vector() == null || row.vector().isEmpty()) {
            statement.setNull(3, Types.OTHER);
        } else {
            statement.setObject(3, toPgVector(row.vector()));
        }
        statement.setString(4, toJson(row.metadata()));
        statement.setString(5, row.docId());
        statement.setString(6, row.chunkId());
    }

    private StoredRow normalizeRow(Map<String, Object> item) {
        Map<String, Object> metadata = castMap(item == null ? null : item.get(metadataField));
        String chunkId = stringValue(item == null ? null : item.get(chunkIdField));
        if (chunkId == null || chunkId.isBlank()) {
            chunkId = stringValue(item == null ? null : item.get("id"));
        }
        if (chunkId == null || chunkId.isBlank()) {
            chunkId = UUID.randomUUID().toString();
        }
        String id = stringValue(item == null ? null : item.get("id"));
        if (id == null || id.isBlank()) {
            id = chunkId;
        }
        String docId = stringValue(item == null ? null : item.get(docIdField));
        if (docId == null || docId.isBlank()) {
            docId = stringValue(metadata.get("doc_id"));
        }
        if (docId == null || docId.isBlank()) {
            docId = chunkId;
        }
        String text = stringValue(item == null ? null : item.get(textField));
        if (text == null) {
            text = "";
        }
        metadata.putIfAbsent("doc_id", docId);
        metadata.putIfAbsent("chunk_id", chunkId);
        return new StoredRow(id, text, castFloatList(item == null ? null : item.get(vectorField)), metadata, docId, chunkId);
    }

    private int inferDimension(List<Map<String, Object>> data) {
        for (Map<String, Object> item : data) {
            StoredRow row = normalizeRow(item);
            if (row.vector() != null && !row.vector().isEmpty()) {
                return requireVectorDimension(row.vector().size());
            }
        }
        throw RetrievalExceptions.error(
                StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                "vector dimension is required to bootstrap PGVector collection");
    }

    private int requireVectorDimension(Integer dimension) {
        if (dimension == null || dimension <= 0) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "vector dimension is required to bootstrap PGVector collection");
        }
        if (dimension > MAX_VECTOR_DIMENSION) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "pgvector only supports vector dimensions up to " + MAX_VECTOR_DIMENSION + ". Got " + dimension + ".");
        }
        return dimension;
    }

    private String placeholders(int size) {
        List<String> placeholders = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            placeholders.add("?");
        }
        return String.join(", ", placeholders);
    }

    private void bindValues(PreparedStatement statement, List<Object> values) throws SQLException {
        int index = 1;
        for (Object value : values) {
            statement.setObject(index++, value);
        }
    }

    private String annIndexSql(String tableName, Map<String, Object> options) {
        String annIndexType = options != null && options.containsKey("index_type")
                ? String.valueOf(options.get("index_type"))
                : "hnsw";
        if ("ivfflat".equalsIgnoreCase(annIndexType)) {
            int lists = options != null && options.get("lists") instanceof Number number ? number.intValue() : 100;
            return "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "vector_ann_idx"))
                    + " ON " + qualifiedTableName(tableName)
                    + " USING ivfflat (" + quoteIdentifier(vectorField) + " " + operatorClass() + ")"
                    + " WITH (lists = " + lists + ")";
        }
        int m = options != null && options.get("m") instanceof Number number ? number.intValue() : 16;
        int efConstruction = options != null && options.get("ef_construction") instanceof Number number
                ? number.intValue()
                : 64;
        return "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "vector_ann_idx"))
                + " ON " + qualifiedTableName(tableName)
                + " USING hnsw (" + quoteIdentifier(vectorField) + " " + operatorClass() + ")"
                + " WITH (m = " + m + ", ef_construction = " + efConstruction + ")";
    }

    private String docIndexSql(String tableName) {
        return "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "doc_id_idx"))
                + " ON " + qualifiedTableName(tableName)
                + " (" + quoteIdentifier(docIdField) + ")";
    }

    private String chunkIndexSql(String tableName) {
        return "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "chunk_id_idx"))
                + " ON " + qualifiedTableName(tableName)
                + " (" + quoteIdentifier(chunkIdField) + ")";
    }

    private String operatorClass() {
        return switch (distanceMetric) {
            case "euclidean" -> "vector_l2_ops";
            case "dot" -> "vector_ip_ops";
            default -> "vector_cosine_ops";
        };
    }

    private String qualifiedTableName(String tableName) {
        return quoteIdentifier(PUBLIC_SCHEMA) + "." + quoteIdentifier(tableName);
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + requireIdentifier(identifier, "identifier") + "\"";
    }

    private String requireIdentifier(String identifier, String field) {
        RetrievalValidation.requireNonBlank(identifier, field);
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    field + " must match " + IDENTIFIER_PATTERN.pattern());
        }
        return identifier;
    }

    private String resolveDatabaseName(String configuredDatabaseName, String jdbcUrl) {
        String parsedDatabaseName = "";
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
            if (!matcher.matches()) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "PGVectorStore jdbcUrl must be in jdbc:postgresql://host/database form");
            }
            parsedDatabaseName = matcher.group(1);
        }
        if (configuredDatabaseName != null && !configuredDatabaseName.isBlank()) {
            if (!parsedDatabaseName.isBlank() && !configuredDatabaseName.equals(parsedDatabaseName)) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "VectorStoreConfig.databaseName does not match jdbcUrl database name");
            }
            return configuredDatabaseName;
        }
        return parsedDatabaseName;
    }

    private PGvector toPgVector(List<Float> vector) {
        float[] values = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            values[i] = vector.get(i);
        }
        return new PGvector(values);
    }

    private String indexName(String tableName, String suffix) {
        String raw = tableName + "_" + suffix;
        if (raw.length() <= 63) {
            return raw;
        }
        return raw.substring(0, 54) + "_" + Integer.toHexString(raw.hashCode());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private List<Float> castFloatList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return null;
        }
        List<Float> floats = new ArrayList<>(values.size());
        for (Object item : values) {
            if (item instanceof Number number) {
                floats.add(number.floatValue());
            }
        }
        return floats.isEmpty() ? null : floats;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private RuntimeException sqlError(String message, SQLException ex) {
        return RetrievalExceptions.error(
                StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                message + ": " + ex.getMessage());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "failed to serialize PGVector JSON payload");
        }
    }

    private record StoredRow(String id,
                             String text,
                             List<Float> vector,
                             Map<String, Object> metadata,
                             String docId,
                             String chunkId) {
    }
}
