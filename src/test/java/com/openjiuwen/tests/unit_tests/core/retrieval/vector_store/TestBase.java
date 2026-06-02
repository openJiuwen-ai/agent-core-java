/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Vector store abstract base class test cases.
 *
 * <p>Mirrors Python's {@code ConcreteVectorStore} and {@code TestVectorStore} in
 * {@code tests.unit_tests.core.retrieval.vector_store.test_base}.</p>
 */
@DisplayName("VectorStore Base Tests")
class TestBase {

    @Nested
    @DisplayName("Add")
    class AddTests {

        @Test
        @DisplayName("test_add - concrete vector store accepts vector rows")
        void testAdd() {
            ConcreteVectorStore store = new ConcreteVectorStore();
            Map<String, Object> row = Map.of(
                    "id", "1",
                    "text", "test",
                    "embedding", List.of(0.1f));

            assertThatCode(() -> store.add(List.of(row), null, Map.of()))
                    .doesNotThrowAnyException();

            assertThat(store.addedRows).containsExactly(row);
        }
    }

    @Nested
    @DisplayName("Search")
    class SearchTests {

        @Test
        @DisplayName("test_search - returns empty vector search results")
        void testSearch() {
            ConcreteVectorStore store = new ConcreteVectorStore();

            List<SearchResult> results = store.search(vector(384), 5, null, Map.of());

            assertThat(results).isEmpty();
            assertThat(store.lastQueryVector).hasSize(384);
            assertThat(store.lastTopK).isEqualTo(5);
        }

        @Test
        @DisplayName("test_sparse_search - returns empty sparse results")
        void testSparseSearch() {
            ConcreteVectorStore store = new ConcreteVectorStore();

            List<SearchResult> results = store.sparseSearch("test query", 5, null, Map.of());

            assertThat(results).isEmpty();
            assertThat(store.lastQueryText).isEqualTo("test query");
            assertThat(store.lastTopK).isEqualTo(5);
        }

        @Test
        @DisplayName("test_hybrid_search - returns empty hybrid results")
        void testHybridSearch() {
            ConcreteVectorStore store = new ConcreteVectorStore();

            List<SearchResult> results = store.hybridSearch("test query", vector(384), 5, 0.5, null, Map.of());

            assertThat(results).isEmpty();
            assertThat(store.lastQueryText).isEqualTo("test query");
            assertThat(store.lastQueryVector).hasSize(384);
            assertThat(store.lastAlpha).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("test_delete - concrete vector store deletes ids")
        void testDelete() {
            ConcreteVectorStore store = new ConcreteVectorStore();

            boolean result = store.delete(List.of("1", "2"), null, Map.of());

            assertThat(result).isTrue();
            assertThat(store.deletedIds).containsExactly("1", "2");
        }
    }

    @Nested
    @DisplayName("Table Operations")
    class TableTests {

        @Test
        @DisplayName("test_table_exists - reports known table")
        void testTableExists() {
            ConcreteVectorStore store = new ConcreteVectorStore();

            assertThat(store.tableExists("test_collection")).isTrue();
            assertThat(store.tableExists("missing_collection")).isFalse();
        }

        @Test
        @DisplayName("test_delete_table - records deleted table")
        void testDeleteTable() {
            ConcreteVectorStore store = new ConcreteVectorStore();

            store.deleteTable("test_collection");

            assertThat(store.deletedTable).isEqualTo("test_collection");
        }
    }

    private static List<Float> vector(int dimension) {
        List<Float> values = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            values.add(0.1f);
        }
        return values;
    }

    private static final class ConcreteVectorStore implements VectorStore {
        private String collectionName = "test_collection";
        private List<Map<String, Object>> addedRows = List.of();
        private List<Float> lastQueryVector = List.of();
        private String lastQueryText;
        private int lastTopK;
        private double lastAlpha;
        private List<String> deletedIds = List.of();
        private String deletedTable;

        @Override
        public String getCollectionName() {
            return collectionName;
        }

        @Override
        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            ConcreteVectorStore scoped = new ConcreteVectorStore();
            scoped.setCollectionName(collectionName);
            return scoped;
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
            addedRows = data == null ? List.of() : new ArrayList<>(data);
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector,
                                         int topK,
                                         Map<String, Object> filters,
                                         Map<String, Object> options) {
            lastQueryVector = queryVector == null ? List.of() : new ArrayList<>(queryVector);
            lastTopK = topK;
            return List.of();
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText,
                                               int topK,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            lastQueryText = queryText;
            lastTopK = topK;
            return List.of();
        }

        @Override
        public List<SearchResult> hybridSearch(String queryText,
                                               List<Float> queryVector,
                                               int topK,
                                               double alpha,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            lastQueryText = queryText;
            lastQueryVector = queryVector == null ? List.of() : new ArrayList<>(queryVector);
            lastTopK = topK;
            lastAlpha = alpha;
            return List.of();
        }

        @Override
        public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
            deletedIds = ids == null ? List.of() : new ArrayList<>(ids);
            return true;
        }

        @Override
        public boolean tableExists(String tableName) {
            return collectionName.equals(tableName);
        }

        @Override
        public void deleteTable(String tableName) {
            deletedTable = tableName;
        }

        @Override
        public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
            Map<String, Object> metadata = filters == null ? Map.of() : new LinkedHashMap<>(filters);
            return List.of(new SearchResult("filtered", "filtered text", 1.0, metadata));
        }

        @Override
        public long count(String tableName) {
            return tableExists(tableName) ? addedRows.size() : 0L;
        }

        @Override
        public String getDatabaseName() {
            return "";
        }

        @Override
        public String getDistanceMetric() {
            return "cosine";
        }

        @Override
        public String getIndexType() {
            return "hybrid";
        }

        @Override
        public String getTextField() {
            return "text";
        }

        @Override
        public String getVectorField() {
            return "embedding";
        }

        @Override
        public String getSparseVectorField() {
            return "sparse_vector";
        }

        @Override
        public String getMetadataField() {
            return "metadata";
        }

        @Override
        public String getDocIdField() {
            return "doc_id";
        }
    }
}
