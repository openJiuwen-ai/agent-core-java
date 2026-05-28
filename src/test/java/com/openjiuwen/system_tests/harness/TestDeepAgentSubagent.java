/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System test for DeepAgent subagent functionality.
 * <p>
 * Mirrors Python's {@code test_deep_agent_subagent.py} in
 * {@code tests.system_tests.harness.test_deep_agent_subagent}.
 *
 * <p>Tests subagent delegation and result aggregation scenarios:
 * <ul>
 *   <li>DeepAgent using subagents to execute background tasks</li>
 *   <li>Main agent reading subagent results from shared workspace</li>
 *   <li>Auto-invoke on spawn completion scenarios</li>
 *   <li>Async spawn query blocking tests</li>
 * </ul>
 *
 * <p><b>NOTE:</b> This is a system test. Full implementation requires:
 * <ul>
 *   <li>Runner infrastructure initialization</li>
 *   <li>DeepAgent configuration with subagents</li>
 *   <li>Real LLM API access for subagent delegation testing</li>
 * </ul>
 */
@Disabled("Requires full system infrastructure and LLM API access")
@Tag("system-test")
class TestDeepAgentSubagent {

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
        tmpDir = Files.createTempDirectory("deepagent_subagent_");
        workDir = tmpDir.toString();
        sysOperationId = "subagent_sysop_" + UUID.randomUUID().toString().replace("-", "");
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

    /**
     * ToolTraceRail - Records tool call sequence for verification.
     * <p>
     * Mirrors Python's {@code ToolTraceRail} in {@code tests.system_tests.harness.test_deep_agent_e2e}.
     */
    private static class ToolTraceRail extends AgentRail {
        private final List<String> toolCalls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void afterToolCall(com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx) {
            if (ctx.getInputs() instanceof com.openjiuwen.core.singleagent.rail.ToolCallInputs inputs
                    && inputs.getToolCall() != null) {
                toolCalls.add(inputs.getToolCall().getName());
            }
        }

        public List<String> getToolCalls() {
            return new ArrayList<>(toolCalls);
        }

        public Map<String, Integer> getToolCounts() {
            Map<String, Integer> counts = new HashMap<>();
            for (String tool : toolCalls) {
                counts.merge(tool, 1, Integer::sum);
            }
            return counts;
        }
    }

    /**
     * Test: DeepAgent tasks using subagents.
     * <p>
     * Mirrors Python's {@code test_deep_agent_tasks_using_subagents}.
     *
     * <p>Scenario: Multi-step complex task - call subagent for research,
     * main agent reads and summarizes results.
     *
     * <p>Verification:
     * <ul>
     *   <li>Main agent can call subagent via task_tool</li>
     *   <li>Main agent and subagent share workspace</li>
     *   <li>Main agent can use files created by subagent</li>
     * </ul>
     */
    @Test
    @Tag("level0")
    @DisplayName("test deep agent tasks using subagents - requires infrastructure")
    void testDeepAgentTasksUsingSubagents() throws Exception {
        requireLlmConfig();
        // TODO: SubAgentConfig equivalent not yet available in Java
        // Python uses SubAgentConfig(agent_card=AgentCard(...), rails=[...], system_prompt=...)
        // Java DeepAgentConfig.subagents uses List<DeepAgent> directly

        Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
        assertNotNull(sysOper, "SysOperation should be initialized");

        ToolTraceRail toolTrace = new ToolTraceRail();

        Model model = createModel();

        // Create research subagent configuration
        AgentCard researchCard = new AgentCard();
        researchCard.setName("research_agent");
        researchCard.setDescription("专注于研究调查任务，当用户想要调查某问题时，可使用该代理执行研究工作。");

        DeepAgentConfig researchConfig = new DeepAgentConfig();
        researchConfig.setCard(researchCard);
        researchConfig.setSystemPrompt("你是一名研究助理，负责针对用户输入的主题开展研究工作。");
        // TODO: Add rails to subagent config when supported

        DeepAgent researchAgent = HarnessFactory.createDeepAgent(researchConfig);

        // Create main agent with subagent
        AgentCard mainCard = new AgentCard();
        mainCard.setName("deep_agent");
        mainCard.setDescription("Main task execution agent");

        DeepAgentConfig mainConfig = new DeepAgentConfig();
        mainConfig.setCard(mainCard);
        mainConfig.setSystemPrompt(
                "你是一个严谨的任务执行助手。" +
                "当用户要求用工具处理文件时，必须调用工具，不要凭空假设。"
        );
        mainConfig.setMaxIterations(12);
        mainConfig.getSubagents().add(researchAgent);
        // TODO: Rails support: mainConfig.getRails().add(toolTrace);
        mainConfig.setSysOperationId(sysOperationId);

        DeepAgent agent = HarnessFactory.createDeepAgent(mainConfig);

        String query = 
                "请严格按顺序执行以下任务，并且每一步都必须调用工具：\n" +
                "1. 调查随机森林算法应用场景，创建summary_research.txt文件，写入内容为调查结果；\n" +
                "2. 使用工具读取 summary_research.txt 文件；\n" +
                "3. 返回文件的结果";

        // TODO: Runner.runAgent async API not yet available
        // Python: result = await Runner.run_agent(agent, {"query": query})
        // When available:
        // Map<String, Object> input = new HashMap<>();
        // input.put("query", query);
        // var result = Runner.runAgent(agent, input).get();

        // Placeholder assertion until async API available
        assertNotNull(agent);
        assertNotNull(workDir);

        // TODO: Verify tool counts when async API available:
        // Map<String, Integer> toolCounts = toolTrace.getToolCounts();
        // assertTrue(toolCounts.getOrDefault("task_tool", 0) >= 1);
        // assertTrue(toolCounts.getOrDefault("read_file", 0) >= 1);

        // TODO: Verify file created when async API available:
        // Path summaryPath = Path.of(workDir).resolve("summary_research.txt");
        // assertTrue(summaryPath.exists());
    }

