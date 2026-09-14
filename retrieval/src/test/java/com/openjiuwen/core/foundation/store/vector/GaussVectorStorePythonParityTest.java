/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.foundation.store.test_gauss_vector_store} in
 * {@code tests/unit_tests/core/foundation/store/test_gauss_vector_store.py}.</p>
 */
class GaussVectorStorePythonParityTest {

    @TestFactory
    Collection<DynamicTest> pythonGaussVectorStoreCases() {
        return List.of(
                dynamic("test_init_with_default_params", this::initWithDefaultParams),
                dynamic("test_init_with_custom_params", this::initWithCustomParams),
                dynamic("test_lazy_init_no_connection_on_init", this::lazyInitNoConnectionOnInit),
                dynamic("test_connection_reuse", this::connectionReuse),
                dynamic("test_close_and_reconnect", this::closeAndReconnect),
                dynamic("test_create_collection_with_schema_object", this::createCollectionWithSchemaObject),
                dynamic("test_create_collection_with_dict_schema", this::createCollectionWithDictSchema),
                dynamic("test_create_collection_with_custom_metric", this::createCollectionWithCustomMetric),
                dynamic("test_create_collection_already_exists", this::createCollectionAlreadyExists),
                dynamic("test_create_collection_missing_vector_dim", this::createCollectionMissingVectorDim),
                dynamic("test_create_collection_missing_vector_field", this::createCollectionMissingVectorField),
                dynamic("test_create_collection_with_auto_id", this::createCollectionWithAutoId),
                dynamic("test_delete_collection_success", this::deleteCollectionSuccess),
                dynamic("test_delete_collection_not_exists", this::deleteCollectionNotExists),
                dynamic("test_collection_exists_true", this::collectionExistsTrue),
                dynamic("test_collection_exists_false", this::collectionExistsFalse),
                dynamic("test_get_schema_success", this::getSchemaSuccess),
                dynamic("test_get_schema_collection_not_exists", this::getSchemaCollectionNotExists),
                dynamic("test_add_docs_success", this::addDocsSuccess),
                dynamic("test_add_docs_with_batch_size", this::addDocsWithBatchSize),
                dynamic("test_add_docs_with_json_metadata", this::addDocsWithJsonMetadata),
                dynamic("test_search_success", this::searchSuccess),
                dynamic("test_search_with_filters", this::searchWithFilters),
                dynamic("test_search_cosine_metric", this::searchCosineMetric),
                dynamic("test_search_l2_metric", this::searchL2Metric),
                dynamic("test_delete_docs_by_ids_success", this::deleteDocsByIdsSuccess),
                dynamic("test_delete_docs_by_ids_empty_list", this::deleteDocsByIdsEmptyList),
                dynamic("test_delete_docs_by_filters_success", this::deleteDocsByFiltersSuccess),
                dynamic("test_delete_docs_by_filters_empty", this::deleteDocsByFiltersEmpty),
                dynamic("test_list_collection_names_success", this::listCollectionNamesSuccess),
                dynamic("test_get_collection_metadata_from_cache", this::getCollectionMetadataFromCache),
                dynamic("test_get_collection_metadata_not_exists", this::getCollectionMetadataNotExists),
                dynamic("test_close_connection", this::closeConnection)
        );
    }

    private void initWithDefaultParams() throws Exception {
        GaussVectorStore store = new GaussVectorStore();

        assertThat(rawField(store, "host")).isEqualTo("localhost");
        assertThat(rawField(store, "port")).isEqualTo(5432);
        assertThat(rawField(store, "database")).isEqualTo("postgres");
        assertThat(rawField(store, "user")).isEqualTo("postgres");
        assertThat(rawField(store, "password")).isEqualTo("");
    }

    private void initWithCustomParams() throws Exception {
        GaussVectorStore store = new GaussVectorStore("testhost", 5433, "testdb", "testuser", "testpass",
                Map.of(), new RecordingConnectionFactory());

        assertThat(rawField(store, "host")).isEqualTo("testhost");
        assertThat(rawField(store, "port")).isEqualTo(5433);
        assertThat(rawField(store, "database")).isEqualTo("testdb");
        assertThat(rawField(store, "user")).isEqualTo("testuser");
        assertThat(rawField(store, "password")).isEqualTo("testpass");
    }

