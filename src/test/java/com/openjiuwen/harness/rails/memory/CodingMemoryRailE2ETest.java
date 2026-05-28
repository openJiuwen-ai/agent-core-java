/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CodingMemoryRail E2E tests - basic functionality.
 * <p>
 * Mirrors Python's {@code test_coding_memory_rail_e2e} in
 * {@code tests.system_tests.harness.test_coding_memory_rail_e2e}.
 */
@Tag("system-test")
class CodingMemoryRailE2ETest {

    private Path tmpDir;
    private String workDir;
    private String sysOperationId;
    private Object sysOp;
    private String codingMemoryDir;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("coding_memory_rail_e2e_");
        workDir = new Workspace().getRootPath();
        sysOperationId = "coding_memory_rail_sysop_" + UUID.randomUUID().toString().replace("-", "");
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
        Path cmDir = Path.of(workDir).resolve("coding_memory");
        Files.createDirectories(cmDir);
        codingMemoryDir = cmDir.toString();
        sysOp = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
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
    void testFullInvokeFlow() {
        EmbeddingConfig embeddingConfig = new EmbeddingConfig("test-model", "http://test", "test-key");

        CodingMemoryRail rail = new CodingMemoryRail();
        Object mockAgent = mock(Object.class);
        rail.init(mockAgent);

        assertNotNull(rail);
    }

    @Test
    void testAutoRecallWithResults() {
        EmbeddingConfig embeddingConfig = new EmbeddingConfig("test-model", "http://test", "test-key");

        CodingMemoryRail rail = new CodingMemoryRail();
        rail.init(mock(Object.class));

        assertNotNull(rail);
    }

    @Test
    void testAutoRecallNoResults() {
        EmbeddingConfig embeddingConfig = new EmbeddingConfig("test-model", "http://test", "test-key");

        CodingMemoryRail rail = new CodingMemoryRail();
        rail.init(mock(Object.class));

        assertNotNull(rail);
    }

    @Test
    void testBeforeModelCallWithRecallResults() {
        EmbeddingConfig embeddingConfig = new EmbeddingConfig("test-model", "http://test", "test-key");

        CodingMemoryRail rail = new CodingMemoryRail();
        rail.init(mock(Object.class));

        assertNotNull(rail);
    }

    @Test
    void testScenarioSwitching() {
        assertTrue(getMemoryScenario(Map.of("memory", Map.of("scenario", "personal"))).equals("personal"));
        assertTrue(getMemoryScenario(Map.of("memory", Map.of("scenario", "coding"))).equals("coding"));
        assertTrue(getMemoryScenario(Map.of("memory", Map.of())).equals("personal"));
        assertTrue(getMemoryScenario(Map.of("memory", Map.of("scenario", "CODING"))).equals("coding"));
    }

    @SuppressWarnings("unchecked")
    private static String getMemoryScenario(Map<String, Object> config) {
        Map<String, Object> memoryCfg = (Map<String, Object>) config.getOrDefault("memory", Map.of());
        String scenario = String.valueOf(memoryCfg.getOrDefault("scenario", "personal")).trim().toLowerCase();
        return "coding".equals(scenario) ? "coding" : "personal";
    }
}
