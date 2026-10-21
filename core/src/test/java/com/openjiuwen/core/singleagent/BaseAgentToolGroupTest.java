/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ExternalTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.toolgroup.SessionConcurrencyHarness;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests BaseAgent tool-group behavior.
 *
 * @since 0.1.17
 */
class BaseAgentToolGroupTest {
    @TempDir
    private Path tempDir;

    @Test
    void listEffectiveToolInfo_withoutGroups_returnsUnfilteredResult() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactlyInAnyOrder("clock", "search");
    }

    @Test
    @Tag("TC-TG-04")
    void activateAndDeactivateToolGroup_togglesVisibility() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactly("clock");

        agent.activateToolGroup("research", session);

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactlyInAnyOrder("clock", "search");

        agent.deactivateToolGroup("research", session);

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactly("clock");
    }

    @Test
    void sessionStateResidue_ignoresUnknownGroupNames() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));

        session.updateState(
                Map.of(BaseAgent.ACTIVE_TOOL_GROUP_NAMES_STATE_KEY, List.of("ghost", "research")));

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactlyInAnyOrder("clock", "search");

        agent.unregisterToolGroup("research");

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactlyInAnyOrder("clock", "search");
    }

    @Test
    @Tag("TC-LNK-03")
    void unregisteredGroupAgents_behaveIdenticallyToBaseline() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");

        agent.activateToolGroup("nonexistent", session);

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactlyInAnyOrder("clock", "search");
    }

    @Test
    void skillToolsInExplicitInactiveGroup_remainHidden() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        Tool deleteFile = skillTool("delete_file");
        registerSkill(agent, "CleanupSkill");
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("CleanupSkill")
                                .tools(List.of(deleteFile))
                                .build())
                .toCompletableFuture()
                .join();
        agent.registerToolGroup(
                new ToolGroup("danger_tools", "Dangerous tools", List.of("delete_file")));

        agent.activateSkill("CleanupSkill", session);

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .doesNotContain("delete_file");

        agent.activateToolGroup("danger_tools", session);

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .contains("delete_file", "skill_tool");
    }

    @Test
    void skillToolsWithoutExplicitGroup_visibleWhenSkillIsActive() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        Tool envLookup = skillTool("envLookup");
        registerSkill(agent, "EnvSkill");
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("EnvSkill")
                                .tools(List.of(envLookup))
                                .build())
                .toCompletableFuture()
                .join();

        agent.activateSkill("EnvSkill", session);

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .contains("envLookup", "skill_tool");
    }

    @Test
    @Tag("TC-LNK-01")
    @Tag("TC-LNK-05")
    void deactivatedSkillTool_directCallRejected() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        Tool envLookup = skillTool("envLookup");
        registerSkill(agent, "EnvSkill");
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("EnvSkill")
                                .tools(List.of(envLookup))
                                .build())
                .toCompletableFuture()
                .join();

        agent.activateSkill("EnvSkill", session);
        ToolCall activeCall =
                ToolCall.builder().id("call-1").name("envLookup").arguments("{}").build();
        List<AbilityManager.ExecutionResult> activeResults =
                agent.getAbilityManager().execute(null, activeCall, session, false);
        assertThat(activeResults.get(0).toolMessage().getContentAsString())
                .doesNotContain("not active");

        agent.deactivateSkill("EnvSkill", session);
        ToolCall deactivatedCall =
                ToolCall.builder().id("call-2").name("envLookup").arguments("{}").build();
        List<AbilityManager.ExecutionResult> deactivatedResults =
                agent.getAbilityManager().execute(null, deactivatedCall, session, false);
        assertThat(deactivatedResults.get(0).result()).isNull();
        assertThat(deactivatedResults.get(0).toolMessage()).isNotNull();
        assertThat(deactivatedResults.get(0).toolMessage().getContentAsString())
                .contains("not active");
    }

    @Test
    @Tag("TC-LNK-02")
    void skillToolRegistryListToolsByActiveSkill_preservesSemantics() {
        TestAgent agent = new TestAgent();
        Tool first = skillTool("firstTool");
        Tool second = skillTool("secondTool");
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("SkillA")
                                .tools(List.of(first))
                                .build())
                .toCompletableFuture()
                .join();
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("SkillB")
                                .tools(List.of(second))
                                .build())
                .toCompletableFuture()
                .join();

        assertThat(agent.getSkillToolRegistry().listToolsByActiveSkill(List.of("SkillA")))
                .containsOnlyKeys("SkillA");
        assertThat(agent.getSkillToolRegistry().listToolsByActiveSkill(List.of())).isEmpty();
        assertThat(agent.getSkillToolRegistry().listToolsByActiveSkill(List.of("SkillA", "SkillB")))
                .containsKeys("SkillA", "SkillB");
    }

    @Test
    @Tag("TC-PRF-01")
    void concurrentSessions_activateDifferentGroupsIndependently() throws Exception {
        TestAgent agent = new TestAgent();
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");
        registerGlobalTool(agent, "chart");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));
        agent.registerToolGroup(new ToolGroup("analysis", "Analysis tools", List.of("chart")));
        AtomicInteger verificationFailures = new AtomicInteger();

        SessionConcurrencyHarness.runInParallel(
                2,
                index -> new MemorySession("session-" + index),
                (index, session) -> {
                    if (index == 0) {
                        agent.activateToolGroup("research", session);
                        List<String> names =
                                agent.listEffectiveToolInfo(session).stream()
                                        .map(ToolInfo::getName)
                                        .sorted()
                                        .toList();
                        if (!names.equals(List.of("clock", "search"))) {
                            verificationFailures.incrementAndGet();
                        }
                    } else {
                        agent.activateToolGroup("analysis", session);
                        List<String> names =
                                agent.listEffectiveToolInfo(session).stream()
                                        .map(ToolInfo::getName)
                                        .sorted()
                                        .toList();
                        if (!names.equals(List.of("chart", "clock"))) {
                            verificationFailures.incrementAndGet();
                        }
                    }
                });

        assertThat(verificationFailures.get()).isZero();
    }

    @Test
    @Tag("TC-LNK-04")
    void effectiveToolOrder_isStableAcrossRepeatedCalls() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");
        registerGlobalTool(agent, "chart");
        registerGlobalTool(agent, "browser");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));
        agent.registerToolGroup(new ToolGroup("analysis", "Analysis tools", List.of("chart")));
        agent.activateToolGroup("research", session);
        agent.activateToolGroup("analysis", session);

        List<String> firstCall =
                agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();
        List<String> secondCall =
                agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();

        assertThat(firstCall).isEqualTo(secondCall);
        assertThat(firstCall).containsExactlyInAnyOrder("browser", "chart", "clock", "search");
    }

    @Test
    @Tag("TC-TG-01")
    @Tag("TC-LNK-04")
    @Tag("TC-SEC-01")
    void mcpGroupTools_followFilterAndExecutionRules() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        McpTestAbilityManager abilityManager = new McpTestAbilityManager();
        agent.setAbilityManager(abilityManager);
        abilityManager.add(
                McpServerConfig.builder().serverName("weather").serverId("mcp-weather").build());
        agent.registerToolGroup(
                new ToolGroup("mcp_tools", "MCP tools", List.of("mcp_weather_forecast")));

        List<String> inactiveNames =
                agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();
        assertThat(inactiveNames).doesNotContain("mcp_weather_forecast");

        agent.activateToolGroup("mcp_tools", session);
        List<String> activeNames =
                agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();
        assertThat(activeNames).contains("mcp_weather_forecast");

        agent.deactivateToolGroup("mcp_tools", session);
        ToolCall toolCall =
                ToolCall.builder()
                        .id("call-mcp")
                        .name("mcp_weather_forecast")
                        .arguments("{}")
                        .build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, toolCall, session, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(results.get(0).toolMessage()).isNotNull();
        assertThat(results.get(0).toolMessage().getContentAsString()).contains("not active");
        assertThat(results.get(0).toolMessage().getContentAsString())
                .contains("mcp_weather_forecast");
    }

    @Test
    @Tag("TC-LNK-04")
    void deactivatedGroupTools_areNotInEffectiveTools() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");
        registerGlobalTool(agent, "chart");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));
        agent.registerToolGroup(new ToolGroup("analysis", "Analysis tools", List.of("chart")));
        agent.activateToolGroup("research", session);

        List<String> effectiveNames =
                agent.listEffectiveToolInfo(session).stream().map(ToolInfo::getName).toList();

        assertThat(effectiveNames).contains("search", "clock");
        assertThat(effectiveNames).doesNotContain("chart");
    }

    @Test
    @Tag("TC-LNK-05")
    @Tag("TC-SEC-01")
    void directCallToDisabledGroupTool_returnsNotActiveWithoutExecuting() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "delete_file");
        agent.registerToolGroup(
                new ToolGroup("danger_tools", "Dangerous tools", List.of("delete_file")));

        ToolCall toolCall =
                ToolCall.builder()
                        .id("call-1")
                        .name("delete_file")
                        .arguments("{\"path\":\"/tmp/x\"}")
                        .build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, toolCall, session, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(results.get(0).toolMessage()).isNotNull();
        assertThat(results.get(0).toolMessage().getContentAsString()).contains("not active");
        assertThat(results.get(0).toolMessage().getContentAsString()).contains("delete_file");
    }

    @Test
    void directCallToActiveGroupTool_executesNormally() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "search");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));
        agent.activateToolGroup("research", session);

        ToolCall toolCall =
                ToolCall.builder()
                        .id("call-1")
                        .name("search")
                        .arguments("{\"query\":\"test\"}")
                        .build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, toolCall, session, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).toolMessage()).isNotNull();
        assertThat(results.get(0).toolMessage().getContentAsString()).doesNotContain("not active");
    }

    @Test
    void directCallWithNullSession_executesUngroupedRejectsGrouped() {
        TestAgent agent = new TestAgent();
        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "delete_file");
        agent.registerToolGroup(
                new ToolGroup("danger_tools", "Dangerous tools", List.of("delete_file")));

        ToolCall clockCall =
                ToolCall.builder().id("call-clock").name("clock").arguments("{}").build();
        List<AbilityManager.ExecutionResult> clockResults =
                agent.getAbilityManager().execute(clockCall);
        assertThat(clockResults).hasSize(1);
        assertThat(clockResults.get(0).toolMessage()).isNotNull();
        assertThat(clockResults.get(0).toolMessage().getContentAsString())
                .doesNotContain("not active");

        ToolCall dangerCall =
                ToolCall.builder()
                        .id("call-danger")
                        .name("delete_file")
                        .arguments("{\"path\":\"/tmp/x\"}")
                        .build();
        List<AbilityManager.ExecutionResult> dangerResults =
                agent.getAbilityManager().execute(dangerCall);
        assertThat(dangerResults).hasSize(1);
        assertThat(dangerResults.get(0).result()).isNull();
        assertThat(dangerResults.get(0).toolMessage()).isNotNull();
        assertThat(dangerResults.get(0).toolMessage().getContentAsString()).contains("not active");
    }

    @Test
    @Tag("TC-SEC-01")
    void directExternalGroupedToolWithNullSession_returnsNotActive() {
        TestAgent agent = new TestAgent();
        registerExternalTool(agent, "external_input");
        agent.registerToolGroup(
                new ToolGroup("external_tools", "External tools", List.of("external_input")));

        ToolCall toolCall =
                ToolCall.builder()
                        .id("call-external")
                        .name("external_input")
                        .arguments("{\"value\":\"secret\"}")
                        .build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, toolCall, null, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(results.get(0).toolMessage()).isNotNull();
        assertThat(results.get(0).toolMessage().getContentAsString())
                .contains("external_input")
                .contains("not active");
    }

    @Test
    @Tag("TC-LNK-03")
    void directExternalUngroupedToolWithNullSession_keepsLegacyBehavior() {
        TestAgent agent = new TestAgent();
        registerExternalTool(agent, "external_input");

        ToolCall toolCall =
                ToolCall.builder()
                        .id("call-external")
                        .name("external_input")
                        .arguments("{\"value\":\"value\"}")
                        .build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, toolCall, null, false);

        assertThat(results).isEmpty();
    }

    @Test
    void noGroupsRegistered_allToolCallsPassThrough() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerGlobalTool(agent, "clock");

        ToolCall toolCall = ToolCall.builder().id("call-1").name("clock").arguments("{}").build();
        List<AbilityManager.ExecutionResult> results = agent.getAbilityManager().execute(toolCall);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).toolMessage()).isNotNull();
        assertThat(results.get(0).toolMessage().getContentAsString()).doesNotContain("not active");
    }

    private static void registerGlobalTool(TestAgent agent, String name) {
        LocalFunction tool =
                new LocalFunction(
                        ToolCard.builder()
                                .id(name)
                                .name(name)
                                .description(name)
                                .inputParams(
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.of(),
                                                "required",
                                                List.of()))
                                .build(),
                        inputs -> Map.of());
        agent.getAbilityManager().addAbility(tool.getCard(), tool);
    }

    private static void registerExternalTool(TestAgent agent, String name) {
        agent.getAbilityManager()
                .add(
                        new ExternalTool(
                                ToolCard.builder()
                                        .id("external." + name)
                                        .name(name)
                                        .description("External tool " + name)
                                        .inputParams(
                                                Map.of("type", "object", "properties", Map.of()))
                                        .build()));
    }

    private static Tool skillTool(String name) {
        return new Tool(
                ToolCard.builder()
                        .id(name)
                        .name(name)
                        .description("tool " + name)
                        .inputParams(Map.of("type", "object"))
                        .build()) {};
    }

    private static final class McpTestAbilityManager extends AbilityManager {
        @Override
        protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
            return List.of(
                    ToolInfo.builder()
                            .name("forecast")
                            .description("Weather forecast")
                            .parameters(Map.of())
                            .build());
        }
    }

    private void registerSkill(TestAgent agent, String skillName) {
        String sysOperationId = "tool-group-test-" + UUID.randomUUID();
        Path skillDir = tempDir.resolve(skillName);
        try {
            Files.createDirectories(skillDir);
            Files.writeString(
                    skillDir.resolve("SKILL.md"),
                    """
                    ---
                    name: %s
                    description: Test skill.
                    ---
                    Skill body.
                    """
                            .formatted(skillName));
            Runner.resourceMgr()
                    .addSysOperation(
                            new SysOperationCard(
                                    sysOperationId,
                                    OperationMode.LOCAL,
                                    LocalWorkConfig.builder()
                                            .sandboxRoot(List.of(tempDir.toString()))
                                            .build()));
            agent.configure(new TestAgentConfig(sysOperationId));
            agent.registerSkill(skillDir.toString()).toCompletableFuture().join();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to create skill fixture", exception);
        }
    }

    private record TestAgentConfig(String sysOperationId) {
        public String getSysOperationId() {
            return sysOperationId;
        }
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent() {
            super(new AgentCard("agent", "agent", "test"));
        }

        @Override
        public BaseAgent configure(Object config) {
            setConfig(config);
            return this;
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Iterator<Object> stream(
                Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class MemorySession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {}

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
