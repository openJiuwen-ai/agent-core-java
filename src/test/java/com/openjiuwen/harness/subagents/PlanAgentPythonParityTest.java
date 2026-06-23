/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/test_plan_agent.py}.
 */
class PlanAgentPythonParityTest {

    @TempDir
    private Path workspace;

    @Test
    void planAgentDescHasCnAndEn() {
        assertThat(PlanAgent.PLAN_AGENT_DESC).containsKeys("cn", "en");
        assertThat(PlanAgent.PLAN_AGENT_DESC.get("cn")).isNotBlank();
        assertThat(PlanAgent.PLAN_AGENT_DESC.get("en")).isNotBlank();
    }

    @Test
    void systemPromptCnIsNonEmpty() {
        assertThat(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_CN).isNotBlank();
    }

    @Test
    void systemPromptEnIsNonEmpty() {
        assertThat(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN).isNotBlank();
    }

    @Test
    void defaultSystemPromptDictMatchesIndividualConstants() {
        assertThat(PlanAgent.DEFAULT_PLAN_AGENT_SYSTEM_PROMPT)
                .containsEntry("cn", PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_CN)
                .containsEntry("en", PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN);
    }

    @Test
    void systemPromptsIncludeReadOnlyConstraint() {
        assertThat(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_CN).contains("只读");
        assertThat(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN.toLowerCase()).contains("read");
    }

    @Test
    void enPromptEndsWithCriticalFilesSection() {
        assertThat(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN).contains("Critical Files for Implementation");
    }

    @Test
    void cnPromptEndsWithCriticalFilesSection() {
        assertThat(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_CN).contains("Critical Files for Implementation");
    }

