/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
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
    }

    @Test
    void retrievalConfigCreationAndFilters() {
        RetrievalConfig config = new RetrievalConfig();
        assertEquals(5, config.getTopK());
        assertNull(config.getScoreThreshold());
        config.setTopK(10);
        assertEquals(10, config.getTopK());
    }

    @Test
    void embeddingConfigBasicValidation() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com");
        assertEquals("test-model", config.getModelName());
        assertNotNull(config.getBaseUrl());
        assertTrue(config.isVerifySsl());

        config.setVerifySsl(false);
        config.setSslCert("/tmp/ca.pem");
        assertFalse(config.isVerifySsl());
        assertEquals("/tmp/ca.pem", config.getSslCert());
    }
}
