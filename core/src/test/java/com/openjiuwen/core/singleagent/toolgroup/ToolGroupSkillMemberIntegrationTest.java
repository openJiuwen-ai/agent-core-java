/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.GroupMemberRef;
import com.openjiuwen.core.operator.toolgroup.GroupMemberType;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillFixtureFactory;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.skills.repository.FileSystemSkillRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag("issue196-w11")
class ToolGroupSkillMemberIntegrationTest {
    @TempDir
    private Path tempDir;

    @AfterEach
    void releaseGlobalResources() {
        Runner.resourceMgr().release();
    }

    @Test
    @Tag("TC-LNK-01")
    void activeGroupActivatesToolAndSkillTogether() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGitHelperCapability(agent);
        agent.registerToolGroup(new ToolGroup(
                "research",
                "Research capabilities",
                List.of(
                        new GroupMemberRef(GroupMemberType.TOOL, "bash"),
                        new GroupMemberRef(GroupMemberType.SKILL, "GitHelper"))));
        MemorySession session = new MemorySession("active-research");
        agent.activateToolGroup("research", session);

        Object result = invokeOnce(agent, session);

        assertAnswer(result);
        ModelCall modelCall = assertOnlyModelCall(agent);
        assertThat(modelCall.toolNames())
                .contains("bash", "gitHelperTool", "skill_tool");
        assertThat(modelCall.systemPrompt())
                .contains("Skill name: GitHelper")
                .contains("Skill name: SearchSkill");
    }

    @Test
    @Tag("TC-LNK-02")
    void deactivatedGroupHidesToolAndGroupedSkill() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGitHelperCapability(agent);
        agent.registerToolGroup(new ToolGroup(
                "research",
                "Research capabilities",
                List.of(
                        new GroupMemberRef(GroupMemberType.TOOL, "bash"),
                        new GroupMemberRef(GroupMemberType.SKILL, "GitHelper"))));
        MemorySession session = new MemorySession("inactive-research");

        Object result = invokeOnce(agent, session);

        assertAnswer(result);
        ModelCall modelCall = assertOnlyModelCall(agent);
        assertThat(modelCall.toolNames())
                .contains("skill_tool")
                .doesNotContain("bash", "gitHelperTool");
        assertThat(modelCall.systemPrompt())
                .contains("Skill name: SearchSkill")
                .doesNotContain("Skill name: GitHelper");
    }

    @Test
    @Tag("TC-LNK-03")
    void directSkillActivationKeepsGroupedSkillAndBoundTool() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGitHelperCapability(agent);
        agent.registerToolGroup(new ToolGroup(
                "research",
                "Research capabilities",
                List.of(
                        new GroupMemberRef(GroupMemberType.TOOL, "bash"),
                        new GroupMemberRef(GroupMemberType.SKILL, "GitHelper"))));
        MemorySession session = new MemorySession("direct-skill");
        agent.activateSkill("GitHelper", session);
        agent.deactivateToolGroup("research", session);

        Object result = invokeOnce(agent, session);

        assertAnswer(result);
        ModelCall modelCall = assertOnlyModelCall(agent);
        assertThat(modelCall.toolNames())
                .contains("gitHelperTool", "skill_tool")
                .doesNotContain("bash");
        assertThat(modelCall.systemPrompt())
                .contains("Skill name: GitHelper")
                .contains("Skill name: SearchSkill");
    }

    @Test
    @Tag("TC-LNK-04")
    void ungroupedSkillRemainsVisibleWhenOtherGroupRegistered() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGlobalTool(agent, "bash");
        registerSkillTool(agent, "SearchSkill", "searchTool");
        agent.registerToolGroup(ToolGroup.ofToolNames(
                "coding", "Coding tools", List.of("bash")));
        MemorySession session = new MemorySession("ungrouped-skill");

        Object result = invokeOnce(agent, session);

        assertAnswer(result);
        ModelCall modelCall = assertOnlyModelCall(agent);
        assertThat(modelCall.toolNames())
                .contains("skill_tool")
                .doesNotContain("searchTool")
                .doesNotContain("bash");
        assertOnlyModelCall(agent)
                .systemPrompt()
                .contains("Skill name: SearchSkill");
    }

    @Test
    @Tag("TC-LNK-05")
    void groupActivationIsSessionScoped() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGitHelperCapability(agent);
        agent.registerToolGroup(new ToolGroup(
                "research",
                "Research capabilities",
                List.of(
                        new GroupMemberRef(GroupMemberType.TOOL, "bash"),
                        new GroupMemberRef(GroupMemberType.SKILL, "GitHelper"))));
        MemorySession activeSession = new MemorySession("session-active");
        MemorySession inactiveSession = new MemorySession("session-inactive");

        Object activeResult = invokeOnce(agent, activeSession);
        List<ModelCall> callsAfterActive = agent.modelCalls();
        agent.activateToolGroup("research", activeSession);
        Object activatedResult = invokeOnce(agent, activeSession);

        Object inactiveResult = invokeOnce(agent, inactiveSession);

        assertAnswer(activeResult);
        assertAnswer(activatedResult);
        assertAnswer(inactiveResult);
        assertThat(callsAfterActive).hasSize(1);
        assertThat(callsAfterActive.get(0).toolNames())
                .contains("skill_tool")
                .doesNotContain("bash", "gitHelperTool");
        assertThat(callsAfterActive.get(0).systemPrompt())
                .contains("Skill name: SearchSkill")
                .doesNotContain("Skill name: GitHelper");
        assertThat(agent.modelCalls().get(1).toolNames())
                .contains("bash", "gitHelperTool", "skill_tool");
        assertThat(agent.modelCalls().get(1).systemPrompt())
                .contains("Skill name: GitHelper", "Skill name: SearchSkill");
        assertThat(agent.modelCalls().get(2).toolNames())
                .contains("skill_tool")
                .doesNotContain("bash", "gitHelperTool");
        assertThat(agent.modelCalls().get(2).systemPrompt())
                .contains("Skill name: SearchSkill")
                .doesNotContain("Skill name: GitHelper");
    }

    @Test
    @Tag("TC-LNK-03")
    void noExplicitGroupsPreservesLegacyPromptAndSkillTools() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGitHelperCapability(agent);
        MemorySession session = new MemorySession("legacy-session");
        agent.activateSkill("GitHelper", session);

        Object result = invokeOnce(agent, session);

        assertAnswer(result);
        ModelCall modelCall = assertOnlyModelCall(agent);
        assertThat(modelCall.toolNames())
                .contains("gitHelperTool", "skill_tool");
        assertThat(modelCall.systemPrompt())
                .contains("Skill name: GitHelper")
                .contains("Skill name: SearchSkill");
    }

    @Test
    @Tag("TC-LNK-01")
    void ofToolNamesCompatibilityFiltersToolsWithoutSkillMembers() throws Exception {
        RecordingReActAgent agent = newAgent();
        registerGlobalTool(agent, "bash");
        agent.registerToolGroup(ToolGroup.ofToolNames(
                "research", "Research tools", List.of("bash")));
        MemorySession session = new MemorySession("compatibility-session");

        Object inactiveResult = invokeOnce(agent, session);
        List<ModelCall> inactiveCalls = agent.modelCalls();
        agent.activateToolGroup("research", session);
        Object activeResult = invokeOnce(agent, session);

        assertAnswer(inactiveResult);
        assertAnswer(activeResult);
        assertThat(agent.getToolGroupManager().getActiveSkillNames(List.of("research"))).isEmpty();
        assertThat(inactiveCalls.get(0).toolNames())
                .doesNotContain("bash");
        assertThat(inactiveCalls.get(0).systemPrompt())
                .contains("Skill name: SearchSkill")
                .contains("Skill name: GitHelper");
        assertThat(agent.modelCalls().get(1).toolNames())
                .containsExactly("bash");
    }

    private static Object invokeOnce(ReActAgent agent, AgentSessionApi session) {
        return agent.invoke(
                Map.of("query", "hello", "conversation_id", session.getSessionId()), session);
    }

    private static void assertAnswer(Object result) {
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("result_type")).isEqualTo("answer");
        assertThat(((Map<?, ?>) result).get("output")).isEqualTo("done");
    }

    private static ModelCall assertOnlyModelCall(RecordingReActAgent agent) {
        assertThat(agent.modelCalls()).hasSize(1);
        return agent.modelCalls().get(0);
    }

    private RecordingReActAgent newAgent() throws Exception {
        Path skillRoot = tempDir.resolve("skills");
        SkillFixtureFactory.writeSkill(
                skillRoot, SkillFixtureFactory.SkillFixtureSource.FILESYSTEM,
                "GitHelper", "Git helper workflow.", false);
        SkillFixtureFactory.writeSkill(
                skillRoot, SkillFixtureFactory.SkillFixtureSource.FILESYSTEM,
                "SearchSkill", "Search workflow.", false);
        RecordingReActAgent agent = new RecordingReActAgent();
        ReActAgentConfig config = new ReActAgentConfig();
        config.setSysOperationId("toolgroup-skill-member-" + UUID.randomUUID());
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(1);
        agent.configure(config);
        agent.getSkillUtil().getSkillManager().setRepositories(
                List.of(new FileSystemSkillRepository(skillRoot)));
        agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());
        return agent;
    }

    private static void registerGitHelperCapability(RecordingReActAgent agent) {
        registerGlobalTool(agent, "bash");
        registerSkillTool(agent, "GitHelper", "gitHelperTool");
    }

    private static void registerSkillTool(
            RecordingReActAgent agent, String skillName, String toolName) {
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName(skillName)
                                .tools(List.of(skillTool(toolName)))
                                .build())
                .toCompletableFuture()
                .join();
    }

    private static void registerGlobalTool(ReActAgent agent, String name) {
        LocalFunction tool = new LocalFunction(
                ToolCard.builder()
                        .id(name)
                        .name(name)
                        .description(name)
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()))
                        .build(),
                inputs -> Map.of("ok", true));
        agent.getAbilityManager().addAbility(tool.getCard(), tool);
    }

    private static Tool skillTool(String name) {
        return new Tool(ToolCard.builder()
                .id(name)
                .name(name)
                .description("tool " + name)
                .inputParams(Map.of("type", "object"))
                .build()) {};
    }

    private record ModelCall(String systemPrompt, List<String> toolNames) {
    }

    private static final class RecordingReActAgent extends ReActAgent {
        private final List<ModelCall> modelCalls = new ArrayList<>();

        private RecordingReActAgent() {
            super(new AgentCard(
                    "toolgroup-skill-member-agent",
                    "toolgroup-skill-member-agent",
                    "integration test agent"));
        }

        @Override
        public Object callModel(
                AgentCallbackContext ctx, com.openjiuwen.core.context.ModelContext context,
                List<ToolInfo> tools) {
            modelCalls.add(new ModelCall(
                    getPromptBuilder().build(),
                    tools.stream().map(ToolInfo::getName).toList()));
            return new AssistantMessage("done");
        }

        private List<ModelCall> modelCalls() {
            return List.copyOf(modelCalls);
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

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
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
