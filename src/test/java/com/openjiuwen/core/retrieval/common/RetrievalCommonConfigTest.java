/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's retrieval common config models in
 * {@code openjiuwen/core/retrieval/common/config.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.common.test_config} in
 * {@code tests/unit_tests/core/retrieval/common/test_config.py}.</p>
 */
class RetrievalCommonConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void knowledgeBaseConfigCreateWithDefaults() {
        KnowledgeBaseConfig config = KnowledgeBaseConfig.builder().kbId("test_kb").build();

        assertThat(config.getKbId()).isEqualTo("test_kb");
        assertThat(config.getIndexType()).isEqualTo("hybrid");
        assertThat(config.isUseGraph()).isFalse();
        assertThat(config.getChunkSize()).isEqualTo(512);
        assertThat(config.getChunkOverlap()).isEqualTo(50);
    }

    @Test
    void knowledgeBaseConfigCreateWithCustomValues() {
        KnowledgeBaseConfig config = KnowledgeBaseConfig.builder()
                .kbId("test_kb")
                .indexType("vector")
                .useGraph(true)
                .chunkSize(1024)
                .chunkOverlap(100)
                .build();

        assertThat(config.getKbId()).isEqualTo("test_kb");
        assertThat(config.getIndexType()).isEqualTo("vector");
        assertThat(config.isUseGraph()).isTrue();
        assertThat(config.getChunkSize()).isEqualTo(1024);
        assertThat(config.getChunkOverlap()).isEqualTo(100);
    }

    @Test
    void knowledgeBaseConfigInvalidIndexType() {
        assertThatThrownBy(() -> KnowledgeBaseConfig.builder().kbId("test_kb").indexType("invalid").build())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.SCHEMA_VALIDATE_INVALID);
    }

    @Test
    void knowledgeBaseConfigRejectsNonPositiveChunkSize() {
        assertThatThrownBy(() -> KnowledgeBaseConfig.builder().kbId("test_kb").chunkSize(0).build())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID);
    }

    @Test
    void knowledgeBaseConfigRejectsNegativeChunkOverlap() {
        assertThatThrownBy(() -> KnowledgeBaseConfig.builder().kbId("test_kb").chunkOverlap(-1).build())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID);
    }

    @Test
    void knowledgeBaseConfigKeepsExistingValidationSemantics() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig(" ");
        config.setIndexType(null);

        assertThat(config.getKbId()).isEqualTo(" ");
        assertThat(config.getIndexType()).isEqualTo("hybrid");
        assertThatThrownBy(() -> config.setIndexType("VECTOR"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("index_type");
    }

    @Test
    void knowledgeBaseConfigMissingKbId() {
        assertThatThrownBy(() -> KnowledgeBaseConfig.builder().build())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.SCHEMA_VALIDATE_INVALID);
    }

    @Test
    void retrievalConfigCreateWithDefaults() {
        RetrievalConfig config = new RetrievalConfig();

        assertThat(config.getTopK()).isEqualTo(5);
        assertThat(config.getScoreThreshold()).isNull();
        assertThat(config.getUseGraph()).isNull();
        assertThat(config.isAgentic()).isFalse();
        assertThat(config.isGraphExpansion()).isFalse();
        assertThat(config.getFilters()).isNull();
    }

    @Test
    void retrievalConfigCreateWithCustomValues() {
        RetrievalConfig config = RetrievalConfig.builder()
                .topK(10)
                .scoreThreshold(0.7)
                .useGraph(true)
                .agentic(true)
                .graphExpansion(true)
                .filters(Map.of("doc_id", "test"))
                .build();

        assertThat(config.getTopK()).isEqualTo(10);
        assertThat(config.getScoreThreshold()).isEqualTo(0.7);
        assertThat(config.getUseGraph()).isTrue();
        assertThat(config.isAgentic()).isTrue();
        assertThat(config.isGraphExpansion()).isTrue();
        assertThat(config.getFilters()).containsEntry("doc_id", "test");
    }

    @Test
    void indexConfigCreateWithDefaults() {
        IndexConfig config = IndexConfig.builder().indexName("test_index").build();

        assertThat(config.getIndexName()).isEqualTo("test_index");
        assertThat(config.getIndexType()).isEqualTo("hybrid");
    }

    @Test
    void indexConfigCreateWithCustomValues() {
        IndexConfig config = IndexConfig.builder().indexName("test_index").indexType("vector").build();

        assertThat(config.getIndexName()).isEqualTo("test_index");
        assertThat(config.getIndexType()).isEqualTo("vector");
    }

    @Test
    void indexConfigInvalidIndexType() {
        assertThatThrownBy(() -> IndexConfig.builder().indexName("test_index").indexType("invalid").build())
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("index_type");
    }

    @Test
    void indexConfigMissingIndexName() {
        assertThatThrownBy(() -> IndexConfig.builder().build())
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("index_name");
    }

    @Test
    void vectorStoreConfigCreateWithDefaults() {
        VectorStoreConfig config = new VectorStoreConfig(StoreType.MILVUS, "", "test_collection", null);

        assertThat(config.getStoreProvider()).isEqualTo(StoreType.MILVUS);
        assertThat(config.getCollectionName()).isEqualTo("test_collection");
        assertThat(config.getDistanceMetric()).isEqualTo("cosine");
    }

    @Test
    void vectorStoreConfigCreateWithCustomValues() {
        VectorStoreConfig config = new VectorStoreConfig(StoreType.CHROMA, "", "test_collection", "euclidean");

        assertThat(config.getStoreProvider()).isEqualTo(StoreType.CHROMA);
        assertThat(config.getCollectionName()).isEqualTo("test_collection");
        assertThat(config.getDistanceMetric()).isEqualTo("euclidean");
    }

    @Test
    void vectorStoreConfigInvalidDistanceMetric() {
        assertThatThrownBy(() -> new VectorStoreConfig(StoreType.MILVUS, "", "test_collection", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distance_metric");
    }

    @Test
    void vectorStoreConfigMissingCollectionName() {
        assertThatThrownBy(() -> VectorStoreConfig.builder().storeProvider(StoreType.MILVUS).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collection_name");
    }

    @Test
    void embeddingConfigCreateWithRequiredFields() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("test_model")
                .baseUrl("https://api.example.com")
                .build();

        assertThat(config.getModelName()).isEqualTo("test_model");
        assertThat(config.getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(config.getApiKey()).isNull();
    }

    @Test
    void embeddingConfigCreateWithAllFields() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("test_model")
                .apiKey("test_key")
                .baseUrl("https://api.example.com")
                .build();

        assertThat(config.getModelName()).isEqualTo("test_model");
        assertThat(config.getApiKey()).isEqualTo("test_key");
        assertThat(config.getBaseUrl()).isEqualTo("https://api.example.com");
    }

    @Test
    void embeddingConfigMissingModelName() {
        assertThatThrownBy(() -> EmbeddingConfig.builder().baseUrl("https://api.example.com").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model_name");
    }

    @Test
    void knowledgeBaseAndIndexConfigsKeepPythonDefaults() {
        KnowledgeBaseConfig knowledgeBaseConfig = KnowledgeBaseConfig.builder().kbId("kb-1").build();
        IndexConfig indexConfig = IndexConfig.builder().indexName("idx-1").build();

        assertThat(knowledgeBaseConfig.getIndexType()).isEqualTo("hybrid");
        assertThat(knowledgeBaseConfig.isUseGraph()).isFalse();
        assertThat(knowledgeBaseConfig.getChunkSize()).isEqualTo(512);
        assertThat(knowledgeBaseConfig.getChunkOverlap()).isEqualTo(50);
        assertThat(knowledgeBaseConfig.isUseCaptionForImages()).isFalse();
        assertThat(indexConfig.getIndexType()).isEqualTo("hybrid");
        assertThat(indexConfig.isUseCaptionForImages()).isFalse();
    }

    @Test
    void retrievalConfigPreservesOptionalFieldsAndSnakeCase() throws Exception {
        RetrievalConfig retrievalConfig = RetrievalConfig.builder()
                .filters(Map.of("lang", "zh"))
                .build();

        String json = objectMapper.writeValueAsString(retrievalConfig);

        assertThat(retrievalConfig.getTopK()).isEqualTo(5);
        assertThat(retrievalConfig.getScoreThreshold()).isNull();
        assertThat(retrievalConfig.getUseGraph()).isNull();
        assertThat(retrievalConfig.isAgentic()).isFalse();
        assertThat(retrievalConfig.isGraphExpansion()).isFalse();
        assertThat(retrievalConfig.getFilters()).containsEntry("lang", "zh");
        assertThat(json).contains("\"top_k\":5");
        assertThat(json).contains("\"graph_expansion\":false");
    }

    @Test
    void vectorStoreConfigSerializesEnumAndValidatesDatabaseName() throws Exception {
        VectorStoreConfig config = new VectorStoreConfig(StoreType.PGVECTOR, "db_01", "collection-a", null);

        String json = objectMapper.writeValueAsString(config);

        assertThat(config.getDistanceMetric()).isEqualTo("cosine");
        assertThat(StoreType.fromValue("milvus")).isEqualTo(StoreType.MILVUS);
        assertThat(json).contains("\"store_provider\":\"pgvector\"");
        assertThat(json).contains("\"database_name\":\"db_01\"");
        assertThatThrownBy(() -> config.setDatabaseName("bad-name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database_name");
    }
}