    private void lazyInitNoConnectionOnInit() {
        RecordingConnectionFactory factory = new RecordingConnectionFactory();

        store(factory);

        assertThat(factory.connectCalls).isZero();
    }

    private void connectionReuse() {
        RecordingConnectionFactory factory = new RecordingConnectionFactory();
        GaussVectorStore store = store(factory);

        GaussVectorStore.SqlConnection first = store.connection();
        GaussVectorStore.SqlConnection second = store.connection();

        assertSame(first, second);
        assertThat(factory.connectCalls).isEqualTo(1);
    }

    private void closeAndReconnect() {
        FakeConnection first = new FakeConnection();
        FakeConnection second = new FakeConnection();
        RecordingConnectionFactory factory = new RecordingConnectionFactory(first, second);
        GaussVectorStore store = store(factory);

        GaussVectorStore.SqlConnection firstConnection = store.connection();
        store.close();
        GaussVectorStore.SqlConnection secondConnection = store.connection();

        assertThat(factory.connectCalls).isEqualTo(2);
        assertSame(first, firstConnection);
        assertSame(second, secondConnection);
        assertThat(first.closed).isTrue();
    }

    private void createCollectionWithSchemaObject() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        store.createCollection("test_collection", schemaWithText(), Map.of()).join();

        assertThat(connection.cursor.executedSqls).hasSizeGreaterThanOrEqualTo(3);
        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE TABLE test_collection")
                .contains("embedding floatvector(768)")
                .contains("text VARCHAR(65535)"));
        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql).contains("CREATE INDEX"));
    }

    private void createCollectionWithDictSchema() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        store.createCollection("test_collection", schemaDict(), Map.of()).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE TABLE test_collection")
                .contains("id VARCHAR PRIMARY KEY")
                .contains("embedding floatvector(768)")
                .contains("text VARCHAR(65535)"));
    }

    private void createCollectionWithCustomMetric() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        store.createCollection("test_collection", schema(), Map.of("distance_metric", "L2")).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE INDEX")
                .contains("embedding l2"));
    }

    private void createCollectionAlreadyExists() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(true)));
        GaussVectorStore store = store(connection);

        store.createCollection("test_collection", schema(), Map.of()).join();

        assertThat(connection.cursor.executedSqls).hasSize(1);
        assertThat(connection.cursor.executedSqls).noneSatisfy(sql -> assertThat(sql).contains("CREATE TABLE"));
        assertThat(connection.cursor.executedSqls).noneSatisfy(sql -> assertThat(sql).contains("CREATE INDEX"));
    }

    private void createCollectionMissingVectorDim() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        BaseError error = assertBaseError(
                () -> store.createCollection("test_collection", schemaDictWithoutVectorDim(), Map.of()).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    private void createCollectionMissingVectorField() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        BaseError error = assertBaseError(
                () -> store.createCollection("test_collection", schemaWithoutVector(), Map.of()).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    private void createCollectionWithAutoId() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        store.createCollection("test_collection", schemaWithAutoId(), Map.of()).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE TABLE test_collection")
                .contains("id SERIAL PRIMARY KEY"));
    }

    private void deleteCollectionSuccess() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(true)));
        GaussVectorStore store = store(connection);

        store.deleteCollection("test_collection", Map.of()).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("DROP TABLE IF EXISTS test_collection CASCADE"));
    }

    private void deleteCollectionNotExists() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        store.deleteCollection("test_collection", Map.of()).join();

        assertThat(connection.cursor.executedSqls).noneSatisfy(sql -> assertThat(sql).contains("DROP TABLE"));
    }

    private void collectionExistsTrue() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(true)));
        GaussVectorStore store = store(connection);

        assertThat(store.collectionExists("test_collection", Map.of()).join()).isTrue();
    }

    private void collectionExistsFalse() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        assertThat(store.collectionExists("test_collection", Map.of()).join()).isFalse();
    }

    private void getSchemaSuccess() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(true)));
        connection.cursor.whenContains("FROM information_schema.columns", List.of("column_name", "data_type"),
                List.of(
                        row("id", "character varying", "YES", null),
                        row("embedding", "floatvector(128)", "YES", null),
                        row("text", "text", "YES", null)
                ));
        connection.cursor.whenContains("PRIMARY KEY", List.of("column_name"), List.of(row("id")));
        GaussVectorStore store = store(connection);

        CollectionSchema schema = store.getSchema("test_collection", Map.of()).join();

        assertThat(schema.getFields()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(schema.getField("id").isPrimary()).isTrue();
        assertThat(schema.getField("embedding").getDtype()).isEqualTo(VectorDataType.FLOAT_VECTOR);
        assertThat(schema.getField("embedding").getDim()).isEqualTo(128);
        assertThat(schema.getField("text").getDtype()).isEqualTo(VectorDataType.VARCHAR);
    }

    private void getSchemaCollectionNotExists() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        BaseError error = assertBaseError(() -> store.getSchema("non_existent_collection", Map.of()).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND);
    }

    private void addDocsSuccess() {
        FakeConnection connection = new FakeConnection();
        GaussVectorStore store = store(connection);

        store.addDocs("test_collection", List.of(
                mapOf("id", "doc1", "embedding", List.of(0.1d, 0.2d, 0.3d), "text", "Test document 1"),
                mapOf("id", "doc2", "embedding", List.of(0.4d, 0.5d, 0.6d), "text", "Test document 2")
        ), Map.of()).join();

        assertThat(connection.cursor.executedManySqls).hasSize(1);
    }

    private void addDocsWithBatchSize() {
        FakeConnection connection = new FakeConnection();
        GaussVectorStore store = store(connection);
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            docs.add(mapOf("id", "doc" + index, "embedding", List.of(0.1d, 0.2d, 0.3d),
                    "text", "Test document " + index));
        }

        store.addDocs("test_collection", docs, Map.of("batch_size", 3)).join();

        assertThat(connection.cursor.executedManySqls).hasSize(4);
    }

    private void addDocsWithJsonMetadata() {
        FakeConnection connection = new FakeConnection();
        GaussVectorStore store = store(connection);

        store.addDocs("test_collection", List.of(
                mapOf("id", "doc1", "embedding", List.of(0.1d, 0.2d, 0.3d),
                        "text", "Test document 1", "metadata", mapOf("source", "test", "page", 1))
        ), Map.of()).join();

        assertThat(connection.cursor.executedManyValues).hasSize(1);
        assertThat(connection.cursor.executedManyValues.get(0).get(0).get(3).toString())
                .contains("\"source\":\"test\"", "\"page\":1");
    }

    private void searchSuccess() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("ORDER BY distance", List.of("id", "text", "distance"),
                List.of(row("doc1", "Text 1", 0.1d), row("doc2", "Text 2", 0.3d)));
        GaussVectorStore store = store(connection);

        List<VectorSearchResult> results = store.search("test_collection", List.of(0.1d, 0.2d, 0.3d),
                "embedding", 5, null, Map.of()).join();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getFields()).containsEntry("id", "doc1");
        assertThat(results.get(0).getFields()).containsEntry("text", "Text 1");
    }

    private void searchWithFilters() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("ORDER BY distance", List.of("id", "category", "distance"),
                List.of(row("doc1", "tech", 0.1d)));
        GaussVectorStore store = store(connection);

        List<VectorSearchResult> results = store.search("test_collection", List.of(0.1d, 0.2d, 0.3d),
                "embedding", 5, Map.of("category", "tech"), Map.of()).join();

        assertThat(results).hasSize(1);
        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("WHERE")
                .contains("category = 'tech'"));
    }

    private void searchCosineMetric() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("ORDER BY distance", List.of("id", "distance"),
                List.of(row("doc1", 0.0d), row("doc2", 1.0d)));
        GaussVectorStore store = store(connection);

        List<VectorSearchResult> results = store.search("test_collection", List.of(0.1d, 0.2d, 0.3d),
                "embedding", 5, null, Map.of("metric_type", "COSINE")).join();

        assertThat(results).extracting(VectorSearchResult::getScore).containsExactly(1.0d, 0.5d);
        assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(results.get(1).getScore());
    }

    private void searchL2Metric() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("ORDER BY distance", List.of("id", "distance"),
                List.of(row("doc1", 0.5d), row("doc2", 1.5d)));
        GaussVectorStore store = store(connection);

        List<VectorSearchResult> results = store.search("test_collection", List.of(0.1d, 0.2d, 0.3d),
                "embedding", 5, null, Map.of("metric_type", "L2")).join();

        assertThat(results).extracting(VectorSearchResult::getScore).containsExactly(0.875d, 0.625d);
        assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(results.get(1).getScore());
    }

    private void deleteDocsByIdsSuccess() {
        FakeConnection connection = new FakeConnection();
        GaussVectorStore store = store(connection);

        store.deleteDocsByIds("test_collection", List.of("doc1", "doc2"), Map.of()).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("DELETE FROM test_collection")
                .contains("IN (?, ?)"));
    }

    private void deleteDocsByIdsEmptyList() {
        RecordingConnectionFactory factory = new RecordingConnectionFactory();
        GaussVectorStore store = store(factory);

        store.deleteDocsByIds("test_collection", List.of(), Map.of()).join();

        assertThat(factory.connectCalls).isZero();
    }

    private void deleteDocsByFiltersSuccess() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT COUNT", List.of("count"), List.of(row(5)));
        GaussVectorStore store = store(connection);

        store.deleteDocsByFilters("test_collection", Map.of("source", "test"), Map.of()).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("SELECT COUNT(*) FROM test_collection WHERE source = 'test'"));
        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("DELETE FROM test_collection WHERE source = 'test'"));
    }

    private void deleteDocsByFiltersEmpty() {
        RecordingConnectionFactory factory = new RecordingConnectionFactory();
        GaussVectorStore store = store(factory);

        store.deleteDocsByFilters("test_collection", Map.of(), Map.of()).join();

        assertThat(factory.connectCalls).isZero();
    }

    private void listCollectionNamesSuccess() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("table_type = 'BASE TABLE'", List.of("table_name"),
                List.of(row("collection1"), row("collection2"), row("collection3")));
        GaussVectorStore store = store(connection);

        List<String> collectionNames = store.listCollectionNames().join();

        assertThat(collectionNames).containsExactly("collection1", "collection2", "collection3");
    }

    private void getCollectionMetadataFromCache() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(true)));
        connection.cursor.whenContains("data_type LIKE", List.of("column_name", "data_type"),
                List.of(row("embedding", "floatvector(768)")));
        GaussVectorStore store = store(connection);

        Map<String, Object> first = store.getCollectionMetadata("test_collection").join();
        int executedBeforeSecondRead = connection.cursor.executedSqls.size();
        Map<String, Object> second = store.getCollectionMetadata("test_collection").join();

        assertThat(first).containsEntry("distance_metric", "COSINE")
                .containsEntry("vector_field", "embedding")
                .containsEntry("schema_version", 0);
        assertThat(second).isEqualTo(first);
        assertThat(connection.cursor.executedSqls).hasSize(executedBeforeSecondRead);
    }

    private void getCollectionMetadataNotExists() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(row(false)));
        GaussVectorStore store = store(connection);

        Map<String, Object> metadata = store.getCollectionMetadata("non_existent_collection").join();

        assertThat(metadata).containsEntry("distance_metric", "COSINE")
                .containsEntry("schema_version", 0);
        assertThat(metadata).doesNotContainKey("vector_field");
    }

    private void closeConnection() throws Exception {
        FakeConnection connection = new FakeConnection();
        GaussVectorStore store = store(connection);

        store.connection();
        store.close();

        assertThat(connection.closed).isTrue();
        assertThat(rawField(store, "connection")).isNull();
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static GaussVectorStore store(FakeConnection connection) {
        return store(new RecordingConnectionFactory(connection));
    }

    private static GaussVectorStore store(RecordingConnectionFactory factory) {
        return new GaussVectorStore("testhost", 5432, "postgres", "postgres", "", Map.of(), factory);
    }

    private static CollectionSchema schema() {
        CollectionSchema schema = new CollectionSchema(List.of(), "Test collection", false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                null, null, null, null));
        schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, 768,
                null, null, null, null));
        return schema;
    }

    private static CollectionSchema schemaWithText() {
        CollectionSchema schema = schema();
        schema.addField(new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null,
                null, null, null, null));
        return schema;
    }

    private static CollectionSchema schemaWithoutVector() {
        CollectionSchema schema = new CollectionSchema(List.of(), "Test collection", false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                null, null, null, null));
        return schema;
    }

    private static CollectionSchema schemaWithAutoId() {
        CollectionSchema schema = new CollectionSchema(List.of(), "Test collection", false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, true, 256, null,
                null, null, null, null));
        schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, 768,
                null, null, null, null));
        return schema;
    }

    private static Map<String, Object> schemaDict() {
        return mapOf(
                "fields", List.of(
                        mapOf("name", "id", "type", "VARCHAR", "max_length", 256, "is_primary", true),
                        mapOf("name", "embedding", "type", "FLOAT_VECTOR", "dim", 768),
                        mapOf("name", "text", "type", "VARCHAR", "max_length", 65535)
                ),
                "description", "Test collection",
                "enable_dynamic_field", false
        );
    }

    private static Map<String, Object> schemaDictWithoutVectorDim() {
        return mapOf(
                "fields", List.of(
                        mapOf("name", "id", "type", "VARCHAR", "max_length", 256, "is_primary", true),
                        mapOf("name", "embedding", "type", "FLOAT_VECTOR")
                ),
                "description", "Test collection",
                "enable_dynamic_field", false
        );
    }

    private static List<Object> row(Object... values) {
        List<Object> row = new ArrayList<>();
        for (Object value : values) {
            row.add(value);
        }
        return row;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private static Object rawField(GaussVectorStore store, String fieldName) throws Exception {
        Field field = GaussVectorStore.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(store);
    }

    private static BaseError assertBaseError(Runnable action) {
        try {
            action.run();
        } catch (BaseError error) {
            return error;
        } catch (CompletionException exception) {
            assertThat(exception.getCause()).isInstanceOf(BaseError.class);
            return (BaseError) exception.getCause();
        }
        throw new AssertionError("Expected BaseError");
    }

    private static final class RecordingConnectionFactory implements GaussVectorStore.SqlConnectionFactory {

        private final List<FakeConnection> scriptedConnections;
        private int connectCalls;

        private RecordingConnectionFactory(FakeConnection... scriptedConnections) {
            this.scriptedConnections = new ArrayList<>(List.of(scriptedConnections));
        }

        @Override
        public GaussVectorStore.SqlConnection connect(String host, int port, String database, String user,
                String password, Map<String, Object> kwargs) {
            FakeConnection connection;
            if (scriptedConnections.isEmpty()) {
                connection = new FakeConnection();
                scriptedConnections.add(connection);
            } else {
                connection = scriptedConnections.get(Math.min(connectCalls, scriptedConnections.size() - 1));
            }
            connectCalls++;
            return connection;
        }
    }

    private static final class FakeConnection implements GaussVectorStore.SqlConnection {

        private final FakeCursor cursor = new FakeCursor();
        private boolean autoCommit;
        private boolean closed;

        @Override
        public GaussVectorStore.SqlCursor cursor() {
            return cursor;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeCursor implements GaussVectorStore.SqlCursor {

        private final List<String> executedSqls = new ArrayList<>();
        private final List<List<Object>> executedParams = new ArrayList<>();
        private final List<String> executedManySqls = new ArrayList<>();
        private final List<List<List<Object>>> executedManyValues = new ArrayList<>();
        private final List<ResultScript> scripts = new ArrayList<>();
        private List<List<Object>> rows = List.of();
        private List<String> columns = List.of();

        private void whenContains(String marker, List<String> columns, List<List<Object>> rows) {
            scripts.add(new ResultScript(marker, columns, rows));
        }

        @Override
        public void execute(String sql, Object... params) {
            executedSqls.add(sql);
            executedParams.add(params == null ? List.of() : List.of(params));
            rows = List.of();
            columns = List.of();
            for (ResultScript script : scripts) {
                if (sql.contains(script.marker())) {
                    rows = script.rows();
                    columns = script.columns();
                    return;
                }
            }
        }

        @Override
        public void executeMany(String sql, List<List<Object>> values) {
            executedManySqls.add(sql);
            executedManyValues.add(values);
        }

        @Override
        public List<Object> fetchOne() {
            return rows.isEmpty() ? List.of() : rows.get(0);
        }

        @Override
        public List<List<Object>> fetchAll() {
            return rows;
        }

        @Override
        public List<String> columns() {
            return columns;
        }

        @Override
        public void close() {
            // Mirrors MagicMock cursor close: no observable side effect needed.
        }
    }

    private record ResultScript(String marker, List<String> columns, List<List<Object>> rows) {
    }
}
