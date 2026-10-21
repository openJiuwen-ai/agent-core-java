/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.skills.SkillSource;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.skills.repository.ClasspathSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.FileSystemSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.WorkspaceSkillRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** End-to-end integration test for Issue #196: ToolGroup + Skill integration. */
@Tag("issue196-w10")
class Issue196IntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    @Tag("TC-LNK-01")
    @Tag("TC-LNK-02")
    @Tag("TC-LNK-03")
    @Tag("TC-LNK-04")
    @Tag("TC-LNK-05")
    @Tag("TC-SEC-01")
    @Tag("TC-PRF-05")
    void endToEnd_registerActivateFilterRejectSkill() throws Exception {
        IntegrationAgent agent = new IntegrationAgent();
        MemorySession session = new MemorySession("e2e-session");

        registerGlobalTool(agent, "clock");
        registerGlobalTool(agent, "search");
        registerGlobalTool(agent, "chart");
        registerGlobalTool(agent, "delete_file");
        agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("search")));
        agent.registerToolGroup(new ToolGroup("analysis", "Analysis tools", List.of("chart")));
        agent.registerToolGroup(
                new ToolGroup("danger_tools", "Dangerous tools", List.of("delete_file")));

        Tool skillTool = skillTool("envLookup");
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("EnvSkill")
                                .tools(List.of(skillTool))
                                .build())
                .toCompletableFuture()
                .join();

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .contains("clock")
                .contains("skill_tool");

        agent.activateToolGroup("research", session);
        session.updateState(Map.of(BaseAgent.ACTIVE_SKILL_NAMES_STATE_KEY, List.of("EnvSkill")));

        List<String> visible =
                agent.listEffectiveToolInfo(session).stream()
                        .map(ToolInfo::getName)
                        .sorted()
                        .toList();
        assertThat(visible).contains("clock", "envLookup", "search", "skill_tool");

        ToolCall searchCall = ToolCall.builder().id("c1").name("search").arguments("{}").build();
        List<AbilityManager.ExecutionResult> searchResults =
                agent.getAbilityManager().execute(null, searchCall, session, false);
        assertThat(searchResults.get(0).toolMessage().getContentAsString())
                .doesNotContain("not active");

        ToolCall dangerCall =
                ToolCall.builder().id("c2").name("delete_file").arguments("{}").build();
        List<AbilityManager.ExecutionResult> dangerResults =
                agent.getAbilityManager().execute(null, dangerCall, session, false);
        assertThat(dangerResults.get(0).result()).isNull();
        assertThat(dangerResults.get(0).toolMessage().getContentAsString()).contains("not active");

        session.updateState(Map.of(BaseAgent.ACTIVE_SKILL_NAMES_STATE_KEY, List.of()));
        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .doesNotContain("envLookup");

        ToolCall skillCall = ToolCall.builder().id("c3").name("envLookup").arguments("{}").build();
        List<AbilityManager.ExecutionResult> skillResults =
                agent.getAbilityManager().execute(null, skillCall, session, false);
        assertThat(skillResults.get(0).toolMessage().getContentAsString()).contains("not active");

        agent.deactivateToolGroup("research", session);
        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .doesNotContain("search");
    }

    @Test
    void noGroupsRegistered_allToolsPassThrough() {
        IntegrationAgent agent = new IntegrationAgent();
        MemorySession session = new MemorySession("bare-session");
        registerGlobalTool(agent, "clock");

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .containsExactly("clock");
    }

    @Test
    @Tag("TC-TG-01")
    @Tag("TC-SEC-01")
    void mcpGroupTools_followFilterAndExecutionRules() {
        IntegrationAgent agent = new IntegrationAgent();
        MemorySession session = new MemorySession("mcp-session");
        MockMcpAbilityManager abilityManager = new MockMcpAbilityManager();
        agent.setAbilityManager(abilityManager);
        abilityManager.add(
                McpServerConfig.builder().serverName("weather").serverId("mcp-weather").build());
        agent.registerToolGroup(
                new ToolGroup("mcp_tools", "MCP tools", List.of("mcp_weather_forecast")));

        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .doesNotContain("mcp_weather_forecast");

        agent.activateToolGroup("mcp_tools", session);
        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .contains("mcp_weather_forecast");

        agent.deactivateToolGroup("mcp_tools", session);
        ToolCall call =
                ToolCall.builder()
                        .id("mcp-call")
                        .name("mcp_weather_forecast")
                        .arguments("{}")
                        .build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, call, session, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(results.get(0).toolMessage().getContentAsString())
                .contains("not active")
                .contains("mcp_weather_forecast");
    }

    @Test
    @Tag("TC-SKL-01")
    @Tag("TC-SKL-02")
    @Tag("TC-SEC-04")
    void threeLevelSkills_useWorkspaceFallbackAndProtectClasspath() throws Exception {
        Path filesystemRoot = tempDir.resolve("filesystem");
        Path workspaceRoot = tempDir.resolve("workspace");
        com.openjiuwen.core.singleagent.skills.SkillFixtureFactory.writeSkill(
                filesystemRoot, "shared_skill", "Filesystem Skill.", "Filesystem body.", false);
        com.openjiuwen.core.singleagent.skills.SkillFixtureFactory.writeSkill(
                workspaceRoot, "shared_skill", "Workspace Skill.", "Workspace body.", false);
        com.openjiuwen.core.singleagent.skills.SkillFixtureFactory.writeSkill(
                workspaceRoot,
                "protected_builtin_demo",
                "Workspace override.",
                "Workspace override body.",
                false);

        IntegrationAgent agent = new IntegrationAgent();
        ReActAgentConfig config = new ReActAgentConfig();
        config.setSysOperationId("e2e-sys-operation");
        agent.configure(config);
        agent.getSkillUtil()
                .getSkillManager()
                .setRepositories(
                        List.of(
                                new ClasspathSkillRepository("issue196/skills"),
                                new FileSystemSkillRepository(filesystemRoot),
                                new WorkspaceSkillRepository(workspaceRoot, "user1")));
        agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());

        var shared = agent.getSkillUtil().getSkillManager().get("shared_skill");
        assertThat(shared.getSource()).isEqualTo(SkillSource.WORKSPACE);
        var protectedSkill = agent.getSkillUtil().getSkillManager().get("protected_builtin_demo");
        assertThat(protectedSkill.getSource()).isEqualTo(SkillSource.CLASSPATH);
        assertThat(protectedSkill.isProtectedSkill()).isTrue();

        String prompt = agent.getSkillUtil().get_skill_prompt();
        assertThat(prompt)
                .contains("Source: workspace")
                .contains("Source: classpath")
                .contains("Loading: on-demand via skill_tool")
                .doesNotContain("Workspace body.")
                .doesNotContain("Filesystem body.")
                .doesNotContain("Protected Builtin Demo Skill");

        agent.getSkillUtil()
                .getSkillManager()
                .setRepositories(
                        List.of(
                                new ClasspathSkillRepository("issue196/skills"),
                                new FileSystemSkillRepository(filesystemRoot)));
        agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());
        shared = agent.getSkillUtil().getSkillManager().get("shared_skill");
        assertThat(shared.getSource()).isEqualTo(SkillSource.FILESYSTEM);
        assertThat(agent.getSkillUtil().getSkillManager().has("protected_builtin_demo")).isTrue();
    }

    private static void registerGlobalTool(IntegrationAgent agent, String name) {
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

    private static Tool skillTool(String name) {
        return new Tool(
                ToolCard.builder()
                        .id(name)
                        .name(name)
                        .description("tool " + name)
                        .inputParams(Map.of("type", "object"))
                        .build()) {};
    }

    private static final class IntegrationAgent extends BaseAgent {
        private IntegrationAgent() {
            super(new com.openjiuwen.core.singleagent.schema.AgentCard("e2e", "e2e", "test"));
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
                Object inputs,
                AgentSessionApi session,
                List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class MockMcpAbilityManager extends AbilityManager {
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
