/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/unit_tests/core/foundation/store/test_milvus_vector_store.py}.
 */
class MilvusVectorStorePythonParityTest {

    @Test
    void initWithDefaultDatabaseKeepsUriTokenAndDefaultDatabase() {
        MilvusVectorStore store = new MilvusVectorStore("http://vector-host:19530");

        assertThat(store.getMilvusUri()).isEqualTo("http://vector-host:19530");
        assertThat(store.getMilvusToken()).isNull();
        assertThat(store.getDatabaseName()).isEqualTo("default");
    }

    @Test
    void initWithTokenKeepsAuthenticationToken() {
        MilvusVectorStore store = new MilvusVectorStore("http://vector-host:19530", "test_token", "default");

        assertThat(store.getMilvusToken()).isEqualTo("test_token");
    }

    @Test
    void initWithCustomDatabaseKeepsDatabaseName() {
        MilvusVectorStore store = new MilvusVectorStore("http://vector-host:19530", null, "custom_db");

        assertThat(store.getDatabaseName()).isEqualTo("custom_db");
    }

    @Test
    void initWithNewDatabaseKeepsRequestedName() {
        MilvusVectorStore store = new MilvusVectorStore(mapOf(
                "milvus_uri", "http://vector-host:19530",
                "database_name", "new_db"
        ));

        assertThat(store.getDatabaseName()).isEqualTo("new_db");
        assertThat(store.getMilvusUri()).isEqualTo("http://vector-host:19530");
    }

    @Test
    void lazyInitDoesNotCreateClientDuringConstructor() throws Exception {
        MilvusVectorStore store = new MilvusVectorStore("http://vector-host:19530");

        assertThat(rawClient(store)).isNull();
    }

