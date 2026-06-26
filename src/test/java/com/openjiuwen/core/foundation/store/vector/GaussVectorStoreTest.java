/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link GaussVectorStore}.
 *
 * <p>Mirrors Python's {@code GaussVectorStore} in
 * {@code openjiuwen/core/foundation/store/vector/gauss_vector_store.py}.</p>
 */
class GaussVectorStoreTest {

    @Test
    void mapsTypesFiltersAndScoresLikePythonHelpers() {
        GaussVectorStore store = store(new FakeConnection());

        assertThat(store.mapFieldTypeToPg(VectorDataType.FLOAT_VECTOR)).isEqualTo("floatvector");
        assertThat(store.mapFieldTypeToPg(VectorDataType.INT64)).isEqualTo("BIGINT");
        assertThat(store.mapPgTypeToOurType("floatvector(3)")).isEqualTo(VectorDataType.FLOAT_VECTOR);
        assertThat(store.mapPgTypeToOurType("double precision")).isEqualTo(VectorDataType.DOUBLE);
        assertThat(store.buildFilterClause(new LinkedHashMap<>(Map.of(
                "name", "alice",
                "active", true,
                "age", 7
        )))).contains("name = 'alice'", "active = TRUE", "age = 7");
        assertThat(store.score("COSINE", 0.25D)).isEqualTo(0.875D);
        assertThat(store.score("L2", 1.0D)).isEqualTo(0.75D);
        assertThat(store.score("IP", 0.5D)).isEqualTo(0.75D);
    }

    @Test
    void createCollectionBuildsGaussVectorTableAndDiskannIndex() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(List.of(false)));
        GaussVectorStore store = store(connection);
        CollectionSchema schema = new CollectionSchema(List.of(), "docs", false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 128, null,
                null, null, null, null));
        schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, 3,
                null, null, null, null));
        schema.addField(new FieldSchema("metadata", VectorDataType.JSON, false, false, null, null,
                null, null, null, null));

        store.createCollection("docs", schema, Map.of(
                "distance_metric", "L2",
                "pg_nseg", 64,
                "pg_nclus", 8,
                "num_parallels", 4
        )).join();

        assertThat(connection.autoCommit).isTrue();
        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE TABLE docs")
                .contains("id VARCHAR PRIMARY KEY")
                .contains("embedding floatvector(3)")
                .contains("metadata JSONB"));
        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("USING GSDISKANN")
                .contains("embedding l2")
                .contains("pg_nseg = 64")
                .contains("pg_nclus = 8")
                .contains("num_parallels = 4"));
        assertThat(store.getCollectionMetadata("docs").join())
                .containsEntry("distance_metric", "L2")
                .containsEntry("vector_field", "embedding")
                .containsEntry("vector_dim", 3);
    }

    @Test
    void addDocsBatchesRowsAndSerializesJsonPayloads() {
        FakeConnection connection = new FakeConnection();
        GaussVectorStore store = store(connection);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", "a");
        first.put("embedding", List.of(0.1D, 0.2D));
        first.put("metadata", Map.of("source", "unit"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", "b");
        second.put("embedding", List.of(0.3D, 0.4D));
        second.put("metadata", Map.of("source", "test"));

        store.addDocs("docs", List.of(first, second), Map.of("batch_size", 1)).join();

        assertThat(connection.cursor.executedManySqls)
                .allSatisfy(sql -> assertThat(sql).contains("INSERT INTO docs (id, embedding, metadata) VALUES"));
        assertThat(connection.cursor.executedManyValues).hasSize(2);
        assertThat(connection.cursor.executedManyValues.get(0).get(0).get(1))
                .isEqualTo("[0.1,0.2]");
        assertThat(connection.cursor.executedManyValues.get(0).get(0).get(2))
                .isEqualTo("{\"source\":\"unit\"}");
    }

    @Test
    void searchBuildsVectorQueryParsesJsonAndNormalizesDistance() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("ORDER BY distance", List.of("id", "metadata", "distance"),
                List.of(List.of("a", "{'source':'unit'}", 0.5D)));
        GaussVectorStore store = store(connection);

        List<VectorSearchResult> results = store.search(
                "docs",
                List.of(0.1D, 0.2D),
                "embedding",
                3,
                Map.of("active", false),
                Map.of("metric_type", "COSINE", "output_fields", List.of("id", "metadata"))
        ).join();

        assertThat(connection.cursor.executedSqls).anySatisfy(sql -> assertThat(sql)
                .contains("SELECT id, metadata, embedding")
                .contains("embedding <-> '[0.1,0.2]'::floatvector AS distance")
                .contains("WHERE active = FALSE")
                .contains("LIMIT 3"));
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getScore()).isEqualTo(0.75D);
            assertThat(result.getFields()).containsEntry("id", "a");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.getFields().get("metadata");
            assertThat(metadata).containsEntry("source", "unit");
        });
    }

    @Test
    void getSchemaMapsColumnsAndPrimaryKeys() {
        FakeConnection connection = new FakeConnection();
        connection.cursor.whenContains("SELECT EXISTS", List.of("exists"), List.of(List.of(true)));
        connection.cursor.whenContains("FROM information_schema.columns", List.of("column_name", "data_type"),
                List.of(
                        row("id", "varchar(128)", "NO", null),
                        row("embedding", "floatvector(4)", "YES", null),
                        row("enabled", "boolean", "YES", null)
                ));
        connection.cursor.whenContains("PRIMARY KEY", List.of("column_name"), List.of(List.of("id")));
        GaussVectorStore store = store(connection);

        CollectionSchema schema = store.getSchema("docs", Map.of()).join();

        assertThat(schema.getField("id").isPrimary()).isTrue();
        assertThat(schema.getField("id").getMaxLength()).isEqualTo(128);
        assertThat(schema.getField("embedding").getDtype()).isEqualTo(VectorDataType.FLOAT_VECTOR);
        assertThat(schema.getField("embedding").getDim()).isEqualTo(4);
        assertThat(schema.getField("enabled").getDtype()).isEqualTo(VectorDataType.BOOL);
    }

    private static GaussVectorStore store(FakeConnection connection) {
        return new GaussVectorStore(
                "localhost",
                5432,
                "postgres",
                "postgres",
                "",
                Map.of(),
                (host, port, database, user, password, kwargs) -> connection
        );
    }

    private static List<Object> row(Object... values) {
        List<Object> row = new ArrayList<>();
        for (Object value : values) {
            row.add(value);
        }
        return row;
    }

    /**
     * In-memory SQL connection used instead of psycopg2 for deterministic focused tests.
     *
     * <p>Mirrors Python's lazy connection/cursor boundary in
     * {@code openjiuwen/core/foundation/store/vector/gauss_vector_store.py}.</p>
     */
    private static final class FakeConnection implements GaussVectorStore.SqlConnection {
        private final FakeCursor cursor = new FakeCursor();
        private boolean autoCommit;

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
            // Nothing to close.
        }
    }

    /**
     * Scripted cursor that records SQL and returns queued rows by SQL substring.
     *
     * <p>Mirrors Python's cursor usage in
     * {@code openjiuwen/core/foundation/store/vector/gauss_vector_store.py}.</p>
     */
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
            // Nothing to close.
        }
    }

    private record ResultScript(String marker, List<String> columns, List<List<Object>> rows) {
    }
}
