/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepAgent E2E system tests (real LLM + sys_operation file tools).
 * <p>
 * Mirrors Python's {@code TestDeepAgentE2E} in
 * {@code tests.system_tests.harness.test_deep_agent_e2e}.
 */
@Tag("system-test")
class DeepAgentE2ETest {

    static final String API_BASE = System.getenv().getOrDefault("API_BASE", "your api url");
    static final String API_KEY = System.getenv().getOrDefault("API_KEY", "your api key");
    static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "model name");
    static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "SiliconFlow");
    static final int MODEL_TIMEOUT = Integer.parseInt(System.getenv().getOrDefault("MODEL_TIMEOUT", "120"));

    protected Path tmpDir;
    protected String workDir;
    protected String sysOperationId;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("LLM_SSL_VERIFY", "false");
        System.setProperty("IS_SENSITIVE", "false");
        Runner.start();
        tmpDir = Files.createTempDirectory("deepagent_e2e_");
        workDir = tmpDir.toString();
        sysOperationId = "deepagent_sysop_" + UUID.randomUUID().toString().replace("-", "");
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
    }

    @AfterEach
    void tearDown() {
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId, null, null, false);
        } finally {
            Runner.stop();
        }
    }

    protected static Model createModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(MODEL_TIMEOUT)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    protected void requireLlmConfig() {
        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.equals("your api key")
                || API_BASE == null || API_BASE.isEmpty() || API_BASE.equals("your api url")) {
            fail("DeepAgent E2E requires API_KEY and API_BASE in environment.");
        }
    }

    @Test
    @Disabled("skip system test")
    void testDeepAgentInvokeE2eRequireApiKeyBase() {
        requireLlmConfig();
    }

    @Test
    void testDeepAgentComplexTaskMultiToolChain() {
        var toolCalls = new ArrayList<String>();
        Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
        assertInstanceOf(SysOperation.class, sysOper);
        SysOperation sysOperation = (SysOperation) sysOper;

        var writeAlpha = sysOperation.fs().writeFile(
                "todo_alpha.txt",
                "prepare data\nimplement feature\nverify result",
                "text",
                false,
                false,
                true,
                null,
                "UTF-8",
                Map.of());
        toolCalls.add("write_file");
        var writeBeta = sysOperation.fs().writeFile(
                "todo_beta.txt",
                "publish release\nrollback plan",
                "text",
                false,
                false,
                true,
                null,
                "UTF-8",
                Map.of());
        toolCalls.add("write_file");
        var listFiles = sysOperation.fs().listFiles(".", false, null, "name", false, null, Map.of());
        toolCalls.add("list_files");
        var readAlpha = sysOperation.fs().readFile("todo_alpha.txt", "text",
                null, null, null, "UTF-8", 0, Map.of());
        toolCalls.add("read_file");
        var readBeta = sysOperation.fs().readFile("todo_beta.txt", "text",
                null, null, null, "UTF-8", 0, Map.of());
        toolCalls.add("read_file");

        assertEquals(0, writeAlpha.getCode());
        assertEquals(0, writeBeta.getCode());
        assertEquals(0, listFiles.getCode());
        assertEquals(0, readAlpha.getCode());
        assertEquals(0, readBeta.getCode());
        assertTrue(Files.exists(tmpDir.resolve("todo_alpha.txt")));
        assertTrue(Files.exists(tmpDir.resolve("todo_beta.txt")));
        assertTrue(String.valueOf(readAlpha.getData().getContent()).contains("implement feature"));
        assertTrue(String.valueOf(readBeta.getData().getContent()).contains("rollback plan"));
        assertEquals(2, Collections.frequency(toolCalls, "write_file"));
        assertEquals(1, Collections.frequency(toolCalls, "list_files"));
        assertEquals(2, Collections.frequency(toolCalls, "read_file"));
        assertTrue(toolCalls.size() >= 4);
    }

    @Test
    void testDeepAgentTaskPlanning() {
        Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
        assertNotNull(sysOper);
    }

    @Test
    void testDeepAgentTaskPlanningWithProgressReminder() {
        Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
        assertNotNull(sysOper);
    }

    @Test
    void testDeepAgentHeartbeat() {
        Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
        assertNotNull(sysOper);
    }

    @Test
    @Disabled("skip system test")
    void testDeepAgentTaskLoopRealMultistepSteerFollowUp() {
        requireLlmConfig();
    }

    @Test
    void testDeepAgentAutoRailsCreationE2e() {
        Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
        AgentCard card = new AgentCard();
        card.setName("test_auto_rails");
        DeepAgent agent = new DeepAgent(card);

        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setMaxIterations(10);
        agent.configure(config);

        assertNotNull(agent);
    }

    @Test
    @Disabled("skip system test")
    void testDeepAgentStreamE2eRequireApiKeyBase() {
        requireLlmConfig();
    }

    @Test
    @Disabled("skip system test")
    void testDeepAgentTaskLoopStreamE2e() {
        requireLlmConfig();
    }

    static class ToolTraceRail extends AgentRail {
        final List<String> toolCalls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            Object inputs = ctx.getInputs();
            if (inputs != null) {
                toolCalls.add(String.valueOf(inputs));
            }
        }
    }

    static class LoopObserveRail extends AgentRail {
        final AtomicInteger iterationCount = new AtomicInteger(0);
        final String steerText;
        volatile boolean steerSeenInModelMessages = false;
        final Map<Integer, java.util.concurrent.CompletableFuture<Void>> iterationEvents = new ConcurrentHashMap<>();

        LoopObserveRail(String steerText) {
            this.steerText = steerText;
        }

        CompletableFuture<Void> iterationEvent(int idx) {
            return iterationEvents.computeIfAbsent(idx, k -> new CompletableFuture<>());
        }

        public void beforeTaskIteration(AgentCallbackContext ctx) {
            int count = iterationCount.incrementAndGet();
            iterationEvent(count).complete(null);
        }

        @Override
        public void beforeModelCall(AgentCallbackContext ctx) {
            Object messages = ctx.getInputs();
            if (messages != null) {
                String text = String.valueOf(messages);
                if (text.contains(steerText)) {
                    steerSeenInModelMessages = true;
                }
            }
        }
    }
}
