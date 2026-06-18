/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.AgentRail;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's factory helpers in
 * {@code openjiuwen/auto_harness/agents/factory.py}.
 */
class AutoHarnessAgentFactoryTest {

    @Test
    void trustedLocalSysOperationUsesPermissiveLocalConfig() {
        SysOperation sysOperation = AutoHarnessAgentFactory.buildTrustedLocalSysOperation("agent-a");

        assertEquals("agent-a_trusted_local", sysOperation.getId());
        assertEquals(OperationMode.LOCAL, sysOperation.getMode());
        LocalWorkConfig config = assertInstanceOf(LocalWorkConfig.class, sysOperation.getRunConfig());
        assertFalse(config.isRestrictToSandbox());
        assertEquals(null, config.getShellAllowlist());
    }

    @Test
    void createAutoHarnessAgentCombinesRailsSubagentsAndConfig() {
        AutoHarnessConfig config = config();

        DeepAgent agent = AutoHarnessAgentFactory.createAutoHarnessAgent(config);
        DeepAgentConfig deepConfig = agent.deepConfig();

        assertEquals("auto-harness", agent.getCard().getName());
        assertEquals("workspace-a", deepConfig.getWorkspace());
        assertTrue(deepConfig.isEnableTaskLoop());
        assertTrue(deepConfig.isEnablePlanMode());
        assertTrue(deepConfig.isEnableAsyncSubagent());
        assertEquals(31, deepConfig.getMaxIterations());
        assertEquals(123.0d, deepConfig.getCompletionTimeout());
        assertInstanceOf(SysOperation.class, deepConfig.getSysOperation());
        assertTrue(agent.getSubagents().containsKey("explore_agent"));
        assertTrue(agent.getSubagents().containsKey("browser_agent"));
        assertTrue(agent.getRails().stream().anyMatch(TaskPlanningRail.class::isInstance));
        assertTrue(agent.getRails().stream().anyMatch(AutoHarnessAgentFactory.AgentRailBridge.class::isInstance));
    }

    @Test
    void commitAgentDisablesTaskLoopAndPlanning() {
        AutoHarnessConfig config = config();

        DeepAgent agent = AutoHarnessAgentFactory.createCommitAgent(config, "workspace-b", List.of());

        assertEquals("workspace-b", agent.deepConfig().getWorkspace());
        assertFalse(agent.deepConfig().isEnableTaskLoop());
        assertFalse(agent.deepConfig().isEnablePlanMode());
        assertEquals(31, agent.deepConfig().getMaxIterations());
    }

    @Test
    void stageAgentsUseExpectedCardsAndIterationDefaults() {
        AutoHarnessConfig config = config();

        DeepAgent assess = AutoHarnessAgentFactory.createAssessAgent(config, List.of());
        DeepAgent plan = AutoHarnessAgentFactory.createPlanAgent(config, List.of());
        DeepAgent activate = AutoHarnessAgentFactory.createActivateGuideAgent(config, List.of());

        assertEquals("auto-harness-assess", assess.getCard().getName());
        assertEquals(12, assess.deepConfig().getMaxIterations());
        assertEquals("auto-harness-plan", plan.getCard().getName());
        assertEquals(13, plan.deepConfig().getMaxIterations());
        assertEquals("activate-guide", activate.getCard().getName());
        assertEquals(1, activate.deepConfig().getMaxIterations());
        assertTrue(activate.getRails().isEmpty());
    }

    @Test
    void bridgeRunsAgentRailCallbacks() {
        MarkerRail marker = new MarkerRail();
        AutoHarnessAgentFactory.AgentRailBridge bridge = AutoHarnessAgentFactory.bridge(marker);
        CallbackContext context = new CallbackContext(new DeepAgent(), Map.of("input", "value"));

        bridge.beforeModelCall(context);

        assertTrue(marker.called);
        assertTrue(Boolean.TRUE.equals(context.getValues().get("agent_rail_bridge")));
    }

    @Test
    void renderPromptOnlyReplacesNamedPlaceholders() {
        String rendered = AutoHarnessAgentFactory.renderPrompt(
                "a={a} json={\"b\": 1}",
                Map.of("a", "x")
        );

        assertEquals("a=x json={\"b\": 1}", rendered);
    }

    private static AutoHarnessConfig config() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setWorkspace("workspace-a");
        config.setLanguage("en");
        config.setModelTimeoutSecs(123.0d);
        config.setAgentIterations(Map.of(
                "implement", 31,
                "assess", 12,
                "plan", 13
        ));
        config.setSkillsDirs(List.of());
        config.setImmutableFiles(List.of("locked/**"));
        config.setHighImpactPrefixes(List.of("src/main/"));
        return config;
    }

    /**
     * Test-only AgentRail callback probe.
     *
     * <p>Mirrors Python's injectable {@code AgentRail} values in
     * {@code openjiuwen/auto_harness/agents/factory.py}.</p>
     */
    private static final class MarkerRail extends AgentRail {
        private boolean called;

        @Override
        public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
            called = true;
            assertNotNull(context.getInputs());
            return CompletableFuture.completedFuture(null);
        }
    }
}