    @Test
    void clientReuseReturnsInjectedAdapter() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        assertThat(store.client().join()).isSameAs(adapter);
        assertThat(store.client().join()).isSameAs(adapter);
    }

    @Test
    void closeReleasesInjectedClientReference() throws Exception {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        assertThat(rawClient(store)).isSameAs(adapter);
        store.close();

        assertThat(rawClient(store)).isNull();
        assertThat(adapter.closeCalls).isZero();
    }

    @Test
    void createCollectionWithSchemaObject() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.createCollection("milvus_vs_test_collection", schema(), Map.of()).join();

        assertThat(adapter.hasCollectionCalls).containsExactly("milvus_vs_test_collection");
        assertThat(adapter.createCollectionCalls).isEqualTo(1);
        assertThat(adapter.descriptions).containsKey("milvus_vs_test_collection");
    }

    @Test
    void createCollectionWithDictSchema() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.createCollection("milvus_vs_test_collection", schemaDict(), Map.of()).join();

        assertThat(adapter.createCollectionCalls).isEqualTo(1);
        assertThat(adapter.createdSchema.getFields()).extracting(FieldSchema::getName)
                .containsExactly("id", "embedding", "text");
    }

    @Test
    void createCollectionWithCustomMetric() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.createCollection("milvus_vs_test_collection", schema(), Map.of("distance_metric", "L2")).join();

        assertThat(adapter.distanceMetricByCollection).containsEntry("milvus_vs_test_collection", "L2");
    }

    @Test
    void createCollectionWithCustomIndexType() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.createCollection("milvus_vs_test_collection", schema(), Map.of("index_type", "HNSW")).join();

        assertThat(adapter.indexTypeByCollection).containsEntry("milvus_vs_test_collection", "HNSW");
    }

    @Test
    void createCollectionAlreadyExistsDoesNotCreateAgain() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schema());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.createCollection("milvus_vs_test_collection", schema(), Map.of()).join();

        assertThat(adapter.createCollectionCalls).isZero();
    }

    @Test
    void createCollectionMissingVectorDimRaisesBaseError() {
        MilvusVectorStore store = new MilvusVectorStore(new RecordingMilvusClientAdapter());

        BaseError error = assertBaseError(() -> store.createCollection(
                "milvus_vs_test_collection",
                schemaDictWithoutVectorDim(),
                Map.of()
        ).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    @Test
    void createCollectionMissingVectorFieldRaisesBaseError() {
        MilvusVectorStore store = new MilvusVectorStore(new RecordingMilvusClientAdapter());

        BaseError error = assertBaseError(() -> store.createCollection(
                "milvus_vs_test_collection",
                schemaWithoutVector(),
                Map.of()
        ).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    @Test
    void deleteCollectionSuccessDropsExistingCollection() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schema());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.deleteCollection("milvus_vs_test_collection", Map.of()).join();

        assertThat(adapter.dropCollectionCalls).containsExactly("milvus_vs_test_collection");
        assertThat(adapter.descriptions).doesNotContainKey("milvus_vs_test_collection");
    }

    @Test
    void deleteCollectionNotExistsReturnsWithoutDrop() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.deleteCollection("milvus_vs_test_collection", Map.of()).join();

        assertThat(adapter.dropCollectionCalls).isEmpty();
    }

    @Test
    void deleteCollectionOtherErrorPropagates() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schema());
        adapter.dropFailure = new IllegalStateException("some other error");
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        CompletionException exception = assertThrows(CompletionException.class,
                () -> store.deleteCollection("milvus_vs_test_collection", Map.of()).join());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class)
                .hasMessage("some other error");
    }

    @Test
    void collectionExistsTrue() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schema());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        assertThat(store.collectionExists("milvus_vs_test_collection", Map.of()).join()).isTrue();
    }

    @Test
    void collectionExistsFalse() {
        MilvusVectorStore store = new MilvusVectorStore(new RecordingMilvusClientAdapter());

        assertThat(store.collectionExists("milvus_vs_test_collection", Map.of()).join()).isFalse();
    }

    @Test
    void getSchemaSuccess() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        CollectionSchema result = store.getSchema("milvus_vs_test_collection", Map.of()).join();

        assertThat(result.getFields()).hasSize(3);
        assertThat(result.getFields().get(0).getName()).isEqualTo("id");
        assertThat(result.getFields().get(0).getDtype()).isEqualTo(VectorDataType.VARCHAR);
        assertThat(result.getFields().get(0).isPrimary()).isTrue();
        assertThat(result.getFields().get(1).getName()).isEqualTo("embedding");
        assertThat(result.getFields().get(1).getDtype()).isEqualTo(VectorDataType.FLOAT_VECTOR);
        assertThat(result.getFields().get(1).getDim()).isEqualTo(768);
    }

    @Test
    void getSchemaCollectionNotExistsRaisesBaseError() {
        MilvusVectorStore store = new MilvusVectorStore(new RecordingMilvusClientAdapter());

        BaseError error = assertBaseError(() -> store.getSchema("non_existent_collection", Map.of()).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND);
    }

    @Test
    void getSchemaWithStringTypesMapsToVectorDataType() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithTwoFields());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        CollectionSchema result = store.getSchema("milvus_vs_test_collection", Map.of()).join();

        assertThat(result.getFields()).hasSize(2);
        assertThat(result.getFields().get(0).getDtype()).isEqualTo(VectorDataType.VARCHAR);
        assertThat(result.getFields().get(1).getDtype()).isEqualTo(VectorDataType.FLOAT_VECTOR);
    }

    @Test
    void addDocsSuccessInsertsAndFlushes() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.addDocs("milvus_vs_test_collection", List.of(
                mapOf("id", "doc1", "embedding", List.of(0.1d, 0.2d, 0.3d), "text", "Test document 1"),
                mapOf("id", "doc2", "embedding", List.of(0.4d, 0.5d, 0.6d), "text", "Test document 2")
        ), Map.of()).join();

        assertThat(adapter.insertBatches).hasSize(1);
        assertThat(adapter.insertBatches.getFirst()).hasSize(2);
        assertThat(adapter.flushCollectionCalls).containsExactly("milvus_vs_test_collection");
    }

    @Test
    void addDocsWithBatchSizeSplitsInserts() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        MilvusVectorStore store = new MilvusVectorStore(adapter);
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            docs.add(mapOf("id", "doc" + index, "embedding", List.of(0.1d, 0.2d, 0.3d),
                    "text", "Test document " + index));
        }

        store.addDocs("milvus_vs_test_collection", docs, Map.of("batch_size", 3)).join();

        assertThat(adapter.insertBatches).hasSize(4);
        assertThat(adapter.flushCollectionCalls).containsExactly("milvus_vs_test_collection");
    }

    @Test
    void addDocsZeroBatchSizeUsesDefault() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.addDocs("milvus_vs_test_collection", List.of(
                mapOf("id", "doc1", "embedding", List.of(0.1d, 0.2d, 0.3d), "text", "Test document")
        ), Map.of("batch_size", 0)).join();

        assertThat(adapter.insertBatches).hasSize(1);
        assertThat(adapter.flushCollectionCalls).containsExactly("milvus_vs_test_collection");
    }

    @Test
    void searchSuccessConvertsHitsToVectorSearchResults() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        adapter.searchHits = List.of(
                new MilvusVectorStore.SearchHit("doc1", null, 0.1d, 0.95d, mapOf("text", "Text 1", "source", "test1")),
                new MilvusVectorStore.SearchHit("doc2", null, 0.3d, 0.85d, mapOf("text", "Text 2", "source", "test2"))
        );
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getFields()).containsEntry("id", "doc1").containsEntry("text", "Text 1");
        assertThat(results.get(0).getScore()).isEqualTo(0.95d);
    }

    @Test
    void searchWithFiltersBuildsFilterExpression() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithCategory());
        adapter.searchHits = List.of(new MilvusVectorStore.SearchHit("doc1", null, 0.1d, null,
                mapOf("category", "tech")));
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, Map.of("category", "tech"), Map.of()).join();

        assertThat(results).hasSize(1);
        assertThat(adapter.lastSearchFilter).isEqualTo("category == \"tech\"");
    }

    @Test
    void searchWithPkFieldUsesPrimaryKeyAsId() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        adapter.searchHits = List.of(new MilvusVectorStore.SearchHit(null, "123", 0.1d, null,
                mapOf("text", "Text 1")));
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertThat(results.getFirst().getFields()).containsEntry("id", "123");
    }

    @Test
    void searchWithJsonMetadataParsesJsonStrings() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        adapter.searchHits = List.of(new MilvusVectorStore.SearchHit("doc1", null, 0.1d, null,
                mapOf("tags", "[\"tag1\",\"tag2\"]")));
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertThat(results.getFirst().getFields().get("tags")).isEqualTo(List.of("tag1", "tag2"));
    }

    @Test
    void searchWithOutputFieldsForwardsCustomFields() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithSource());
        adapter.searchHits = List.of(new MilvusVectorStore.SearchHit("doc1", null, 0.1d, null,
                mapOf("text", "Text 1")));
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null,
                Map.of("output_fields", List.of("text", "source"))).join();

        assertThat(results).hasSize(1);
        assertThat(adapter.lastOutputFields).containsExactly("text", "source");
    }

    @Test
    void searchIpDistanceConversionMatchesPythonScoreFormula() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        adapter.searchHits = List.of(
                new MilvusVectorStore.SearchHit("doc1", null, 1.0d, null, Map.of()),
                new MilvusVectorStore.SearchHit("doc2", null, 0.0d, null, Map.of()),
                new MilvusVectorStore.SearchHit("doc3", null, -1.0d, null, Map.of())
        );
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of("metric_type", "IP")).join();

        assertThat(results).extracting(VectorSearchResult::getScore).containsExactly(1.0d, 0.5d, 0.0d);
    }

    @Test
    void searchCosineDistanceConversionMatchesPythonScoreFormula() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        adapter.searchHits = List.of(
                new MilvusVectorStore.SearchHit("doc1", null, 1.0d, null, Map.of()),
                new MilvusVectorStore.SearchHit("doc2", null, 0.0d, null, Map.of()),
                new MilvusVectorStore.SearchHit("doc3", null, -1.0d, null, Map.of())
        );
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        List<VectorSearchResult> results = store.search("milvus_vs_test_collection",
                List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertThat(results).extracting(VectorSearchResult::getScore).containsExactly(1.0d, 0.5d, 0.0d);
    }

    @Test
    void deleteDocsByIdsSuccess() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.deleteDocsByIds("milvus_vs_test_collection", List.of("doc1", "doc2"), Map.of()).join();

        assertThat(adapter.lastDeleteIds).containsExactly("doc1", "doc2");
        assertThat(adapter.flushCollectionCalls).containsExactly("milvus_vs_test_collection");
    }

    @Test
    void deleteDocsByIdsEmptyListReturnsEarly() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.deleteDocsByIds("milvus_vs_test_collection", List.of(), Map.of()).join();

        assertThat(adapter.deleteByIdsCalls).isZero();
        assertThat(adapter.flushCollectionCalls).isEmpty();
    }

    @Test
    void deleteDocsByFiltersSuccess() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        adapter.putDescription("milvus_vs_test_collection", schemaWithText());
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.deleteDocsByFilters("milvus_vs_test_collection", Map.of("source", "test"), Map.of()).join();

        assertThat(adapter.lastDeleteFilter).isEqualTo("source == \"test\"");
        assertThat(adapter.flushCollectionCalls).containsExactly("milvus_vs_test_collection");
    }

    @Test
    void deleteDocsByFiltersEmptyReturnsEarly() {
        RecordingMilvusClientAdapter adapter = new RecordingMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.deleteDocsByFilters("milvus_vs_test_collection", Map.of(), Map.of()).join();

        assertThat(adapter.deleteByFilterCalls).isZero();
        assertThat(adapter.flushCollectionCalls).isEmpty();
    }

    private static CollectionSchema schema() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 768,
                        null, null, null, null)
        ), "Test collection", false);
    }

    private static CollectionSchema schemaWithText() {
        CollectionSchema schema = schema();
        schema.addField(new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null,
                null, null, null, null));
        return schema;
    }

    private static CollectionSchema schemaWithTwoFields() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 1536,
                        null, null, null, null)
        ), "Test collection", true);
    }

    private static CollectionSchema schemaWithCategory() {
        CollectionSchema schema = schema();
        schema.addField(new FieldSchema("category", VectorDataType.VARCHAR, false, false, 65535, null,
                null, null, null, null));
        return schema;
    }

    private static CollectionSchema schemaWithSource() {
        CollectionSchema schema = schemaWithText();
        schema.addField(new FieldSchema("source", VectorDataType.VARCHAR, false, false, 65535, null,
                null, null, null, null));
        return schema;
    }

    private static CollectionSchema schemaWithoutVector() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null)
        ), "Test collection", false);
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

    private static BaseError assertBaseError(Runnable action) {
        CompletionException exception = assertThrows(CompletionException.class, action::run);
        assertThat(exception.getCause()).isInstanceOf(BaseError.class);
        return (BaseError) exception.getCause();
    }

    private static Object rawClient(MilvusVectorStore store) throws Exception {
        Field field = MilvusVectorStore.class.getDeclaredField("client");
        field.setAccessible(true);
        return field.get(store);
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    /**
     * Mirrors Python's mocked {@code AsyncMilvusClient} in
     * {@code tests/unit_tests/core/foundation/store/test_milvus_vector_store.py}.
     */
    private static final class RecordingMilvusClientAdapter implements MilvusVectorStore.MilvusClientAdapter {
        private final Map<String, MilvusVectorStore.CollectionDescription> descriptions = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> rowsByCollection = new LinkedHashMap<>();
        private final Map<String, String> distanceMetricByCollection = new LinkedHashMap<>();
        private final Map<String, String> indexTypeByCollection = new LinkedHashMap<>();
        private final List<String> hasCollectionCalls = new ArrayList<>();
        private final List<String> dropCollectionCalls = new ArrayList<>();
        private final List<String> loadCollectionCalls = new ArrayList<>();
        private final List<String> flushCollectionCalls = new ArrayList<>();
        private final List<List<Map<String, Object>>> insertBatches = new ArrayList<>();
        private CollectionSchema createdSchema;
        private List<MilvusVectorStore.SearchHit> searchHits = List.of();
        private RuntimeException dropFailure;
        private int createCollectionCalls;
        private int deleteByIdsCalls;
        private int deleteByFilterCalls;
        private int closeCalls;
        private String lastSearchFilter;
        private List<String> lastOutputFields = List.of();
        private List<String> lastDeleteIds = List.of();
        private String lastDeleteFilter;

        private void putDescription(String collectionName, CollectionSchema schema) {
            List<MilvusVectorStore.FieldDescription> fields = schema.getFields().stream()
                    .map(RecordingMilvusClientAdapter::fieldDescription)
                    .toList();
            descriptions.put(collectionName, new MilvusVectorStore.CollectionDescription(
                    schema.getDescription() == null ? "" : schema.getDescription(),
                    schema.isEnableDynamicField(),
                    fields,
                    Map.of()
            ));
            rowsByCollection.putIfAbsent(collectionName, new ArrayList<>());
        }

        @Override
        public boolean hasCollection(String collectionName) {
            hasCollectionCalls.add(collectionName);
            return descriptions.containsKey(collectionName);
        }

        @Override
        public void createCollection(String collectionName, CollectionSchema schema, String distanceMetric,
                String indexType) {
            createCollectionCalls++;
            createdSchema = schema;
            putDescription(collectionName, schema);
            distanceMetricByCollection.put(collectionName, distanceMetric);
            indexTypeByCollection.put(collectionName, indexType);
        }

        @Override
        public void dropCollection(String collectionName) {
            if (dropFailure != null) {
                throw dropFailure;
            }
            dropCollectionCalls.add(collectionName);
            descriptions.remove(collectionName);
            rowsByCollection.remove(collectionName);
        }

        @Override
        public MilvusVectorStore.CollectionDescription describeCollection(String collectionName) {
            MilvusVectorStore.CollectionDescription description = descriptions.get(collectionName);
            if (description == null) {
                throw new IllegalArgumentException("Collection does not exist: " + collectionName);
            }
            return description;
        }

        @Override
        public void insert(String collectionName, List<Map<String, Object>> rows) {
            List<Map<String, Object>> copiedRows = rows.stream()
                    .map(LinkedHashMap::new)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
            insertBatches.add(copiedRows);
            rowsByCollection.computeIfAbsent(collectionName, ignored -> new ArrayList<>()).addAll(copiedRows);
        }

        @Override
        public void flush(String collectionName) {
            flushCollectionCalls.add(collectionName);
        }

        @Override
        public List<MilvusVectorStore.SearchHit> search(String collectionName, List<Double> queryVector,
                String vectorField, int limit, List<String> outputFields, Map<String, Object> searchParams,
                String filter) {
            lastSearchFilter = filter;
            lastOutputFields = new ArrayList<>(outputFields);
            return searchHits.stream().limit(limit).toList();
        }

        @Override
        public Map<String, Object> deleteByIds(String collectionName, List<String> ids) {
            deleteByIdsCalls++;
            lastDeleteIds = new ArrayList<>(ids);
            return Map.of("delete_count", ids.size());
        }

        @Override
        public Map<String, Object> deleteByFilter(String collectionName, String filter) {
            deleteByFilterCalls++;
            lastDeleteFilter = filter;
            return Map.of("delete_count", 1);
        }

        @Override
        public void loadCollection(String collectionName) {
            loadCollectionCalls.add(collectionName);
        }

        @Override
        public String describeIndexMetric(String collectionName, String vectorField) {
            return distanceMetricByCollection.getOrDefault(collectionName, "COSINE");
        }

        @Override
        public List<Map<String, Object>> queryAll(String collectionName) {
            return rowsByCollection.getOrDefault(collectionName, List.of()).stream()
                    .map(LinkedHashMap::new)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
        }

        @Override
        public void releaseCollection(String collectionName) {
        }

        @Override
        public void renameCollection(String oldName, String newName) {
            descriptions.put(newName, descriptions.remove(oldName));
            rowsByCollection.put(newName, rowsByCollection.remove(oldName));
        }

        @Override
        public void alterCollectionProperties(String collectionName, Map<String, String> properties) {
            MilvusVectorStore.CollectionDescription current = describeCollection(collectionName);
            descriptions.put(collectionName, new MilvusVectorStore.CollectionDescription(
                    current.description(),
                    current.enableDynamicField(),
                    current.fields(),
                    new LinkedHashMap<>(properties)
            ));
        }

        @Override
        public List<String> listCollections() {
            return new ArrayList<>(descriptions.keySet());
        }

        @Override
        public void close() {
            closeCalls++;
        }

        private static MilvusVectorStore.FieldDescription fieldDescription(FieldSchema field) {
            return new MilvusVectorStore.FieldDescription(
                    field.getName(),
                    field.getDtype(),
                    field.isPrimary(),
                    field.isAutoId(),
                    field.getMaxLength(),
                    field.getDim(),
                    field.getDescription()
            );
        }
    }
}
