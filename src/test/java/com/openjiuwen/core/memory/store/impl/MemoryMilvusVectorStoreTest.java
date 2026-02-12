/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.store.impl;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.SearchResult;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for MemoryMilvusVectorStore class
 *
 * Tests the Milvus vector store implementation:
 * - Connection management
 * - Collection creation and management
 * - CRUD operations (add, search, delete)
 * - Error handling
 */
@SuppressWarnings("unchecked")
class MemoryMilvusVectorStoreTest {

    private MemoryMilvusVectorStore milvusStore;
    private MemoryMilvusVectorStore milvusStoreWithToken;

    @BeforeEach
    void setUp() {
        milvusStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128);
        milvusStoreWithToken = new MemoryMilvusVectorStore("milvus.example.com", "443", "test-token", 1536);
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreInit")
    class TestMemoryMilvusVectorStoreInit {

        @Test
        @DisplayName("test_init_basic_params - Should initialize with basic parameters")
        void testInitBasicParams() {
            assertEquals("localhost", milvusStore.getMilvusHost());
            assertEquals("19530", milvusStore.getMilvusPort());
            assertNull(milvusStore.getToken());
            assertEquals(128, milvusStore.getEmbeddingDims());
            assertTrue(milvusStore.getCollections().isEmpty());
        }

        @Test
        @DisplayName("test_init_with_token - Should initialize with token")
        void testInitWithToken() {
            assertEquals("test-token", milvusStoreWithToken.getToken());
            assertEquals(1536, milvusStoreWithToken.getEmbeddingDims());
        }

        @Test
        @DisplayName("test_default_timeout - Should have default timeout of 3 seconds")
        void testDefaultTimeout() {
            assertEquals(3, milvusStore.getTimeout());
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreEnsureConnection")
    class TestMemoryMilvusVectorStoreEnsureConnection {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        @Test
        @DisplayName("test_ensure_connection_success - Should connect successfully")
        void testEnsureConnectionSuccess() {
            // Create a testable subclass that returns mock client
            MemoryMilvusVectorStore testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };

            CompletableFuture<Void> future = testStore.ensureConnection();

            assertDoesNotThrow(() -> future.join());
        }

        @Test
        @DisplayName("test_ensure_connection_failure_raises_error - Should raise error on connection failure")
        void testEnsureConnectionFailureRaisesError() {
            // Create a testable subclass that throws exception
            MemoryMilvusVectorStore testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    throw new RuntimeException("Connection refused");
                }
            };

            CompletableFuture<Void> future = testStore.ensureConnection();

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
            assertTrue(ex.getCause().getMessage().contains("milvus connect error"));
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreAdd")
    class TestMemoryMilvusVectorStoreAdd {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;
        private MemoryMilvusVectorStore testStore;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            testStore = createMockStore();
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        private MemoryMilvusVectorStore createMockStore() {
            return new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };
        }

        @Test
        @DisplayName("test_add_without_table_name_raises_error - Should raise error when table_name is not provided")
        void testAddWithoutTableNameRaisesError() {
            List<Map<String, Object>> data = List.of(
                    Map.of("id", "test_id", "embedding", createEmbedding(128))
            );

            CompletableFuture<Void> future = milvusStore.add(data, 128);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
            assertTrue(ex.getCause().getMessage().contains("table_name is required"));
        }

