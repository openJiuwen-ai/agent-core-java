/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import com.openjiuwen.auto_harness.rails.AutoHarnessContextRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.tools.WebTools;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's factory helpers in
 * {@code openjiuwen/auto_harness/agents/factory.py}.
 *
 * <p>Mirrors Python's agent factory unit tests in
 * {@code tests/unit_tests/auto_harness/test_agent.py}.</p>
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

    @Disabled("remote env do not support node")
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
        assertBridgedRail(agent, AutoHarnessContextRail.class);
        assertTrue(agent.getRails().stream().anyMatch(LspRail.class::isInstance));
        assertEquals(Set.of("implement", "verify", "communicate"), skillNames(agent));
        assertFalse(skillNames(agent).contains("commit"));
        assertFalse(skillNames(agent).contains("evolve"));
        assertTrue(skillRail(agent).getSkillDirs().stream()
                .map(path -> Path.of(path).getFileName().toString())
                .anyMatch("skills"::equals));
        TaskPlanningRail planningRail = singleRail(agent, TaskPlanningRail.class);
        assertTrue(planningRail.isEnableProgressRepeat());
    }

    @Disabled("remote env do not support node")
    @Test
    void commitAgentDisablesTaskLoopAndPlanning() {
        AutoHarnessConfig config = config();

        DeepAgent agent = AutoHarnessAgentFactory.createCommitAgent(config, "workspace-b", List.of());

        assertEquals("workspace-b", agent.deepConfig().getWorkspace());
        assertFalse(agent.deepConfig().isEnableTaskLoop());
        assertFalse(agent.deepConfig().isEnablePlanMode());
        assertEquals(31, agent.deepConfig().getMaxIterations());
        assertEquals(Set.of("commit", "communicate"), skillNames(agent));
        assertFalse(agent.getRails().stream().anyMatch(TaskPlanningRail.class::isInstance));
    }

    @Test
    void createAutoHarnessAgentHonorsWorkspaceOverrideForSubagents() {
        AutoHarnessConfig config = config();

        DeepAgent agent = AutoHarnessAgentFactory.createAutoHarnessAgent(
                config,
                "workspace-override",
                null,
                true,
                null,
                true,
                true,
                true,
                null,
                null
        );

        assertEquals("workspace-override", agent.deepConfig().getWorkspace());
        for (Object raw : agent.getSubagents().values()) {
            if (raw instanceof DeepAgent child) {
                assertEquals("workspace-override", child.deepConfig().getWorkspace());
            } else {
                assertThat(raw).isNotNull();
            }
        }
    }

    @Test
    void injectedToolTrackerRailIsPreservedForMainAndAssessAgents() {
        AutoHarnessConfig config = config();

        DeepAgent main = AutoHarnessAgentFactory.createAutoHarnessAgent(
                config,
                null,
                null,
                true,
                null,
                true,
                true,
                true,
                List.of(AutoHarnessAgentFactory.bridge(new ToolTrackingRail())),
                null
        );
        DeepAgent assess = AutoHarnessAgentFactory.createAssessAgent(
                config,
                List.of(AutoHarnessAgentFactory.bridge(new ToolTrackingRail()))
        );

        assertBridgedRail(main, ToolTrackingRail.class);
        assertBridgedRail(assess, ToolTrackingRail.class);
    }

    @Test
    void defaultMainAgentDoesNotIncludeToolTracker() {
        AutoHarnessConfig config = config();

        DeepAgent main = AutoHarnessAgentFactory.createAutoHarnessAgent(config);

        assertFalse(hasBridgedRail(main, ToolTrackingRail.class));
    }

    @Test
    void assessAgentDoesNotIncludeToolTrackerWithoutInjection() {
        AutoHarnessConfig config = config();

        DeepAgent assess = AutoHarnessAgentFactory.createAssessAgent(config, List.of());

        assertFalse(hasBridgedRail(assess, ToolTrackingRail.class));
    }

    @Disabled("remote env do not support node")
    @Test
    void assessAgentIncludesReadonlyRailsSubagentsAndResearchTools() {
        AutoHarnessConfig config = config();

        DeepAgent agent = AutoHarnessAgentFactory.createAssessAgent(config, List.of());

        assertBridgedRail(agent, AutoHarnessContextRail.class);
        assertTrue(agent.getRails().stream().anyMatch(LspRail.class::isInstance));
        assertTrue(agent.deepConfig().isEnableAsyncSubagent());
        assertTrue(agent.getSubagents().containsKey("explore_agent"));
        assertInstanceOf(SysOperation.class, agent.deepConfig().getSysOperation());
        assertEquals(Set.of("assess"), skillNames(agent));
        assertTrue(agent.getTools().values().stream().anyMatch(WebTools.WebFreeSearchTool.class::isInstance));
        assertTrue(agent.getTools().values().stream().anyMatch(WebTools.WebFetchWebpageTool.class::isInstance));
    }

    @Disabled("remote env do not support node")
    @Test
    void stageAgentsExposeExpectedSkillsAndPromptBehavior() {
        AutoHarnessConfig config = config();

        DeepAgent plan = AutoHarnessAgentFactory.createPlanAgent(config, List.of());
        DeepAgent select = AutoHarnessAgentFactory.createSelectPipelineAgent(config, List.of());
        DeepAgent prDraft = AutoHarnessAgentFactory.createPrDraftAgent(config, "workspace-b", List.of());
        DeepAgent learnings = AutoHarnessAgentFactory.createLearningsAgent(
                config,
                "- task-1 (success=true, reverted=false)",
                "- [insight] topic: summary",
                List.of()
        );

        assertEquals(Set.of("plan"), skillNames(plan));
        assertEquals(Set.of("select_pipeline"), skillNames(select));
        assertEquals(Set.of("communicate"), skillNames(prDraft));
        assertEquals("workspace-b", prDraft.deepConfig().getWorkspace());
        assertTrue(prDraft.deepConfig().getTools().isEmpty());
        assertEquals(Set.of("communicate"), skillNames(learnings));
        assertTrue(learnings.deepConfig().getTools().isEmpty());
        assertFalse(learnings.deepConfig().getSystemPrompt().contains("{session_results}"));
        assertFalse(learnings.deepConfig().getSystemPrompt().contains("{existing_memories}"));
        assertTrue(learnings.deepConfig().getSystemPrompt().contains("task-1"));
        assertTrue(learnings.deepConfig().getSystemPrompt().contains("topic: summary"));
    }

    @Test
    void allAutoHarnessAgentsUseConfiguredCompletionTimeout() {
        AutoHarnessConfig config = config();
        config.setModelTimeoutSecs(6000.0d);

        List<DeepAgent> agents = List.of(
                AutoHarnessAgentFactory.createAutoHarnessAgent(config),
                AutoHarnessAgentFactory.createCommitAgent(config, "workspace-b", List.of()),
                AutoHarnessAgentFactory.createAssessAgent(config, List.of()),
                AutoHarnessAgentFactory.createPlanAgent(config, List.of()),
                AutoHarnessAgentFactory.createEvalAgent(config, List.of()),
                AutoHarnessAgentFactory.createSelectPipelineAgent(config, List.of()),
                AutoHarnessAgentFactory.createDesignExtAgent(config, List.of()),
                AutoHarnessAgentFactory.createPrDraftAgent(config, "workspace-b", List.of()),
                AutoHarnessAgentFactory.createLearningsAgent(config, "results", "memories", List.of()),
                AutoHarnessAgentFactory.createActivateGuideAgent(config, List.of())
        );

        assertFalse(agents.isEmpty());
        assertTrue(agents.stream().allMatch(agent -> agent.deepConfig().getCompletionTimeout() == 6000.0d));
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
        // Stage agents always get buildSubagents() → applyAutoRailsFromConfig adds SubagentRail.
        assertFalse(activate.getRails().isEmpty());
        assertTrue(activate.getRails().stream()
                .allMatch(rail -> rail instanceof com.openjiuwen.harness.rails.subagent.SubagentRail));
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

    private static SkillUseRail skillRail(DeepAgent agent) {
        return singleRail(agent, SkillUseRail.class);
    }

    private static Set<String> skillNames(DeepAgent agent) {
        SkillUseRail rail = skillRail(agent);
        rail.reloadSkills();
        return rail.getSkillsMeta().stream()
                .map(SkillDescriptor::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static <T> T singleRail(DeepAgent agent, Class<T> railType) {
        List<T> matches = new ArrayList<>();
        for (DeepAgentRail rail : agent.getRails()) {
            if (railType.isInstance(rail)) {
                matches.add(railType.cast(rail));
            }
        }
        assertEquals(1, matches.size());
        return matches.get(0);
    }

    private static <T extends AgentRail> void assertBridgedRail(DeepAgent agent, Class<T> railType) {
        assertTrue(hasBridgedRail(agent, railType));
    }

    private static <T extends AgentRail> boolean hasBridgedRail(DeepAgent agent, Class<T> railType) {
        return agent.getRails().stream()
                .filter(AutoHarnessAgentFactory.AgentRailBridge.class::isInstance)
                .map(AutoHarnessAgentFactory.AgentRailBridge.class::cast)
                .map(AutoHarnessAgentFactory.AgentRailBridge::getDelegate)
                .anyMatch(railType::isInstance);
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
