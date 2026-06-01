/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests/unit_tests/core/retrieval/common/test_config.py.
 */
class ConfigTest {

    @Test
    void retrievalConfigRejectsNonPositiveTopK() {
        RetrievalConfig config = new RetrievalConfig();
        assertThrows(BaseError.class, () -> config.setTopK(0));
        assertThrows(BaseError.class, () -> config.setTopK(-1));
    }

    @Test
    void rerankerConfigAppliesDefaultsAndValidation() {
        RerankerConfig config = new RerankerConfig("https://api.example.com");
        assertEquals("https://api.example.com", config.getApiBase());
        assertEquals(10.0, config.getTimeout());

        config.setExtraBody(Map.of("provider", "openai"));
        assertEquals("openai", config.getExtraBody().get("provider"));
        assertThrows(BaseError.class, () -> config.setTimeout(0.0));
        assertThrows(BaseError.class, () -> config.setApiBase(" "));
    }

    @Test
    void rankConfigsExposeArgsAndRegistry() {
        ResultRankRegistry.registerResultRankerClass("milvus", String.class, Integer.class, Map.of());

        WeightedRankConfig weighted = new WeightedRankConfig();
        weighted.setDenseName(0.0);
        weighted.setDenseContent(0.6);
        weighted.setSparseContent(0.4);
        assertEquals(2, weighted.getArgs().positional().size());
        assertEquals(String.class, weighted.getRankerClass("milvus"));

        RRFRankConfig rrf = new RRFRankConfig();
        rrf.setDenseContent(false);
        assertEquals(java.util.List.of(1, 0, 1), rrf.isActive());
        assertEquals(Integer.class, rrf.getRankerClass("milvus"));
    }

    @Test
    void vectorStoreConfigExposesEnumView() {
        VectorStoreConfig config = new VectorStoreConfig(StoreType.CHROMA, "test_collection");
        assertEquals(StoreType.CHROMA, config.getStoreType());
    }

    @Test
    void knowledgeBaseConfigDefaultValues() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
        assertEquals("test_kb", config.getKbId());
        assertEquals("hybrid", config.getIndexType());
        assertFalse(config.isUseGraph());
        assertEquals(512, config.getChunkSize());
        assertEquals(50, config.getChunkOverlap());
    }

    @Test
    void knowledgeBaseConfigCustomValues() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb", "vector", true, 1024, 100);

        assertEquals("test_kb", config.getKbId());
        assertEquals("vector", config.getIndexType());
        assertTrue(config.isUseGraph());
        assertEquals(1024, config.getChunkSize());
        assertEquals(100, config.getChunkOverlap());
    }

    @Test
    void knowledgeBaseConfigRejectsInvalidIndexType() {
        assertThrows(BaseError.class, () -> new KnowledgeBaseConfig("test_kb", "invalid", false, 512, 50));
    }

    @Test
    void knowledgeBaseConfigRequiresKbId() {
        assertThrows(BaseError.class, () -> new KnowledgeBaseConfig().validate());
    }

    @Test
    void retrievalConfigDefaultValues() {
        RetrievalConfig config = new RetrievalConfig();
        assertEquals(5, config.getTopK());
        assertNull(config.getScoreThreshold());
        assertNull(config.getUseGraph());
        assertFalse(config.isAgentic());
        assertFalse(config.isGraphExpansion());
        assertNull(config.getFilters());
    }

    @Test
    void retrievalConfigCustomValues() {
        RetrievalConfig config = new RetrievalConfig();
        config.setTopK(10);
        config.setScoreThreshold(0.7);
        config.setUseGraph(true);
        config.setAgentic(true);
        config.setGraphExpansion(true);
        config.setFilters(Map.of("doc_id", "test"));

        assertEquals(10, config.getTopK());
        assertEquals(0.7, config.getScoreThreshold());
        assertEquals(true, config.getUseGraph());
        assertTrue(config.isAgentic());
        assertTrue(config.isGraphExpansion());
        assertEquals(Map.of("doc_id", "test"), config.getFilters());
    }

    @Test
    void indexConfigDefaultValues() {
        IndexConfig config = new IndexConfig("test_index");

        assertEquals("test_index", config.getIndexName());
        assertEquals("hybrid", config.getIndexType());
    }

    @Test
    void indexConfigCustomValues() {
        IndexConfig config = new IndexConfig("test_index", "vector");

        assertEquals("test_index", config.getIndexName());
        assertEquals("vector", config.getIndexType());
    }

    @Test
    void indexConfigRejectsInvalidIndexType() {
        assertThrows(BaseError.class, () -> new IndexConfig("test_index", "invalid"));
    }

    @Test
    void indexConfigRequiresIndexName() {
        assertThrows(BaseError.class, () -> new IndexConfig().validate());
    }

    @Test
    void vectorStoreConfigDefaultValues() {
        VectorStoreConfig config = new VectorStoreConfig("milvus", "test_collection");

        assertEquals("milvus", config.getStoreProvider());
        assertEquals("test_collection", config.getCollectionName());
        assertEquals("cosine", config.getDistanceMetric());
    }

    @Test
    void vectorStoreConfigCustomValues() {
        VectorStoreConfig config = new VectorStoreConfig("chroma", "", "test_collection", "euclidean");

        assertEquals("chroma", config.getStoreProvider());
        assertEquals("test_collection", config.getCollectionName());
        assertEquals("euclidean", config.getDistanceMetric());
    }

    @Test
    void vectorStoreConfigRejectsInvalidDistanceMetric() {
        assertThrows(BaseError.class, () -> new VectorStoreConfig("milvus", "", "test_collection", "invalid"));
    }

    @Test
    void vectorStoreConfigRequiresCollectionName() {
        assertThrows(BaseError.class, () -> new VectorStoreConfig().validate());
    }

    @Test
    void embeddingConfigRequiredFields() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com");
        assertEquals("test-model", config.getModelName());
        assertEquals("https://api.example.com", config.getBaseUrl());
        assertNull(config.getApiKey());
    }

    @Test
    void embeddingConfigAllFields() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com", "test-key");

        assertEquals("test-model", config.getModelName());
        assertEquals("test-key", config.getApiKey());
        assertEquals("https://api.example.com", config.getBaseUrl());
    }

    @Test
    void embeddingConfigBasicValidation() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com");
        assertNotNull(config.getBaseUrl());
        assertTrue(config.isVerifySsl());

        config.setVerifySsl(false);
        config.setSslCert("/tmp/ca.pem");
        assertFalse(config.isVerifySsl());
        assertEquals("/tmp/ca.pem", config.getSslCert());
    }

    @Test
    void embeddingConfigRequiresModelName() {
        assertThrows(BaseError.class, () -> new EmbeddingConfig(null, "https://api.example.com"));
    }
}