        @Test
        @DisplayName("test_add_with_null_table_name_raises_error - Should raise error for null table_name")
        void testAddWithNullTableNameRaisesError() {
            List<Map<String, Object>> data = List.of(
                    Map.of("id", "test_id", "embedding", createEmbedding(128))
            );

            CompletableFuture<Void> future = milvusStore.add(data, (String) null);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("test_add_with_empty_table_name_raises_error - Should raise error for empty table_name")
        void testAddWithEmptyTableNameRaisesError() {
            List<Map<String, Object>> data = List.of(
                    Map.of("id", "test_id", "embedding", createEmbedding(128))
            );

            CompletableFuture<Void> future = milvusStore.add(data, "");

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("test_add_single_dict_converted_to_list - Should handle single dict data")
        void testAddSingleDictConvertedToList() {
            // Setup mock for collection operations
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            R<MutationResult> insertResult = R.success(MutationResult.newBuilder().build());
            doReturn(insertResult).when(mockClient).insert(any(InsertParam.class));

            Map<String, Object> data = new HashMap<>();
            data.put("id", "test_id");
            data.put("embedding", createEmbedding(128));

            CompletableFuture<Void> future = testStore.add(List.of(data), "test_table");

            assertDoesNotThrow(() -> future.join());
            verify(mockClient).insert(any(InsertParam.class));
        }

        @Test
        @DisplayName("test_add_list_of_data - Should handle list of data")
        void testAddListOfData() {
            // Setup mock for collection operations
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            R<MutationResult> insertResult = R.success(MutationResult.newBuilder().build());
            doReturn(insertResult).when(mockClient).insert(any(InsertParam.class));

            List<Map<String, Object>> data = List.of(
                    Map.of("id", "id1", "embedding", createEmbedding(128)),
                    Map.of("id", "id2", "embedding", createEmbedding(128))
            );

            CompletableFuture<Void> future = testStore.add(data, "test_table");

            assertDoesNotThrow(() -> future.join());
            verify(mockClient).insert(any(InsertParam.class));
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreSearch")
    class TestMemoryMilvusVectorStoreSearch {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;
        private MemoryMilvusVectorStore testStore;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        @Test
        @DisplayName("test_search_without_table_name_raises_error - Should raise error when table_name is not provided")
        void testSearchWithoutTableNameRaisesError() {
            List<Double> queryVector = createEmbedding(128);

            CompletableFuture<List<SearchResult>> future = milvusStore.search(queryVector, 5, (Map<String, Object>) null);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
            assertTrue(ex.getCause().getMessage().contains("table_name is required"));
        }

        @Test
        @DisplayName("test_search_with_null_table_name_raises_error - Should raise error for null table_name")
        void testSearchWithNullTableNameRaisesError() {
            List<Double> queryVector = createEmbedding(128);

            CompletableFuture<List<SearchResult>> future = milvusStore.search(queryVector, 5, (String) null);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("test_search_returns_search_results - Should return list of SearchResult using mock hits")
        void testSearchReturnsSearchResults() {
            // Setup mock for collection operations
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            // Set mock search hits for testing
            testStore.setMockSearchHits(List.of(
                    new MemoryMilvusVectorStore.MockSearchHit("result_id", 0.95f)
            ));

            List<Double> queryVector = createEmbedding(128);
            List<SearchResult> results = testStore.search(queryVector, 5, "test_table").join();

            assertEquals(1, results.size());
            assertEquals("result_id", results.get(0).getId());
            assertEquals(0.95, results.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("test_search_empty_results - Should return empty list when no results")
        void testSearchEmptyResults() {
            // Setup mock for collection operations
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            // Set empty mock search hits
            testStore.setMockSearchHits(Collections.emptyList());

            List<Double> queryVector = createEmbedding(128);
            List<SearchResult> results = testStore.search(queryVector, 5, "test_table").join();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("test_sparse_search_returns_empty - Should return empty list for sparse search")
        void testSparseSearchReturnsEmpty() throws Exception {
            List<SearchResult> results = milvusStore.sparseSearch("query text", 5, null).join();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("test_hybrid_search_returns_empty - Should return empty list for hybrid search")
        void testHybridSearchReturnsEmpty() throws Exception {
            List<Double> queryVector = createEmbedding(128);

            List<SearchResult> results = milvusStore.hybridSearch("query", queryVector, 5, 0.5, null).join();

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreDelete")
    class TestMemoryMilvusVectorStoreDelete {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;
        private MemoryMilvusVectorStore testStore;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        @Test
        @DisplayName("test_delete_without_table_name_raises_error - Should raise error when table_name is not provided")
        void testDeleteWithoutTableNameRaisesError() {
            CompletableFuture<Boolean> future = milvusStore.delete(List.of("id1"), (String) null);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
            assertTrue(ex.getCause().getMessage().contains("table_name is required"));
        }

        @Test
        @DisplayName("test_deleteFromTable_without_table_name_raises_error - Should raise error for null table_name")
        void testDeleteFromTableWithoutTableNameRaisesError() {
            CompletableFuture<Boolean> future = milvusStore.deleteFromTable(List.of("id1"), null);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("test_deleteFromTable_with_empty_table_name_raises_error - Should raise error for empty table_name")
        void testDeleteFromTableWithEmptyTableNameRaisesError() {
            CompletableFuture<Boolean> future = milvusStore.deleteFromTable(List.of("id1"), "");

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("test_delete_collection_not_exists_returns_true - Should return true when collection doesn't exist")
        void testDeleteCollectionNotExistsReturnsTrue() {
            // Mock hasCollection returns false
            R<Boolean> hasCollectionResult = R.success(false);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            Boolean result = testStore.deleteFromTable(List.of("id1"), "non_existent").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_delete_success - Should delete records successfully")
        void testDeleteSuccess() {
            // Mock hasCollection returns true
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            R<MutationResult> deleteResult = R.success(MutationResult.newBuilder().build());
            doReturn(deleteResult).when(mockClient).delete(any(DeleteParam.class));

            Boolean result = testStore.deleteFromTable(List.of("id1", "id2"), "test_table").join();

            assertTrue(result);
            verify(mockClient).delete(any(DeleteParam.class));
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreDeleteTable")
    class TestMemoryMilvusVectorStoreDeleteTable {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;
        private MemoryMilvusVectorStore testStore;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        @Test
        @DisplayName("test_delete_table_not_exists_returns_true - Should return true when table doesn't exist")
        void testDeleteTableNotExistsReturnsTrue() {
            R<Boolean> hasCollectionResult = R.success(false);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            Boolean result = testStore.deleteTable("non_existent").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_delete_table_removes_from_cache - Should remove collection from cache after deletion")
        void testDeleteTableRemovesFromCache() {
            // Pre-populate collections cache
            testStore.getCollections().put("test_table", "test_table");

            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> dropResult = mock(R.class);
            when(dropResult.getException()).thenReturn(null);
            doReturn(dropResult).when(mockClient).dropCollection(any(DropCollectionParam.class));

            Boolean result = testStore.deleteTable("test_table").join();

            assertTrue(result);
            assertFalse(testStore.getCollections().containsKey("test_table"));
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreTableExists")
    class TestMemoryMilvusVectorStoreTableExists {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;
        private MemoryMilvusVectorStore testStore;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        @Test
        @DisplayName("test_table_exists_true - Should return true when collection exists")
        void testTableExistsTrue() {
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            Boolean result = testStore.tableExists("existing_table").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_table_exists_false - Should return false when collection doesn't exist")
        void testTableExistsFalse() {
            R<Boolean> hasCollectionResult = R.success(false);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            Boolean result = testStore.tableExists("non_existent").join();

            assertFalse(result);
        }

        @Test
        @DisplayName("test_table_exists_null_result - Should return false when result data is null")
        void testTableExistsNullResult() {
            R<Boolean> hasCollectionResult = R.success(null);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            Boolean result = testStore.tableExists("some_table").join();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreGetCollection")
    class TestMemoryMilvusVectorStoreGetCollection {

        @Mock
        private MilvusClient mockClient;

        private AutoCloseable mocks;
        private MemoryMilvusVectorStore testStore;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            testStore = new MemoryMilvusVectorStore("localhost", "19530", null, 128) {
                @Override
                protected MilvusClient createMilvusClient() {
                    return mockClient;
                }
            };
        }

        @AfterEach
        void tearDown() throws Exception {
            mocks.close();
        }

        @Test
        @DisplayName("test_get_collection_from_cache - Should return cached collection")
        void testGetCollectionFromCache() {
            // Pre-populate cache
            testStore.getCollections().put("cached_table", "cached_table");

            // Setup mock - collection exists
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            R<MutationResult> insertResult = R.success(MutationResult.newBuilder().build());
            doReturn(insertResult).when(mockClient).insert(any(InsertParam.class));

            // Use add to trigger getOrCreateCollection
            List<Map<String, Object>> data = List.of(
                    Map.of("id", "test_id", "embedding", createEmbedding(128))
            );

            assertDoesNotThrow(() -> testStore.add(data, "cached_table").join());
            assertTrue(testStore.getCollections().containsKey("cached_table"));
        }

        @Test
        @DisplayName("test_get_collection_loads_existing - Should load existing collection")
        void testGetCollectionLoadsExisting() {
            // Setup mock - collection exists
            R<Boolean> hasCollectionResult = R.success(true);
            doReturn(hasCollectionResult).when(mockClient).hasCollection(any(HasCollectionParam.class));

            R<RpcStatus> loadResult = mock(R.class);
            when(loadResult.getException()).thenReturn(null);
            doReturn(loadResult).when(mockClient).loadCollection(any(LoadCollectionParam.class));

            R<MutationResult> insertResult = R.success(MutationResult.newBuilder().build());
            doReturn(insertResult).when(mockClient).insert(any(InsertParam.class));

            List<Map<String, Object>> data = List.of(
                    Map.of("id", "test_id", "embedding", createEmbedding(128))
            );

            assertDoesNotThrow(() -> testStore.add(data, "existing_table").join());
            verify(mockClient).loadCollection(any(LoadCollectionParam.class));
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreCollections")
    class TestMemoryMilvusVectorStoreCollections {

        @Test
        @DisplayName("test_collections_map_is_empty_initially - Should have empty collections map on init")
        void testCollectionsMapIsEmptyInitially() {
            assertTrue(milvusStore.getCollections().isEmpty());
        }

        @Test
        @DisplayName("test_collections_map_is_thread_safe - Should use ConcurrentHashMap for thread safety")
        void testCollectionsMapIsThreadSafe() {
            assertNotNull(milvusStore.getCollections());
            assertTrue(milvusStore.getCollections() instanceof java.util.concurrent.ConcurrentHashMap);
        }
    }

    @Nested
    @DisplayName("TestMemoryMilvusVectorStoreCheckVectorField")
    class TestMemoryMilvusVectorStoreCheckVectorField {

        @Test
        @DisplayName("test_check_vector_field_logs_error - Should log error (placeholder implementation)")
        void testCheckVectorFieldLogsError() {
            // This should not throw, just log an error
            assertDoesNotThrow(() -> milvusStore.checkVectorField());
        }
    }

    @Nested
    @DisplayName("TestMilvusUtils")
    class TestMilvusUtils {

        @Test
        @DisplayName("test_convert_empty_results - Should return empty list for empty results")
        void testConvertEmptyResults() {
            List<SearchResultsWrapper.IDScore> results = Collections.emptyList();

            List<SearchResult> converted = MilvusUtils.convertMilvusResult(results);

            assertTrue(converted.isEmpty());
        }

        @Test
        @DisplayName("test_memory_id_length_constant - Should have correct memory ID length constant")
        void testMemoryIdLengthConstant() {
            assertEquals(36, MilvusUtils.MEMORY_ID_LENGTH);
        }

        @Test
        @DisplayName("test_store_type_constant - Should have correct store type constant")
        void testStoreTypeConstant() {
            assertEquals("milvus vector store", MilvusUtils.STORE_TYPE);
        }

        // Note: Tests for convertMilvusResult with non-empty data require
        // real Milvus SearchResultsWrapper.IDScore objects, which can only be
        // created through actual Milvus search operations.
        // These tests are covered by integration tests.
    }

    // Helper methods
    private List<Double> createEmbedding(int dims) {
        List<Double> embedding = new ArrayList<>(dims);
        for (int i = 0; i < dims; i++) {
            embedding.add(0.1);
        }
        return embedding;
    }
}
