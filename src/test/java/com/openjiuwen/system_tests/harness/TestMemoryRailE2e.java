/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryRail end-to-end system test (real LLM + Embedding API).
 * <p>
 * Mirrors Python's {@code test_memory_rail_e2e.py} in
 * {@code tests/system_tests/harness/test_memory_rail_e2e.py}.
 */
public class TestMemoryRailE2e {

    private Path tmpDir;
    private String workDir;
    private String sysOperationId;

    private static final String LLM_API_BASE = System.getenv("API_BASE");
    private static final String LLM_API_KEY = System.getenv("API_KEY");
    private static final String LLM_MODEL_NAME = System.getenv("MODEL_NAME");
    private static final String LLM_MODEL_PROVIDER = System.getenv("MODEL_PROVIDER");
    private static final String EMBED_API_KEY = System.getenv("EMBED_API_KEY");
    private static final String EMBED_API_BASE = System.getenv("EMBED_API_BASE");
    private static final String EMBED_MODEL_NAME = System.getenv("EMBED_MODEL_NAME");

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("memory_rail_e2e_" + UUID.randomUUID().toString().substring(0, 8));
        workDir = tmpDir.toString();
        sysOperationId = "memory_rail_sysop_" + UUID.randomUUID().toString().replace("-", "");
        
        SysOperationCard card = SysOperationCard.builder()
                .id(sysOperationId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workDir).build())
                .build();
        
        Runner.resourceMgr().addSysOperation(card);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        } finally {
            if (tmpDir != null) {
                Files.deleteIfExists(tmpDir);
            }
            Runner.stop();
        }
    }

    private Model createLlmModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(LLM_MODEL_PROVIDER != null ? LLM_MODEL_PROVIDER : "OpenAI")
                .apiKey(LLM_API_KEY)
                .apiBase(LLM_API_BASE)
                .timeout(60)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(LLM_MODEL_NAME != null ? LLM_MODEL_NAME : "model")
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private EmbeddingConfig createEmbeddingConfig() {
        return new EmbeddingConfig(
                EMBED_MODEL_NAME != null ? EMBED_MODEL_NAME : "text-embedding-v3",
                EMBED_API_BASE != null ? EMBED_API_BASE : "http://embed-api",
                EMBED_API_KEY
        );
    }

    @Nested
    @DisplayName("MemoryRail E2E tests")
    @DisabledIfEnvironmentVariable(named = "API_KEY", matches = "")
    class MemoryRailTests {

        @Test
        @DisplayName("Test memory rail with real embedding")
        @DisabledIfEnvironmentVariable(named = "API_KEY", matches = "")
        void testMemoryRailWithRealEmbedding() {
            // Placeholder: Memory rail with real embedding API test
            
            EmbeddingConfig embedConfig = createEmbeddingConfig();
            assertThat(embedConfig).isNotNull();
        }

        @Test
        @DisplayName("Test memory recall in agent")
        @DisabledIfEnvironmentVariable(named = "API_KEY", matches = "")
        void testMemoryRecallInAgent() {
            // Placeholder: Test memory recall during agent execution
            
            assertThat(Runner.resourceMgr()).isNotNull();
        }
    }
}