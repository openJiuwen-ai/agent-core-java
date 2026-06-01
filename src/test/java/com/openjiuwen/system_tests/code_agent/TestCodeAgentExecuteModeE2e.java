/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.code_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.subagents.CodeAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CodeAgent execute-mode switching end-to-end system tests.
 *
 * <p>Mirrors Python's {@code TestDeepAgentExecuteModeE2E} in
 * {@code tests/system_tests/code_agent/test_code_agent_execute_mode_e2e.py}.</p>
 */
class TestCodeAgentExecuteModeE2e {

    private static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    private static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "");
    private static final int MODEL_TIMEOUT = Integer.parseInt(System.getenv().getOrDefault("MODEL_TIMEOUT", "120"));

    @TempDir
    Path workDir;

    private AgentSessionApi session;

    @BeforeEach
    void setUp() {
        com.openjiuwen.core.runner.Runner.start();
        session = AgentSessionApi.create(
                "deepagent_plan_mode_" + UUID.randomUUID().toString().replace("-", ""),
                null,
                AgentCard.builder()
                        .id("code_agent")
                        .name("code_agent")
                        .description("code agent test session")
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        com.openjiuwen.core.runner.Runner.stop();
    }

    private static Model createRealModel() {
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

    private static void requireLlmConfig() {
        if (API_KEY.isBlank() || API_BASE.isBlank() || MODEL_NAME.isBlank() || MODEL_PROVIDER.isBlank()) {
            fail("Real-model E2E requires API_KEY/API_BASE/MODEL_NAME/MODEL_PROVIDER in environment.");
        }
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("plan mode with real model end to end")
    void testPlanModeWithRealModelEndToEnd() {
        requireLlmConfig();
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null,
                "You are an AI coding assistant. Use tools for code and planning work.",
                null, List.of(trace), null, true, 24, workDir.toString(), null, null);

        agent.switchMode(session, "plan");
        Map<?, ?> first = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Plan a small dynamic city information webpage."), session, null);
        assertEquals("answer", first.get("result_type"));

        Path planPath = agent.getPlanFilePath(session);
        assertEquals("plan", agent.loadState(session).getPlanMode().getMode());
        assertNotNull(planPath);
        assertTrue(Files.exists(planPath));
        int firstCallCount = trace.toolCalls.size();

        agent.switchMode(session, "normal");
        Map<?, ?> second = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Execute the plan."), session, null);
        assertEquals("answer", second.get("result_type"));
        List<String> secondCalls = trace.toolCalls.subList(firstCallCount, trace.toolCalls.size());
        assertFalse(secondCalls.contains("enter_plan_mode"));
        assertTrue(secondCalls.contains("todo_create"));
        assertEquals("normal", agent.loadState(session).getPlanMode().getMode());
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("plan mode modify plan then execute")
    void testPlanModeWithRealModelEndToEndModifyPlan() throws Exception {
        requireLlmConfig();
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null,
                "You are an AI coding assistant. Use tools for code and planning work.",
                null, List.of(trace), null, true, 24, workDir.toString(), null, null);

        agent.switchMode(session, "plan");
        Map<?, ?> first = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Plan a small dynamic city information webpage."), session, null);
        assertEquals("answer", first.get("result_type"));
        Path planPath = agent.getPlanFilePath(session);
        assertNotNull(planPath);
        String firstText = Files.readString(planPath);
        int secondStart = trace.toolCalls.size();

        Map<?, ?> second = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Simplify the plan."), session, null);
        assertEquals("answer", second.get("result_type"));
        assertEquals(planPath, agent.getPlanFilePath(session));
        assertNotEquals(firstText, Files.readString(planPath));

        agent.switchMode(session, "normal");
        Map<?, ?> third = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Execute the revised plan."), session, null);
        assertEquals("answer", third.get("result_type"));
        assertFalse(trace.toolCalls.subList(secondStart, trace.toolCalls.size()).contains("enter_plan_mode"));
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("plan mode steer updates final plan before execute")
    void testPlanModeWithRealModelEndToEndSteer() throws Exception {
        requireLlmConfig();
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null,
                "You are an AI coding assistant. Use tools for code and planning work.",
                null, List.of(trace), null, true, 24, workDir.toString(), null, null);

        agent.switchMode(session, "plan");
        com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Plan a city information webpage."), session, null);
        agent.steer("Only show Seattle information.", session);
        Path planPath = agent.getPlanFilePath(session);
        assertNotNull(planPath);
        assertTrue(Files.readString(planPath).contains("Seattle"));
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("new session creates distinct plan file")
    void testPlanModeNewSessionCreatesDistinctPlanFile() {
        requireLlmConfig();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null, null, null, null, null, true, 24, workDir.toString(), null, null);
        AgentSessionApi sessionA = AgentSessionApi.create(
                "deepagent_plan_mode_a_" + UUID.randomUUID().toString().replace("-", ""),
                null, AgentCard.builder().id("code_agent").name("code_agent").build());
        AgentSessionApi sessionB = AgentSessionApi.create(
                "deepagent_plan_mode_b_" + UUID.randomUUID().toString().replace("-", ""),
                null, AgentCard.builder().id("code_agent").name("code_agent").build());

        agent.switchMode(sessionA, "plan");
        com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Plan a city information webpage."), sessionA, null);
        Path planPathA = agent.getPlanFilePath(sessionA);

        agent.switchMode(sessionB, "plan");
        com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Plan a README task."), sessionB, null);
        Path planPathB = agent.getPlanFilePath(sessionB);

        assertNotNull(planPathA);
        assertNotNull(planPathB);
        assertNotEquals(planPathA, planPathB);
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("ask_user interrupt can resume in plan mode")
    void testPlanModeAskUserInterruptAndResume() {
        requireLlmConfig();
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null,
                "In plan mode, ask the user if critical details are missing.",
                null, List.of(trace), null, true, 24, workDir.toString(), null, null);

        agent.switchMode(session, "plan");
        Map<?, ?> first = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Create a city page, but the city is unspecified."), session, null);
        assertEquals("interrupt", first.get("result_type"));
        List<?> interruptIds = (List<?>) first.get("interrupt_ids");
        assertTrue(!interruptIds.isEmpty());

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(String.valueOf(interruptIds.get(0)), Map.of("answer", "Show Seattle information."));
        Map<?, ?> second = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", interactiveInput), session, null);
        assertEquals("answer", second.get("result_type"));
        assertTrue(trace.toolCalls.contains("ask_user"));
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("query switches plan to normal and executes")
    void testQuerySwitchPlanToAutoAndExecute() {
        requireLlmConfig();
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null,
                "You can switch mode according to user intent.",
                null, List.of(trace), null, true, 24, workDir.toString(), null, null);

        agent.switchMode(session, "plan");
        Map<?, ?> first = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "First make a simple development plan."), session, null);
        assertEquals("answer", first.get("result_type"));
        int firstCallCount = trace.toolCalls.size();

        Map<?, ?> second = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Execute the previous plan directly."), session, null);
        assertEquals("answer", second.get("result_type"));
        List<String> secondCalls = trace.toolCalls.subList(firstCallCount, trace.toolCalls.size());
        assertTrue(secondCalls.contains("switch_mode"));
        assertFalse(secondCalls.contains("enter_plan_mode"));
        assertTrue(secondCalls.contains("todo_create"));
    }

    @Test
    @Disabled("skip system test")
    @DisplayName("query switches normal to plan and generates plan")
    void testQuerySwitchAutoToPlanAndGeneratePlan() {
        requireLlmConfig();
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = CodeAgent.createCodeAgent(
                createRealModel(), null, null, null, List.of(trace), null, true, 24, workDir.toString(), null, null);

        agent.switchMode(session, "normal");
        Map<?, ?> first = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                agent, Map.of("query", "Give me a plan for a simple city information page."), session, null);
        if ("interrupt".equals(first.get("result_type"))) {
            List<?> interruptIds = (List<?>) first.get("interrupt_ids");
            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update(String.valueOf(interruptIds.get(0)), Map.of("answer", "Decide the simplest plan."));
            first = (Map<?, ?>) com.openjiuwen.core.runner.Runner.runAgent(
                    agent, Map.of("query", interactiveInput), session, null);
        }

        assertEquals("answer", first.get("result_type"));
        assertTrue(trace.toolCalls.contains("switch_mode"));
        assertTrue(trace.toolCalls.contains("enter_plan_mode"));
        assertEquals("plan", agent.loadState(session).getPlanMode().getMode());
        assertNotNull(agent.getPlanFilePath(session));
    }

    static final class ToolTraceRail extends AgentRail {
        final List<String> toolCalls = new ArrayList<>();

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            if (ctx.getInputs() instanceof ToolCallInputs inputs && inputs.getToolName() != null) {
                toolCalls.add(inputs.getToolName());
            }
        }
    }
}
