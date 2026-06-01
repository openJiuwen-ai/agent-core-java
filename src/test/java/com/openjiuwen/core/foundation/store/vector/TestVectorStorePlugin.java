/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.foundation.store.StoreFactory;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the vector-store factory plugin framework.
 * <p>
 * Mirrors Python's {@code test_vector_store_plugin.py}.
 */
@DisplayName("Vector Store Plugin Tests")
class TestVectorStorePlugin {

    @Nested
    class TestBuiltinRegression {
        @Test
        void testUnknownReturnsNone() {
            assertNull(StoreFactory.createVectorStore("this_backend_does_not_exist"));
        }

        @Test
        void testChromaDispatchesToChromaClass() {
            BaseVectorStore store = StoreFactory.createVectorStore("chroma",
                    Map.of("database_name", uniqueDb()));
            assertInstanceOf(ChromaVectorStore.class, store);
        }

        @Test
        void testMilvusDispatchesToMilvusClass() {
            BaseVectorStore store = StoreFactory.createVectorStore("milvus",
                    Map.of("milvus_uri", "http://localhost:19530", "database_name", uniqueDb()));
            assertInstanceOf(MilvusVectorStore.class, store);
        }

        @Test
        void testGaussvectorDispatchesToGaussClass() {
            BaseVectorStore store = StoreFactory.createVectorStore("gaussvector",
                    Map.of("database_name", uniqueDb()));
            assertInstanceOf(GaussVectorStore.class, store);
        }
    }

    @Nested
    class TestExplicitRegistration {
        @Test
        void testRegisterThenCreate() {
            String name = "test_fake_" + uniqueDb();
            StoreFactory.registerVectorStore(name, options -> new InMemoryVectorStore(options));

            BaseVectorStore store = StoreFactory.createVectorStore(name,
                    Map.of("database_name", uniqueDb(), "collection_name", "custom_collection"));

            assertInstanceOf(InMemoryVectorStore.class, store);
        }

        @Test
        void testRegisterDoesNotShadowBuiltin() {
            StoreFactory.registerVectorStore("chroma", options -> new FakeVectorStore());

            BaseVectorStore store = StoreFactory.createVectorStore("chroma",
                    Map.of("database_name", uniqueDb()));

            assertInstanceOf(ChromaVectorStore.class, store);
        }
    }

    @Nested
    class TestEntryPointsDiscovery {
        @Test
        void testEntryPointIsDiscovered() {
            String name = "test_ep_fake_" + uniqueDb();
            StoreFactory.registerVectorStore(name, options -> new FakeVectorStore());

            assertInstanceOf(FakeVectorStore.class, StoreFactory.createVectorStore(name));
        }

        @Test
        void testEntryPointLoadErrorIsSwallowed() {
            String name = "broken_" + uniqueDb();
            StoreFactory.registerVectorStore(name, options -> {
                throw new IllegalStateException("fake import failure");
            });

            assertNull(StoreFactory.createVectorStore(name));
        }

        @Test
        void testBuiltinWinsOverEntryPoint() {
            StoreFactory.registerVectorStore("chroma", options -> new FakeVectorStore());

            assertInstanceOf(ChromaVectorStore.class, StoreFactory.createVectorStore("chroma",
                    Map.of("database_name", uniqueDb())));
        }
    }

    @Nested
    class TestEntryPointsGroupName {
        @Test
        void testGroupNameIsDocumentedConstant() {
            assertEquals("openjiuwen.vector_stores", StoreFactory.VECTOR_STORE_ENTRY_POINT_GROUP);
        }
    }

    private static String uniqueDb() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static class FakeVectorStore extends InMemoryVectorStore {
        FakeVectorStore() {
            super(Map.of("database_name", uniqueDb()));
        }
    }
}
