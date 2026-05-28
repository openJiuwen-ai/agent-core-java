/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * MemoryRail E2E system tests (real LLM + Embedding API).
 * <p>
 * Mirrors Python's {@code test_memory_rail_e2e} in
 * {@code tests.system_tests.harness.test_memory_rail_e2e}.
 */
@Tag("system-test")
class MemoryRailE2ETest {

    static final String LLM_API_BASE = System.getenv().getOrDefault("API_BASE", "your_llm_api_url");
    static final String LLM_API_KEY = System.getenv().getOrDefault("API_KEY", "your_llm_api_key");
    static final String LLM_MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "your_llm_model_name");
    static final String LLM_MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");
    static final int LLM_TIMEOUT = Integer.parseInt(System.getenv().getOrDefault("LLM_TIMEOUT", "60"));

    static final String EMBED_API_KEY = System.getenv().getOrDefault("EMBED_API_KEY", "your_embed_api_key");
    static final String EMBED_API_BASE = System.getenv().getOrDefault("EMBED_API_BASE", "your_embed_api_url");
    static final String EMBED_MODEL_NAME = System.getenv().getOrDefault("EMBED_MODEL_NAME", "text-embedding-v3");

    private Path tmpDir;
    private String workDir;
    private String sysOperationId;

    static Model createLlmModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(LLM_MODEL_PROVIDER)
                .apiKey(LLM_API_KEY)
                .apiBase(LLM_API_BASE)
                .timeout(LLM_TIMEOUT)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(LLM_MODEL_NAME)
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    static EmbeddingConfig createEmbeddingConfig() {
        return new EmbeddingConfig(EMBED_MODEL_NAME, EMBED_API_BASE, EMBED_API_KEY);
    }

    static void requireApiConfig() {
        List<String> missing = new ArrayList<>();
        if (LLM_API_KEY == null || LLM_API_KEY.isEmpty() || LLM_API_KEY.equals("your_llm_api_key")) {
            missing.add("LLM_API_KEY");
        }
        if (LLM_API_BASE == null || LLM_API_BASE.isEmpty() || LLM_API_BASE.equals("your_llm_api_url")) {
            missing.add("LLM_API_BASE");
        }
        if (EMBED_API_KEY == null || EMBED_API_KEY.isEmpty() || EMBED_API_KEY.equals("your_embed_api_key")) {
            missing.add("EMBED_API_KEY");
        }
        if (!missing.isEmpty()) {
            assumeTrue(false, "MemoryRail E2E requires " + String.join(", ", missing) + " in environment.");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("LLM_SSL_VERIFY", "false");
        System.setProperty("IS_SENSITIVE", "false");
        Runner.start();
        tmpDir = Files.createTempDirectory("memory_rail_e2e_");
        workDir = new Workspace().getRootPath();
        sysOperationId = "memory_rail_sysop_" + UUID.randomUUID().toString().replace("-", "");
        SysOperationCard card = new SysOperationCard();
        card.setId(sysOperationId);
        card.setMode(OperationMode.LOCAL);
        LocalWorkConfig workConfig = new LocalWorkConfig();
        workConfig.setWorkDir(workDir);
        card.setWorkConfig(workConfig);
        var addResult = Runner.resourceMgr().addSysOperation(card, null);
        if (addResult.isError()) {
            throw new RuntimeException("add_sys_operation failed: " + addResult.getError());
        }
        Path memoryDir = Path.of(workDir).resolve("memory");
        Files.createDirectories(memoryDir);
    }

    @AfterEach
    void tearDown() {
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId, null, null, false);
        } finally {
            Runner.stop();
        }
    }

    @Test
    @Disabled("need llm and embedding")
    void test01MemoryRailBasicInvoke() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test02WriteMemoryTool() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test03MemorySearchTool() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test04ReadMemoryTool() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test05EditMemoryTool() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test06MemoryGetTool() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test07WriteMemoryAppendMode() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test08UpdateUserProfile() {
        requireApiConfig();
    }

    @Test
    @Disabled("need llm and embedding")
    void test09WriteMemoryMdFile() {
        requireApiConfig();
    }
}