    @Nested
    @DisplayName("DeepAgent Subagent Tests - Requires Infrastructure")
    class DeepAgentSubagentTests {

        /**
         * Test: DeepAgent tasks using predefined subagents.
         * <p>
         * Mirrors Python's {@code test_deep_agent_tasks_using_predefined_subagents}.
         *
         * <p>Scenario: Multi-step task using research_agent and code_agent subagents.
         *
         * <p>Verification:
         * <ul>
         *   <li>Main agent can call subagents via task_tool</li>
         *   <li>Multiple task_tool calls for parallel subagent tasks</li>
         * </ul>
         */
        @Test
        @DisplayName("test deep agent tasks using predefined subagents - requires infrastructure")
        void testDeepAgentTasksUsingPredefinedSubagents() throws Exception {
            requireLlmConfig();

            Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
            assertNotNull(sysOper);

            ToolTraceRail toolTrace = new ToolTraceRail();
            Model model = createModel();

            // TODO: create_research_agent and create_code_agent factory methods
            // Python: research_agent = create_research_agent(model=model, sys_operation=sys_oper)
            // Python: code_agent = create_code_agent(model=model, sys_operation=sys_oper)

            // Create research subagent
            AgentCard researchCard = new AgentCard();
            researchCard.setName("research_agent");
            researchCard.setDescription("Research agent for investigation tasks");
            DeepAgentConfig researchConfig = new DeepAgentConfig();
            researchConfig.setCard(researchCard);
            researchConfig.setSystemPrompt("你是一名研究助理。");
            DeepAgent researchAgent = HarnessFactory.createDeepAgent(researchConfig);

            // Create code subagent
            AgentCard codeCard = new AgentCard();
            codeCard.setName("code_agent");
            codeCard.setDescription("Code agent for programming tasks");
            DeepAgentConfig codeConfig = new DeepAgentConfig();
            codeConfig.setCard(codeCard);
            codeConfig.setSystemPrompt("你是一名编程助理。");
            DeepAgent codeAgent = HarnessFactory.createDeepAgent(codeConfig);

            // Create main agent with subagents
            AgentCard mainCard = new AgentCard();
            mainCard.setName("deep_agent");
            DeepAgentConfig mainConfig = new DeepAgentConfig();
            mainConfig.setCard(mainCard);
            mainConfig.setSystemPrompt("你是一个严谨的任务执行助手。");
            mainConfig.setMaxIterations(12);
            mainConfig.getSubagents().add(researchAgent);
            mainConfig.getSubagents().add(codeAgent);
            mainConfig.setSysOperationId(sysOperationId);

            DeepAgent agent = HarnessFactory.createDeepAgent(mainConfig);

            String query =
                    "请严格按顺序执行以下任务，并且每一步都必须调用工具：\n" +
                    "1. 我想研究詹姆斯、科比的成就并对比；\n" +
                    "2. 创建 summary_research.txt，写入内容为上一步调查的结果；\n" +
                    "3. 使用工具读取 summary_research.txt 文件；\n" +
                    "4. 对比两个人的成就返回总结结果";

            // TODO: Runner.runAgent async API
            assertNotNull(agent);

            // TODO: Verify when async API available:
            // Map<String, Integer> toolCounts = toolTrace.getToolCounts();
            // assertTrue(toolCounts.getOrDefault("task_tool", 0) >= 2);
            // assertTrue(toolCounts.getOrDefault("write_file", 0) >= 1);
            // assertTrue(toolCounts.getOrDefault("read_file", 0) >= 1);
            // Path summaryPath = Path.of(workDir).resolve("summary_research.txt");
            // assertTrue(summaryPath.exists());
        }

