/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.pgvector.PGvector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PG vector store test cases.
 *
 * <p>Mirrors Python's {@code TestPGVectorStore} in
 * {@code tests.unit_tests.core.retrieval.vector_store.test_pg_store}.</p>
 */
@DisplayName("PGVectorStore Tests")
class TestPgStore {

    @Nested
    @DisplayName("Initialization")
    class InitTests {

        @Test
        @DisplayName("test_init - initializes connection settings and metric")
        void testInit() {
            PGVectorStore store = new PGVectorStore(
                    vectorStoreConfig("euclidean"),
                    mock(DataSource.class),
                    "hybrid",
                    Map.of("vector_field", "embedding"));
            PGVectorStore cosineStore = new PGVectorStore(
                    vectorStoreConfig("cosine"),
                    mock(DataSource.class),
                    "hybrid",
                    Map.of("vector_field", "embedding"));

            assertThat(store.getCollectionName()).isEqualTo("test_collection");
            assertThat(store.getDistanceMetric()).isEqualTo("euclidean");
            assertThat(store.getVectorField()).isEqualTo("embedding");
            assertThat(cosineStore.getDistanceMetric()).isEqualTo("cosine");
        }

        @Test
        @DisplayName("test_init_invalid_config - rejects invalid vector field")
        void testInitInvalidConfig() {
            assertThatThrownBy(() -> new PGVectorStore(
                    vectorStoreConfig("euclidean"),
                    mock(DataSource.class),
                    "hybrid",
                    Map.of("vector_field", 123)))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("vectorField must match");
        }
    }

    @Nested
    @DisplayName("Table Operations")
    class TableTests {

        @Test
        @DisplayName("test_get_or_create_table_reflects_existing_table - ensures PGVector schema")
        void testEnsureCollectionCreatesTableAndIndexes() throws SQLException {
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.createStatement()).thenReturn(statement);
            PGVectorStore store = storeWith(vectorStoreConfig("cosine"), connection);

            store.ensureCollection(null, "hybrid", 2, Map.of("index_type", "ivfflat", "lists", 10));

