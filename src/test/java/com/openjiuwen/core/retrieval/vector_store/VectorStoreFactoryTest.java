/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.PGVectorField;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link VectorStoreFactory}.
 *
 * <p>Mirrors Python's {@code create_vector_store} in
 * {@code openjiuwen/core/retrieval/vector_store/store.py}.</p>
 */
class VectorStoreFactoryTest {

    @Test
    void createVectorStoreReturnsChromaStore() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("chroma_path", "/tmp/chroma");
        kwargs.put("text_field", "body");
        kwargs.put("vector_field", "vector");
        kwargs.put("sparse_vector_field", "sparse");
        kwargs.put("metadata_field", "meta");
        kwargs.put("doc_id_field", "doc");

        VectorStore store = VectorStoreFactory.createVectorStore(config(StoreType.CHROMA), kwargs);

        assertThat(store).isInstanceOf(ChromaVectorStore.class);
        ChromaVectorStore chromaStore = (ChromaVectorStore) store;
        assertThat(chromaStore.getChromaPath()).isEqualTo("/tmp/chroma");
        assertThat(chromaStore.getTextField()).isEqualTo("body");
        assertThat(chromaStore.getVectorField().getVectorField()).isEqualTo("vector");
        assertThat(chromaStore.getSparseVectorField()).isEqualTo("sparse");
        assertThat(chromaStore.getMetadataField()).isEqualTo("meta");
        assertThat(chromaStore.getDocIdField()).isEqualTo("doc");
    }

    @Test
    void createVectorStoreReturnsPgVectorStore() {
        PGVectorField field = new PGVectorField();
        field.setVectorField("embedding_vec");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("pg_uri", "postgresql+asyncpg://localhost/db");
        kwargs.put("text_field", "body");
        kwargs.put("vector_field", field);
        kwargs.put("sparse_vector_field", "sparse");
        kwargs.put("metadata_field", "meta");
        kwargs.put("doc_id_field", "doc");

        VectorStore store = VectorStoreFactory.createVectorStore(config(StoreType.PGVECTOR), kwargs);

        assertThat(store).isInstanceOf(PGVectorStore.class);
        PGVectorStore pgVectorStore = (PGVectorStore) store;
        assertThat(pgVectorStore.getPgUri()).isEqualTo("postgresql+asyncpg://localhost/db");
        assertThat(pgVectorStore.getTextField()).isEqualTo("body");
        assertThat(pgVectorStore.getVectorField()).isEqualTo("embedding_vec");
        assertThat(pgVectorStore.getSparseVectorField()).isEqualTo("sparse");
        assertThat(pgVectorStore.getMetadataField()).isEqualTo("meta");
        assertThat(pgVectorStore.getDocIdField()).isEqualTo("doc");
    }

    @Test
    void createVectorStoreReturnsMilvusStoreWithInjectedFacade() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("milvus_uri", "http://localhost:19530");
        kwargs.put("milvus_token", "token");
        kwargs.put("text_field", "body");
        kwargs.put("vector_field", "vector");
        kwargs.put("sparse_vector_field", "sparse");
        kwargs.put("metadata_field", "meta");
        kwargs.put("doc_id_field", "doc");
        kwargs.put("milvus_alias", "alias");
        kwargs.put("milvus_client", new StubMilvusClientFacade());

        VectorStore store = VectorStoreFactory.createVectorStore(config(StoreType.MILVUS), kwargs);

        assertThat(store).isInstanceOf(MilvusVectorStore.class);
        MilvusVectorStore milvusStore = (MilvusVectorStore) store;
        assertThat(milvusStore.getMilvusUri()).isEqualTo("http://localhost:19530");
        assertThat(milvusStore.getMilvusToken()).isEqualTo("token");
        assertThat(milvusStore.getTextField()).isEqualTo("body");
        assertThat(milvusStore.getVectorField()).isEqualTo("vector");
        assertThat(milvusStore.getSparseVectorField()).isEqualTo("sparse");
        assertThat(milvusStore.getMetadataField()).isEqualTo("meta");
        assertThat(milvusStore.getDocIdField()).isEqualTo("doc");
    }

    @Test
    void createVectorStoreRaisesPythonStatusForUnavailableProvider() {
        VectorStoreConfig config = new VectorStoreConfig(null, "", "collection", "cosine");

        assertThatThrownBy(() -> VectorStoreFactory.createVectorStore(config))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_VECTOR_STORE_PROVIDER_INVALID);
    }

    private static VectorStoreConfig config(StoreType storeType) {
        return new VectorStoreConfig(storeType, "", "collection", "cosine");
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
