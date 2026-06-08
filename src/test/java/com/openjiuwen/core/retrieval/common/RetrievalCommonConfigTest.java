/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalCommonConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