    @Test
    void defaultsEn() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "en", false, 25);

        assertThat(spec.getCard().getName()).isEqualTo("plan_agent");
        assertThat(spec.getCard().getDescription()).isEqualTo(PlanAgent.PLAN_AGENT_DESC.get("en"));
        assertThat(spec.getSystemPrompt()).isEqualTo(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN);
        assertThat(spec.getRails()).hasSize(1).first().isInstanceOf(SysOperationRail.class);
        assertThat(spec.isEnableTaskLoop()).isFalse();
        assertThat(spec.getMaxIterations()).isEqualTo(25);
    }

    @Test
    void defaultsCn() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "cn", false, 25);

        assertThat(spec.getCard().getName()).isEqualTo("plan_agent");
        assertThat(spec.getCard().getDescription()).isEqualTo(PlanAgent.PLAN_AGENT_DESC.get("cn"));
        assertThat(spec.getSystemPrompt()).isEqualTo(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_CN);
    }

    @Test
    void customCardOverridesDefault() {
        AgentCard customCard = new AgentCard("my_plan", "my_plan", "custom planner");

        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), customCard, null, null, null, null, "en", false, 25);

        assertThat(spec.getCard().getName()).isEqualTo("my_plan");
        assertThat(spec.getCard().getDescription()).isEqualTo("custom planner");
    }

    @Test
    void customSystemPromptOverridesDefault() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, "Custom planning prompt.", null, null, null, "en", false, 25);

        assertThat(spec.getSystemPrompt()).isEqualTo("Custom planning prompt.");
    }

    @Test
    void customRailsReplaceDefault() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, List.of(), "en", false, 25);

        assertThat(spec.getRails()).isEmpty();
    }

    @Test
    void nullRailsUsesSysOperationRail() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "en", false, 25);

        assertThat(spec.getRails()).hasSize(1).first().isInstanceOf(SysOperationRail.class);
    }

    @Test
    void enableTaskLoopPropagates() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "en", true, 25);

        assertThat(spec.isEnableTaskLoop()).isTrue();
        assertThat(spec.getConfig().isEnableTaskLoop()).isTrue();
    }

    @Test
    void maxIterationsPropagates() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "en", false, 10);

        assertThat(spec.getMaxIterations()).isEqualTo(10);
        assertThat(spec.getMetadata()).containsEntry("max_iterations", 10);
    }

    @Test
    void modelPropagates() {
        Object model = dummyModel();

        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                model, null, null, null, null, null, "en", false, 25);

        assertThat(spec.getModel()).isSameAs(model);
        assertThat(spec.getConfig().getModel()).isSameAs(model);
    }

    @Test
    void toolsPropagate() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, List.of(), null, null, "en", false, 25);

        assertThat(spec.getTools()).isEmpty();
        assertThat(spec.getConfig().getTools()).isEmpty();
    }

    @Test
    void mcpsPropagate() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, List.of(), null, "en", false, 25);

        assertThat(spec.getMcps()).isEmpty();
        assertThat(spec.getMetadata()).containsEntry("mcps", List.of());
    }

    @Test
    void unknownLanguageFallsBackToCn() {
        DeepAgentConfig.SubAgentConfig spec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "fr", false, 25);

        assertThat(spec.getCard().getDescription()).isEqualTo(PlanAgent.PLAN_AGENT_DESC.get("cn"));
    }

    @Test
    void createPlanAgentReturnsDeepAgentWithDefaults() {
        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), null, null, null, null, null, workspace.toString(), "en", false, 25);

        assertThat(agent).isInstanceOf(DeepAgent.class);
        assertThat(agent.getCard().getName()).isEqualTo("plan_agent");
    }

    @Test
    void createPlanAgentRespectsCustomCard() {
        AgentCard customCard = new AgentCard("custom_planner", "custom_planner", "desc");

        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), customCard, null, null, null, null, workspace.toString(), "en", false, 25);

        assertThat(agent.getCard().getName()).isEqualTo("custom_planner");
    }

    @Test
    void createPlanAgentRespectsCustomSystemPrompt() {
        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), null, "my custom prompt", null, null, null, workspace.toString(), "en", false, 25);

        assertThat(agent.deepConfig().getSystemPrompt()).isEqualTo("my custom prompt");
    }

    @Test
    void sysOperationRailAttachedByDefault() {
        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), null, null, null, null, null, workspace.toString(), "en", false, 25);

        assertThat(agent.findRailsByType(SysOperationRail.class)).hasSize(1);
    }

    @Test
    void customEmptyRailsRemovesSysOperationRail() {
        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), null, null, null, null, List.of(), workspace.toString(), "en", false, 25);

        assertThat(agent.findRailsByType(SysOperationRail.class)).isEmpty();
    }

    @Test
    void languageCnSetsCnPrompt() {
        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), null, null, null, null, null, workspace.toString(), "cn", false, 25);

        assertThat(agent.deepConfig().getSystemPrompt()).isEqualTo(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_CN);
    }

    @Test
    void languageEnSetsEnPrompt() {
        DeepAgent agent = PlanAgent.createPlanAgent(
                dummyModel(), null, null, null, null, null, workspace.toString(), "en", false, 25);

        assertThat(agent.deepConfig().getSystemPrompt()).isEqualTo(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN);
    }

    @Test
    void subagentInitializesSysOperationRail() {
        DeepAgentConfig.SubAgentConfig planSpec = PlanAgent.buildPlanAgentConfig(
                dummyModel(), null, null, null, null, null, "en", false, 25);
        DeepAgentConfig parentConfig = new DeepAgentConfig();
        parentConfig.setSubagents(Map.of("plan_agent", planSpec));
        DeepAgent parentAgent = new DeepAgent(new AgentCard("parent", "parent", "test"));
        parentAgent.configure(parentConfig);

        DeepAgent subagent = parentAgent.createSubagent("plan_agent", "sub_session_id");

        assertThat(subagent.getCard().getName()).isEqualTo("plan_agent");
        assertThat(subagent.findRailsByType(SysOperationRail.class)).hasSize(1);
    }

    private static Object dummyModel() {
        return new Object();
    }
}
