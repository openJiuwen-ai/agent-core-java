/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.vector_store.test_store} in
 * {@code tests/unit_tests/core/retrieval/vector_store/test_store.py}.</p>
 */
class VectorStoreFactoryPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void createMilvusStore() {
        VectorStore store = VectorStoreFactory.createVectorStore(milvusConfig(), milvusOptions());

        assertThat(store).isInstanceOf(MilvusVectorStore.class);
    }

    @Test
    void createChromaStore() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("chroma_path", tempDir.resolve("test_chroma").toString());

        VectorStore store = VectorStoreFactory.createVectorStore(chromaConfig(), kwargs);

        assertThat(store).isInstanceOf(ChromaVectorStore.class);
    }

    @Disabled("Python baseline failed: tests.unit_tests.core.retrieval.vector_store.test_store."
            + "TestCreateVectorStore::test_create_pgvector_store; latest-summary.json records "
            + "AttributeError because openjiuwen.core.retrieval.vector_store had no pg_store attribute.")
    @Test
    void createPgvectorStore() {
        assertThat(true).isTrue();
    }

    @Test
    void createMilvusStoreByStringProvider() {
        VectorStoreConfig config = new VectorStoreConfig(
                StoreType.fromValue("milvus"),
                "",
                "test_collection",
                "cosine"
        );

        VectorStore store = VectorStoreFactory.createVectorStore(config, milvusOptions());

        assertThat(store).isInstanceOf(MilvusVectorStore.class);
    }

    @Test
    void createVectorStoreInvalidProvider() {
        VectorStoreConfig config = new VectorStoreConfig(null, "", "test_collection", "cosine");

        assertThatThrownBy(() -> VectorStoreFactory.createVectorStore(config))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_VECTOR_STORE_PROVIDER_INVALID);
    }

    @Test
    void createVectorStorePassesKwargsToStore() {
        Map<String, Object> kwargs = milvusOptions();
        kwargs.put("milvus_token", "secret_token");

        VectorStore store = VectorStoreFactory.createVectorStore(milvusConfig(), kwargs);

        assertThat(store).isInstanceOf(MilvusVectorStore.class);
        assertThat(((MilvusVectorStore) store).getMilvusToken()).isEqualTo("secret_token");
    }

    @Test
    void createMilvusStoreCollectionNamePreserved() {
        VectorStore store = VectorStoreFactory.createVectorStore(milvusConfig(), milvusOptions());

        assertThat(store).isInstanceOf(MilvusVectorStore.class);
        assertThat(((MilvusVectorStore) store).getCollectionName()).isEqualTo("test_collection");
    }

    private static VectorStoreConfig milvusConfig() {
        return new VectorStoreConfig(StoreType.MILVUS, "", "test_collection", "cosine");
    }

    private static VectorStoreConfig chromaConfig() {
        return new VectorStoreConfig(StoreType.CHROMA, "", "test_collection", "cosine");
    }

    private static Map<String, Object> milvusOptions() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("milvus_uri", "http://localhost:19530");
        kwargs.put("milvus_client", new StubMilvusClientFacade());
        return kwargs;
    }

    private static final class StubMilvusClientFacade implements MilvusVectorStore.MilvusClientFacade {
        @Override
        public boolean hasCollection(String collectionName) {
            return false;
        }

        @Override
        public void loadCollection(String collectionName) {
        }

        @Override
        public void insert(String collectionName, List<Map<String, Object>> rows, int batchSize) {
        }

        @Override
        public List<MilvusVectorStore.SearchHit> search(String collectionName,
                                                        MilvusVectorStore.SearchRequest request,
                                                        String metricType,
                                                        int topK,
                                                        List<String> outputFields,
                                                        Map<String, Object> searchParams,
                                                        String filter) {
            return List.of();
        }

        @Override
        public List<MilvusVectorStore.SearchHit> hybridSearch(String collectionName,
                                                              List<MilvusVectorStore.SearchRequest> requests,
                                                              int topK,
                                                              List<String> outputFields) {
            return List.of();
        }

        @Override
        public long delete(String collectionName, List<String> ids, String filter) {
            return 0L;
        }

        @Override
        public void flush(String collectionName) {
        }

        @Override
        public void dropCollection(String collectionName) {
        }

        @Override
        public Map<String, Object> describeIndex(String collectionName, String fieldName) {
            return Map.of();
        }

        @Override
        public MilvusVectorStore.CollectionDescription describeCollection(String collectionName) {
            return new MilvusVectorStore.CollectionDescription(List.of());
        }

        @Override
        public void close() {
        }
    }
}