        /**
         * Test: Subagent result aggregation.
         * <p>
         * Mirrors Python's tests in {@code TestDeepAgentSessionRail}.
         *
         * <p>Scenario: Verify subagent result aggregation and auto-invoke behavior.
         *
         * <p>Verification:
         * <ul>
         *   <li>Subagent spawns complete with proper status</li>
         *   <li>Main agent aggregates results from multiple subagents</li>
         *   <li>Auto-invoke triggered on spawn completion</li>
         * </ul>
         */
        @Test
        @DisplayName("test subagent result aggregation - requires infrastructure")
        void testSubagentResultAggregation() throws Exception {
            requireLlmConfig();

            Object sysOper = Runner.resourceMgr().getSysOperation(sysOperationId, null, null);
            assertNotNull(sysOper);

            Model model = createModel();

            // Create subagent for async background task
            AgentCard subAgentCard = new AgentCard();
            subAgentCard.setName("research_agent");
            subAgentCard.setDescription("专注于研究调查任务");

            DeepAgentConfig subConfig = new DeepAgentConfig();
            subConfig.setCard(subAgentCard);
            subConfig.setSystemPrompt("你是研究助理，负责围绕用户输入的主题开展调研，仅需返回最终研究结果。");

            DeepAgent subagent = HarnessFactory.createDeepAgent(subConfig);

            // Create main agent with async subagent enabled
            AgentCard mainCard = new AgentCard();
            mainCard.setName("deep_agent");

            DeepAgentConfig mainConfig = new DeepAgentConfig();
            mainConfig.setCard(mainCard);
            mainConfig.setSystemPrompt("你是一个严谨的任务执行助手。");
            mainConfig.setMaxIterations(20);
            mainConfig.getSubagents().add(subagent);
            mainConfig.setSysOperationId(sysOperationId);
            // TODO: enable_task_loop, enable_async_subagent flags in DeepAgentConfig

            DeepAgent agent = HarnessFactory.createDeepAgent(mainConfig);

            String conversationId = "auto_invoke_" + UUID.randomUUID().toString().replace("-", "");
            String query1 = "提交后台任务：分析Chipotle为什么还没有进入中国市场，不要写入文件！";

            // TODO: Runner.runAgent async API with conversation_id
            // Python: r1 = await Runner.run_agent(agent, {"query": q1, "conversation_id": cid})
            assertNotNull(agent);

            // TODO: Verify when async API available:
            // assertEquals("answer", r1.get("result_type"));
            // assertFalse(agent.isInvokeActive());
            // SessionToolkit toolkit = agent.getSessionToolkit();
            // assertNotNull(toolkit);
            // Wait for spawn completion with timeout
            // List<SpawnRow> rows = toolkit.listAll();
            // assertTrue(all rows.status in ("completed", "error"));
        }
    }
}