            verify(statement).execute("CREATE EXTENSION IF NOT EXISTS vector");
            verify(statement).execute(sqlContaining("CREATE TABLE IF NOT EXISTS \"public\".\"test_collection\""));
            verify(statement).execute(sqlContaining("USING ivfflat"));
            verify(connection).setAutoCommit(false);
            verify(connection).commit();
            verify(connection).setAutoCommit(true);
        }
    }

    @Nested
    @DisplayName("CRUD")
    class CrudTests {

        @Test
        @DisplayName("test_crud_lifecycle - batches add operations")
        void testCrudLifecycle() throws SQLException {
            Connection connection = mock(Connection.class);
            PreparedStatement upsert = mock(PreparedStatement.class);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(sqlContaining("INSERT INTO"))).thenReturn(upsert);
            PGVectorStore store = noEnsureStoreWith(vectorStoreConfig("euclidean"), connection);

            store.add(rows(150), 100, Map.of());

            verify(upsert, org.mockito.Mockito.times(150)).addBatch();
            verify(upsert, org.mockito.Mockito.times(2)).executeBatch();
            verify(connection).commit();
        }

        @Test
        @DisplayName("test_delete_by_ids - deletes rows and reports missing table")
        void testDeleteByIds() throws SQLException {
            Connection connection = mock(Connection.class);
            PreparedStatement deleteStatement = mock(PreparedStatement.class);
            stubTableExists(connection, true);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(sqlContaining("DELETE FROM"))).thenReturn(deleteStatement);
            when(deleteStatement.executeUpdate()).thenReturn(1);
            PGVectorStore store = storeWith(vectorStoreConfig("euclidean"), connection);

            boolean deleted = store.delete(List.of("1", "2"), null, Map.of());

            assertThat(deleted).isTrue();
            verify(deleteStatement).setObject(1, "1");
            verify(deleteStatement).setObject(2, "2");
            verify(deleteStatement).setObject(3, "1");
            verify(deleteStatement).setObject(4, "2");
            verify(connection).commit();

            Connection missingConnection = mock(Connection.class);
            stubTableExists(missingConnection, false);
            PGVectorStore missingStore = storeWith(vectorStoreConfig("euclidean"), missingConnection);

            assertThat(missingStore.delete(List.of("1"), null, Map.of())).isFalse();
        }
    }

    @Nested
    @DisplayName("Search")
    class SearchTests {

        @Test
        @DisplayName("test_search_metric_handling - supports euclidean and cosine metrics")
        void testSearchMetricHandling() throws SQLException {
            Connection euclideanConnection = mock(Connection.class);
            PreparedStatement euclideanQuery = stubSearch(euclideanConnection, 0.5, "euclidean result");
            PGVectorStore euclideanStore = storeWith(vectorStoreConfig("euclidean"), euclideanConnection);

            List<SearchResult> euclideanResults = euclideanStore.search(vector(2), 5, null, Map.of());

            assertThat(euclideanResults).hasSize(1);
            assertThat(euclideanResults.get(0).getText()).isEqualTo("euclidean result");
            assertThat(euclideanResults.get(0).getScore()).isBetween(0.66, 0.67);
            verify(euclideanConnection).prepareStatement(sqlContaining("\"embedding\" <-> ? AS raw_score"));
            verify(euclideanQuery).setObject(eq(1), isA(PGvector.class));
            verify(euclideanQuery).setObject(2, 5);

            Connection cosineConnection = mock(Connection.class);
            stubSearch(cosineConnection, 0.25, "cosine result");
            PGVectorStore cosineStore = storeWith(vectorStoreConfig("cosine"), cosineConnection);

            List<SearchResult> cosineResults = cosineStore.search(vector(2), 5, null, Map.of());

            assertThat(cosineResults).hasSize(1);
            assertThat(cosineResults.get(0).getScore()).isEqualTo(0.75);
            verify(cosineConnection).prepareStatement(sqlContaining("\"embedding\" <=> ? AS raw_score"));
        }

        @Test
        @DisplayName("test_sparse_search - returns ranked text matches")
        void testSparseSearch() throws SQLException {
            Connection connection = mock(Connection.class);
            PreparedStatement query = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);
            stubTableExists(connection, true);
            when(connection.prepareStatement(sqlContaining("ts_rank"))).thenReturn(query);
            when(query.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("metadata")).thenReturn(Map.of());
            when(resultSet.getString("chunk_id")).thenReturn("chunk_1");
            when(resultSet.getString("doc_id")).thenReturn("doc_1");
            when(resultSet.getString("id")).thenReturn("1");
            when(resultSet.getString("text")).thenReturn("text");
            when(resultSet.getDouble("raw_score")).thenReturn(0.9);
            PGVectorStore store = storeWith(vectorStoreConfig("euclidean"), connection);

            List<SearchResult> results = store.sparseSearch("query", 5, null, Map.of());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getScore()).isEqualTo(0.9);
            verify(query).setString(1, "query");
            verify(query).setString(2, "query");
            verify(query).setObject(3, 5);
        }

        @Test
        @DisplayName("test_hybrid_search - fuses dense and sparse results")
        void testHybridSearch() {
            HybridPGVectorStore store = new HybridPGVectorStore(
                    List.of(new SearchResult("1", "t1", 0.9, Map.of("id", "1"))),
                    List.of(
                            new SearchResult("1", "t1", 0.8, Map.of("id", "1")),
                            new SearchResult("2", "t2", 0.7, Map.of("id", "2"))));

            List<SearchResult> results = store.hybridSearch("q", vector(1), 5, 0.5, null, Map.of());

            assertThat(store.searchCalled).isTrue();
            assertThat(store.sparseSearchCalled).isTrue();
            assertThat(results).extracting(SearchResult::getId).contains("1", "2");
        }
    }

    @Nested
    @DisplayName("Validation and Errors")
    class ValidationTests {

        @Test
        @DisplayName("test_exception_handling - wraps DB errors")
        void testExceptionHandling() throws SQLException {
            Connection addConnection = mock(Connection.class);
            PreparedStatement upsert = mock(PreparedStatement.class);
            when(addConnection.prepareStatement(sqlContaining("INSERT INTO"))).thenReturn(upsert);
            when(upsert.executeBatch()).thenThrow(new SQLException("DB Connection Lost"));
            PGVectorStore addStore = noEnsureStoreWith(vectorStoreConfig("euclidean"), addConnection);

            assertThatThrownBy(() -> addStore.add(rows(1), null, Map.of()))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("DB Connection Lost");
            verify(addConnection).rollback();

            Connection searchConnection = mock(Connection.class);
            PreparedStatement query = mock(PreparedStatement.class);
            stubTableExists(searchConnection, true);
            when(searchConnection.prepareStatement(sqlContaining("\"embedding\" <-> ? AS raw_score"))).thenReturn(query);
            when(query.executeQuery()).thenThrow(new SQLException("Query Failed"));
            PGVectorStore searchStore = storeWith(vectorStoreConfig("euclidean"), searchConnection);

            assertThatThrownBy(() -> searchStore.search(vector(2), 5, null, Map.of()))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("Query Failed");
        }

        @Test
        @DisplayName("test_dimension_validation - enforces pgvector dimension limit")
        void testDimensionValidation() throws SQLException {
            PGVectorStore badStore = noEnsureStoreWith(vectorStoreConfig("euclidean"));
            Map<String, Object> tooLong = Map.of(
                    "id", "1",
                    "text", "t",
                    "embedding", vector(2001));

            assertThatThrownBy(() -> badStore.add(List.of(tooLong), null, Map.of()))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("pgvector only supports vector dimensions up to 2000");

            Connection connection = mock(Connection.class);
            PreparedStatement upsert = mock(PreparedStatement.class);
            when(connection.prepareStatement(sqlContaining("INSERT INTO"))).thenReturn(upsert);
            PGVectorStore goodStore = noEnsureStoreWith(vectorStoreConfig("euclidean"), connection);
            Map<String, Object> maxDim = Map.of(
                    "id", "1",
                    "text", "t",
                    "embedding", vector(2000));

            assertThatCode(() -> goodStore.add(List.of(maxDim), null, Map.of()))
                    .doesNotThrowAnyException();
        }
    }

    private static VectorStoreConfig vectorStoreConfig(String distanceMetric) {
        return new VectorStoreConfig("pgvector", "", "test_collection", distanceMetric);
    }

    private static PGVectorStore storeWith(VectorStoreConfig config, Connection... connections) {
        return new TestablePGVectorStore(config, false, connections);
    }

    private static PGVectorStore noEnsureStoreWith(VectorStoreConfig config, Connection... connections) {
        return new TestablePGVectorStore(config, true, connections);
    }

    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(Map.of(
                    "id", String.valueOf(i),
                    "text", "text" + i,
                    "embedding", vector(2),
                    "metadata", Map.of("i", i)));
        }
        return rows;
    }

    private static List<Float> vector(int dimension) {
        List<Float> values = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            values.add(i == 0 ? 1.0f : 0.0f);
        }
        return values;
    }

    private static PreparedStatement stubSearch(Connection connection, double rawScore, String text) throws SQLException {
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        stubTableExists(connection, true);
        when(connection.prepareStatement(sqlContaining("\"embedding\""))).thenReturn(query);
        when(query.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject("metadata")).thenReturn(Map.of());
        when(resultSet.getString("chunk_id")).thenReturn("chunk_1");
        when(resultSet.getString("doc_id")).thenReturn("doc_1");
        when(resultSet.getString("id")).thenReturn("1");
        when(resultSet.getString("text")).thenReturn(text);
        when(resultSet.getDouble("raw_score")).thenReturn(rawScore);
        return query;
    }

    private static PreparedStatement stubTableExists(Connection connection, boolean exists) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(sqlContaining("SELECT EXISTS"))).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(exists);
        return statement;
    }

    private static String sqlContaining(String fragment) {
        return argThat(sql -> sql != null && sql.contains(fragment));
    }

    private static class TestablePGVectorStore extends PGVectorStore {
        private final Queue<Connection> connections;
        private final boolean skipEnsureCollection;

        TestablePGVectorStore(VectorStoreConfig config, boolean skipEnsureCollection, Connection... connections) {
            super(config, mock(DataSource.class), "hybrid", Map.of("vector_field", "embedding"));
            this.skipEnsureCollection = skipEnsureCollection;
            this.connections = new ArrayDeque<>(List.of(connections));
        }

        @Override
        public void ensureCollection(String collectionName,
                                     String indexType,
                                     Integer dimension,
                                     Map<String, Object> options) {
            if (skipEnsureCollection) {
                return;
            }
            super.ensureCollection(collectionName, indexType, dimension, options);
        }

        @Override
        protected Connection openConnection() throws SQLException {
            if (connections.isEmpty()) {
                throw new SQLException("No mock connection configured");
            }
            return connections.remove();
        }

        @Override
        protected void registerVectorTypes(Connection connection) {
        }
    }

    private static final class HybridPGVectorStore extends PGVectorStore {
        private final List<SearchResult> dense;
        private final List<SearchResult> sparse;
        private boolean searchCalled;
        private boolean sparseSearchCalled;

        private HybridPGVectorStore(List<SearchResult> dense, List<SearchResult> sparse) {
            super(vectorStoreConfig("euclidean"), mock(DataSource.class), "hybrid", Map.of("vector_field", "embedding"));
            this.dense = dense;
            this.sparse = sparse;
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector,
                                         int topK,
                                         Map<String, Object> filters,
                                         Map<String, Object> options) {
            searchCalled = true;
            return dense;
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText,
                                               int topK,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            sparseSearchCalled = true;
            return sparse;
        }
    }
}
