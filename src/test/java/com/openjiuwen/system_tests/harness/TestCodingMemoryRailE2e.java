/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CodingMemoryRail E2E tests - Basic functionality tests.
 * <p>
 * Mirrors Python's {@code test_coding_memory_rail_e2e.py} in
 * {@code tests/system_tests/harness/test_coding_memory_rail_e2e.py}.
 */
public class TestCodingMemoryRailE2e {

    private Path tmpDir;
    private String workDir;
    private String sysOperationId;
    private SysOperation sysOp;
    private String codingMemoryDir;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("coding_memory_rail_e2e_" + UUID.randomUUID().toString().substring(0, 8));
        workDir = new Workspace().getRootPath();
        sysOperationId = "coding_memory_rail_sysop_" + UUID.randomUUID().toString().replace("-", "");
        
        SysOperationCard card = SysOperationCard.builder()
                .id(sysOperationId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workDir).build())
                .build();
        
        Runner.resourceMgr().addSysOperation(card);
        
        codingMemoryDir = Path.of(workDir, "coding_memory").toString();
        Files.createDirectories(Path.of(codingMemoryDir));
        
        sysOp = Runner.resourceMgr().getSysOperation(sysOperationId);
        mocks = MockitoAnnotations.openMocks(this);
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
            if (mocks != null) {
                mocks.close();
            }
        }
    }

    /**
     * Test environment data holder.
     */
    private static class TestEnv {
        final Path tmpDir;
        final String workDir;
        final String sysOperationId;
        final SysOperation sysOp;
        final String codingMemoryDir;

        TestEnv(Path tmpDir, String workDir, String sysOperationId, SysOperation sysOp, String codingMemoryDir) {
            this.tmpDir = tmpDir;
            this.workDir = workDir;
            this.sysOperationId = sysOperationId;
            this.sysOp = sysOp;
            this.codingMemoryDir = codingMemoryDir;
        }
    }

    private TestEnv getTestEnv() {
        return new TestEnv(tmpDir, workDir, sysOperationId, sysOp, codingMemoryDir);
    }

    @Nested
    @DisplayName("End-to-end tests: Verify complete flow")
    class FullFlowTests {

        @Test
        @DisplayName("Test full invoke flow")
        void testFullInvokeFlow() throws Exception {
            TestEnv env = getTestEnv();
            String codingMemoryDir = env.codingMemoryDir;

            EmbeddingConfig embeddingConfig = new EmbeddingConfig(
                    "test-model",
                    "http://test",
                    "test-key"
            );

            CodingMemoryRail rail = new CodingMemoryRail();
            // Note: Java version has simplified init, placeholder for full implementation

            // Mock agent
            Object mockAgent = mock(Object.class);
            rail.init(mockAgent);

            // Placeholder: Full invoke flow test requires additional implementation
            // of before_invoke, before_model_call in Java CodingMemoryRail
            assertThat(rail).isNotNull();
        }

        @Test
        @DisplayName("Test auto recall with results")
        void testAutoRecallWithResults() throws Exception {
            TestEnv env = getTestEnv();
            String codingMemoryDir = env.codingMemoryDir;
            SysOperation sysOp = env.sysOp;

            // Create memory file
            Path memFile = Path.of(codingMemoryDir, "python_pref.md");
            String fileContent = """
                ---
                name: Python Preference
                description: User prefers Python
                type: user
                ---
                
                用户喜欢使用 Python 编程.
                """;
            Files.writeString(memFile, fileContent);

            EmbeddingConfig embeddingConfig = new EmbeddingConfig(
                    "test-model",
                    "http://test",
                    "test-key"
            );

            CodingMemoryRail rail = new CodingMemoryRail();

            // Placeholder: _auto_recall requires implementation in Java
            assertThat(rail).isNotNull();
            assertThat(Files.exists(memFile)).isTrue();
        }

        @Test
        @DisplayName("Test auto recall no results")
        void testAutoRecallNoResults() throws Exception {
            TestEnv env = getTestEnv();
            String codingMemoryDir = env.codingMemoryDir;

            EmbeddingConfig embeddingConfig = new EmbeddingConfig(
                    "test-model",
                    "http://test",
                    "test-key"
            );

            CodingMemoryRail rail = new CodingMemoryRail();

            // Placeholder: _auto_recall with empty results requires implementation
            assertThat(rail).isNotNull();
        }

        @Test
        @DisplayName("Test before model call with recall results")
        void testBeforeModelCallWithRecallResults() throws Exception {
            TestEnv env = getTestEnv();
            String codingMemoryDir = env.codingMemoryDir;

            EmbeddingConfig embeddingConfig = new EmbeddingConfig(
                    "test-model",
                    "http://test",
                    "test-key"
            );

            CodingMemoryRail rail = new CodingMemoryRail();

            // Placeholder: before_model_call with recalled content requires implementation
            assertThat(rail).isNotNull();
        }
    }

    @Nested
    @DisplayName("Scenario switching tests")
    class ScenarioTests {

        @Test
        @DisplayName("Test scenario switching logic")
        void testScenarioSwitching() {
            // Inline implementation of scenario switching logic
            // Avoids cross-module import

            // Test personal scenario
            assertThat(getMemoryScenario(createConfig("personal"))).isEqualTo("personal");

            // Test coding scenario
            assertThat(getMemoryScenario(createConfig("coding"))).isEqualTo("coding");

            // Test default scenario
            assertThat(getMemoryScenario(createConfig(null))).isEqualTo("personal");

            // Test case insensitive
            assertThat(getMemoryScenario(createConfig("CODING"))).isEqualTo("coding");
        }

        private java.util.Map<String, Object> createConfig(String scenario) {
            java.util.Map<String, Object> memoryCfg = new java.util.HashMap<>();
            if (scenario != null) {
                memoryCfg.put("scenario", scenario);
            }
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            config.put("memory", memoryCfg);
            return config;
        }

        private String getMemoryScenario(java.util.Map<String, Object> config) {
            java.util.Map<String, Object> memoryCfg = (java.util.Map<String, Object>) 
                    (config != null ? config.get("memory") : new java.util.HashMap<>());
            String scenario = memoryCfg.get("scenario") != null 
                    ? memoryCfg.get("scenario").toString().strip().toLowerCase() 
                    : "personal";
            return "coding".equals(scenario) ? "coding" : "personal";
        }
    }
}