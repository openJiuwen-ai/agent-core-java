/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.DbBasedKVStore;
import com.openjiuwen.core.foundation.store.vector.ChromaVectorStore;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the foundation store package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.store} module in
 * {@code openjiuwen/core/foundation/store/__init__.py}.</p>
 */
class FoundationStorePackageTest {

    @AfterEach
    void clearRegistry() {
        FoundationStorePackage.clearCustomVectorStoresForTest();
    }

    @Test
    void exposesPythonAllInOrder() {
        List<String> expected = List.of(
                "BaseKVStore",
                "BaseMessageStore",
                "MessageMetadata",
                "BaseVectorStore",
                "InMemoryKVStore",
                "VectorSearchResult",
                "CollectionSchema",
                "FieldSchema",
                "VectorDataType",
                "create_vector_store",
                "register_vector_store",
                "VECTOR_STORE_ENTRY_POINT_GROUP",
                "vector_fields",
                "vector",
                "query",
                "object",
                "kv",
                "graph",
                "db",
                "BaseDbStore",
                "DbBasedKVStore",
                "DefaultDbStore"
        );

        assertEquals("openjiuwen/core/foundation/store/__init__.py", FoundationStorePackage.PYTHON_MODULE);
        assertEquals("openjiuwen.vector_stores", FoundationStorePackage.VECTOR_STORE_ENTRY_POINT_GROUP);
        assertIterableEquals(expected, FoundationStorePackage.EXPORTED_SYMBOLS);
        assertSame(FoundationStorePackage.EXPORTED_SYMBOLS, FoundationStorePackage.all());
    }

    @Test
    void dirSymbolsAppendLazyAttributesAgainLikePython() {
        List<String> symbols = FoundationStorePackage.dirSymbols();

        assertEquals(25, symbols.size());
        assertEquals("BaseKVStore", symbols.get(0));
        assertEquals("BaseDbStore", symbols.get(22));
        assertEquals("DbBasedKVStore", symbols.get(23));
        assertEquals("DefaultDbStore", symbols.get(24));
    }

    @Test
    void resolvesLazySqlAttributes() {
        assertEquals(BaseDbStore.class, FoundationStorePackage.resolveLazyAttribute("BaseDbStore"));
        assertEquals(DbBasedKVStore.class, FoundationStorePackage.resolveLazyAttribute("DbBasedKVStore"));
        assertEquals(DefaultDbStore.class, FoundationStorePackage.resolveLazyAttribute("DefaultDbStore"));
        assertThrows(IllegalArgumentException.class,
                () -> FoundationStorePackage.resolveLazyAttribute("missing"));
    }

    @Test
    void customVectorStoreRegistrationIsUsedForNonBuiltins() {
        AtomicReference<Map<String, Object>> seenKwargs = new AtomicReference<>();
        DummyVectorStore expectedStore = new DummyVectorStore();
        FoundationStorePackage.registerVectorStore("private", kwargs -> {
            seenKwargs.set(kwargs);
            return expectedStore;
        });

        Map<String, Object> kwargs = Map.of("collection", "docs");
        BaseVectorStore actualStore = FoundationStorePackage.createVectorStore("private", kwargs);

        assertSame(expectedStore, actualStore);
        assertSame(kwargs, seenKwargs.get());
    }

    @Test
    void builtInNamesAreClosedSetAndWinBeforeCustomRegistry() {
        FoundationStorePackage.register_vector_store("chroma", kwargs -> new DummyVectorStore());

        assertTrue(FoundationStorePackage.isBuiltinVectorStore("chroma"));
        assertTrue(FoundationStorePackage.isBuiltinVectorStore("milvus"));
        assertTrue(FoundationStorePackage.isBuiltinVectorStore("gaussvector"));

        BaseVectorStore store = FoundationStorePackage.create_vector_store("chroma", Map.of());

        assertInstanceOf(ChromaVectorStore.class, store);
    }

    @Test
    void missingStoreReturnsNull() {
        assertNull(FoundationStorePackage.createVectorStore("missing", Map.of()));
    }

    private static final class DummyVectorStore extends BaseVectorStore {
        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(String collectionName, List<Double> queryVector,
                String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }
}
