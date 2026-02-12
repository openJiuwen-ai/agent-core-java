/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.store.impl;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.SearchResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MemoryChromaVectorStore class
 *
 * Tests the ChromaDB vector store implementation using HTTP REST API:
 * - Collection management
 * - CRUD operations (add, search, delete)
 * - Table/collection lifecycle
 * - Error handling
 */
class MemoryChromaVectorStoreTest {

    private MemoryChromaVectorStore chromaStore;

    @BeforeEach
    void setUp() {
        chromaStore = new MemoryChromaVectorStore("http://localhost:8000");
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreInit")
    class TestMemoryChromaVectorStoreInit {

        @Test
        @DisplayName("test_init_success - Should initialize successfully")
        void testInitSuccess() {
            MemoryChromaVectorStore store = new MemoryChromaVectorStore("http://localhost:8000");

            assertNotNull(store);
            assertEquals("http://localhost:8000", store.getBaseUrl());
            assertTrue(store.getCollectionCache().isEmpty());
        }

        @Test
        @DisplayName("test_init_trailing_slash_removed - Should remove trailing slash from URL")
        void testInitTrailingSlashRemoved() {
            MemoryChromaVectorStore store = new MemoryChromaVectorStore("http://localhost:8000/");

            assertEquals("http://localhost:8000", store.getBaseUrl());
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreGetCollection")
    class TestMemoryChromaVectorStoreGetCollection {

        private MockWebServer mockServer;
        private MemoryChromaVectorStore testStore;

        @BeforeEach
        void setUp() throws IOException {
            mockServer = new MockWebServer();
            mockServer.start();
            testStore = new MemoryChromaVectorStore(mockServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
            mockServer.shutdown();
        }

        @Test
        @DisplayName("test_get_collection - Should get or create collection")
        void testGetCollection() throws Exception {
            // Mock collection creation response
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"test-uuid-123\", \"name\": \"test_table\"}"));

            CompletableFuture<String> future = testStore.getOrCreateCollection("test_table");
            String collectionId = future.join();

            assertNotNull(collectionId);
            assertEquals("test-uuid-123", collectionId);
            assertTrue(testStore.getCollectionCache().containsKey("test_table"));
            assertEquals("test-uuid-123", testStore.getCollectionCache().get("test_table"));
        }

        @Test
        @DisplayName("test_get_collection_from_cache - Should return cached collection")
        void testGetCollectionFromCache() {
            // Pre-populate cache
            testStore.getCollectionCache().put("test_table", "cached-uuid");

            CompletableFuture<String> future = testStore.getOrCreateCollection("test_table");
            String collectionId = future.join();

            assertEquals("cached-uuid", collectionId);
        }

        @Test
        @DisplayName("test_remove_collection_from_cache - Should remove collection from cache")
        void testRemoveCollectionFromCache() {
            chromaStore.getCollectionCache().put("test_table", "collection-uuid");

            chromaStore.removeCollectionFromCache("test_table");

            assertFalse(chromaStore.getCollectionCache().containsKey("test_table"));
        }

        @Test
        @DisplayName("test_collection_cache_is_thread_safe - Should use ConcurrentHashMap for thread safety")
        void testCollectionCacheIsThreadSafe() {
            assertNotNull(chromaStore.getCollectionCache());
            assertTrue(chromaStore.getCollectionCache() instanceof java.util.concurrent.ConcurrentHashMap);
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreTableName")
    class TestMemoryChromaVectorStoreTableName {

        @Test
        @DisplayName("test_check_table_name_null - Should throw error for null table name")
        void testCheckTableNameNull() {
            assertThrows(BaseError.class, () ->
                    chromaStore.checkTableName(null, "test_operation"));
        }

        @Test
        @DisplayName("test_check_table_name_empty - Should throw error for empty table name")
        void testCheckTableNameEmpty() {
            assertThrows(BaseError.class, () ->
                    chromaStore.checkTableName("", "test_operation"));
        }

        @Test
        @DisplayName("test_check_table_name_whitespace - Should throw error for whitespace-only table name")
        void testCheckTableNameWhitespace() {
            assertThrows(BaseError.class, () ->
                    chromaStore.checkTableName("   ", "test_operation"));
        }

        @Test
        @DisplayName("test_check_table_name_valid - Should not throw for valid table name")
        void testCheckTableNameValid() {
            assertDoesNotThrow(() ->
                    chromaStore.checkTableName("valid_table", "test_operation"));
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreTableExists")
    class TestMemoryChromaVectorStoreTableExists {

        private MockWebServer mockServer;
        private MemoryChromaVectorStore testStore;

        @BeforeEach
        void setUp() throws IOException {
            mockServer = new MockWebServer();
            mockServer.start();
            testStore = new MemoryChromaVectorStore(mockServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
            mockServer.shutdown();
        }

        @Test
        @DisplayName("test_table_exists_false - Should return false when collection does not exist")
        void testTableExistsFalse() {
            mockServer.enqueue(new MockResponse().setResponseCode(404));

            Boolean exists = testStore.tableExists("non_existent_table").join();

            assertFalse(exists);
        }

        @Test
        @DisplayName("test_table_exists_true - Should return true when collection exists")
        void testTableExistsTrue() {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"test-uuid\", \"name\": \"test_table\"}"));

            Boolean exists = testStore.tableExists("test_table").join();

            assertTrue(exists);
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreAdd")
    class TestMemoryChromaVectorStoreAdd {

        private MockWebServer mockServer;
        private MemoryChromaVectorStore testStore;

        @BeforeEach
        void setUp() throws IOException {
            mockServer = new MockWebServer();
            mockServer.start();
            testStore = new MemoryChromaVectorStore(mockServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
            mockServer.shutdown();
        }

        @Test
        @DisplayName("test_add_validates_table_name - Should validate table name before adding")
        void testAddValidatesTableName() {
            List<Map<String, Object>> data = List.of(
                    Map.of("id", "vec1", "embedding", List.of(0.1, 0.2, 0.3, 0.4))
            );

            // add without tableName should throw
            assertThrows(BaseError.class, () ->
                    chromaStore.add(data, (String) null));
        }

        @Test
        @DisplayName("test_add_default_method_throws - Should throw error for default add without table_name")
        void testAddDefaultMethodThrows() {
            List<Map<String, Object>> data = List.of(
                    Map.of("id", "vec1", "embedding", List.of(0.1, 0.2, 0.3, 0.4))
            );

            // Default add method requires table_name, but batchSize overload doesn't support it
            assertThrows(BaseError.class, () -> chromaStore.add(data, 100));
        }

        @Test
        @DisplayName("test_add_single_vector - Should add single vector to collection")
        void testAddSingleVector() {
            // Mock collection creation
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock add response
            mockServer.enqueue(new MockResponse().setResponseCode(200));

            Map<String, Object> vectorData = new HashMap<>();
            vectorData.put("id", "vec1");
            vectorData.put("embedding", List.of(0.1, 0.2, 0.3, 0.4));

            List<Map<String, Object>> data = List.of(vectorData);

            CompletableFuture<Void> result = testStore.add(data, "test_table");

            assertDoesNotThrow(() -> result.join());
        }

        @Test
        @DisplayName("test_add_multiple_vectors - Should add multiple vectors to collection")
        void testAddMultipleVectors() {
            // Mock collection creation
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock add response
            mockServer.enqueue(new MockResponse().setResponseCode(200));

            List<Map<String, Object>> data = List.of(
                    Map.of("id", "vec1", "embedding", List.of(0.1, 0.2, 0.3, 0.4)),
                    Map.of("id", "vec2", "embedding", List.of(0.5, 0.6, 0.7, 0.8))
            );

            CompletableFuture<Void> result = testStore.add(data, "test_table");

            assertDoesNotThrow(() -> result.join());
        }

        @Test
        @DisplayName("test_add_with_batching - Should add vectors in batches")
        void testAddWithBatching() {
            // Mock collection creation
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock add responses for 2 batches (200 vectors with batch size 100)
            mockServer.enqueue(new MockResponse().setResponseCode(200));
            mockServer.enqueue(new MockResponse().setResponseCode(200));

            List<Map<String, Object>> data = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", "vec" + i);
                item.put("embedding", List.of(0.1, 0.2, 0.3, 0.4));
                data.add(item);
            }

            CompletableFuture<Void> result = testStore.add(data, 100, "test_table");

            assertDoesNotThrow(() -> result.join());
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreSearch")
    class TestMemoryChromaVectorStoreSearch {

        private MockWebServer mockServer;
        private MemoryChromaVectorStore testStore;

        @BeforeEach
        void setUp() throws IOException {
            mockServer = new MockWebServer();
            mockServer.start();
            testStore = new MemoryChromaVectorStore(mockServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
            mockServer.shutdown();
        }

        @Test
        @DisplayName("test_search_validates_table_name - Should validate table name before searching")
        void testSearchValidatesTableName() {
            List<Double> queryVector = List.of(0.1, 0.2, 0.3, 0.4);

            assertThrows(BaseError.class, () ->
                    chromaStore.search(queryVector, 2, (String) null));
        }

        @Test
        @DisplayName("test_search_default_method_throws - Should throw error for default search without table_name")
        void testSearchDefaultMethodThrows() {
            List<Double> queryVector = List.of(0.1, 0.2, 0.3, 0.4);

            // Default search method requires table_name
            assertThrows(BaseError.class, () ->
                    chromaStore.search(queryVector, 2, (Map<String, Object>) null));
        }

        @Test
        @DisplayName("test_search - Should search and return SearchResult list")
        void testSearch() {
            // Mock collection creation
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock search response
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"ids\": [[\"vec1\", \"vec2\"]], \"distances\": [[0.1, 0.3]]}"));

            List<Double> queryVector = List.of(0.1, 0.2, 0.3, 0.4);

            List<SearchResult> results = testStore.search(queryVector, 2, "test_table").join();

            assertNotNull(results);
            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(r -> r instanceof SearchResult));
            assertEquals("vec1", results.get(0).getId());
            assertEquals("vec2", results.get(1).getId());
            // Verify score conversion (1 - distance)
            assertEquals(0.9, results.get(0).getScore(), 0.001);
            assertEquals(0.7, results.get(1).getScore(), 0.001);
        }

        @Test
        @DisplayName("test_search_empty_results - Should return empty list when no results")
        void testSearchEmptyResults() {
            // Mock collection creation
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock empty search response
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"ids\": [[]], \"distances\": [[]]}"));

            List<Double> queryVector = List.of(0.1, 0.2, 0.3, 0.4);

            List<SearchResult> results = testStore.search(queryVector, 2, "test_table").join();

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("test_sparse_search_returns_empty - Should return empty list for sparse search")
        void testSparseSearchReturnsEmpty() throws Exception {
            List<SearchResult> results = chromaStore.sparseSearch("query text", 5, null).join();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("test_hybrid_search_returns_empty - Should return empty list for hybrid search")
        void testHybridSearchReturnsEmpty() throws Exception {
            List<Double> queryVector = List.of(0.1, 0.2, 0.3, 0.4);

            List<SearchResult> results = chromaStore.hybridSearch("query", queryVector, 5, 0.5, null).join();

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreDelete")
    class TestMemoryChromaVectorStoreDelete {

        private MockWebServer mockServer;
        private MemoryChromaVectorStore testStore;

        @BeforeEach
        void setUp() throws IOException {
            mockServer = new MockWebServer();
            mockServer.start();
            testStore = new MemoryChromaVectorStore(mockServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
            mockServer.shutdown();
        }

        @Test
        @DisplayName("test_delete_validates_table_name - Should validate table name before deleting")
        void testDeleteValidatesTableName() {
            assertThrows(BaseError.class, () ->
                    chromaStore.deleteFromTable(List.of("id1"), null));
        }

        @Test
        @DisplayName("test_delete - Should delete vectors from collection")
        void testDelete() {
            // Mock table exists check
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock collection fetch for getOrCreate
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock delete response
            mockServer.enqueue(new MockResponse().setResponseCode(200));

            Boolean result = testStore.deleteFromTable(List.of("vec1"), "test_table").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_delete_nonexistent_collection - Should return true when collection doesn't exist")
        void testDeleteNonexistentCollection() {
            // Mock table exists check - collection doesn't exist
            mockServer.enqueue(new MockResponse().setResponseCode(404));

            Boolean result = testStore.deleteFromTable(List.of("vec1"), "test_table").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_delete_empty_ids_returns_true - Should return true when ids list is empty")
        void testDeleteEmptyIdsReturnsTrue() {
            // Mock table exists check
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));

            Boolean result = testStore.deleteFromTable(Collections.emptyList(), "test_table").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_delete_default_method_throws - Should throw error for default delete without table_name")
        void testDeleteDefaultMethodThrows() throws Exception {
            // Default delete method requires table_name, but filterExpr overload doesn't support it
            assertThrows(BaseError.class, () -> chromaStore.delete(List.of("id1"), "filter"));
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreDeleteTable")
    class TestMemoryChromaVectorStoreDeleteTable {

        private MockWebServer mockServer;
        private MemoryChromaVectorStore testStore;

        @BeforeEach
        void setUp() throws IOException {
            mockServer = new MockWebServer();
            mockServer.start();
            testStore = new MemoryChromaVectorStore(mockServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
            mockServer.shutdown();
        }

        @Test
        @DisplayName("test_delete_table - Should delete table and remove from cache")
        void testDeleteTable() {
            // Pre-populate cache
            testStore.getCollectionCache().put("test_table", "collection-uuid");

            // Mock table exists check
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": \"collection-uuid\", \"name\": \"test_table\"}"));
            // Mock delete response
            mockServer.enqueue(new MockResponse().setResponseCode(200));

            Boolean result = testStore.deleteTable("test_table").join();

            assertTrue(result);
            assertFalse(testStore.getCollectionCache().containsKey("test_table"));
        }

        @Test
        @DisplayName("test_delete_nonexistent_table - Should return true when table doesn't exist")
        void testDeleteNonexistentTable() {
            // Mock table exists check - table doesn't exist
            mockServer.enqueue(new MockResponse().setResponseCode(404));

            Boolean result = testStore.deleteTable("non_existent_table").join();

            assertTrue(result);
        }

        @Test
        @DisplayName("test_delete_table_removes_from_cache - Should remove from cache after deletion")
        void testDeleteTableRemovesFromCache() {
            // Pre-populate cache
            chromaStore.getCollectionCache().put("test_table", "uuid");

            // Remove from cache
            chromaStore.removeCollectionFromCache("test_table");

            assertFalse(chromaStore.getCollectionCache().containsKey("test_table"));
        }
    }

    @Nested
    @DisplayName("TestMemoryChromaVectorStoreCheckVectorField")
    class TestMemoryChromaVectorStoreCheckVectorField {

        @Test
        @DisplayName("test_check_vector_field_logs_error - Should log error (placeholder implementation)")
        void testCheckVectorFieldLogsError() {
            // This should not throw, just log an error
            assertDoesNotThrow(() -> chromaStore.checkVectorField());
        }
    }
}
