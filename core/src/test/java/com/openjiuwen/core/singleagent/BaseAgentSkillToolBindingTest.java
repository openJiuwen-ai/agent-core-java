/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

@Tag("issue196-w9")
class BaseAgentSkillToolBindingTest {
    @TempDir
    private Path tempDir;

    private ResourceMgr resourceMgr;
    private MockedStatic<Runner> runner;

    @BeforeEach
    void isolateResources() {
        resourceMgr = new ResourceMgr();
        // These tests register the shared skill_tool ID; keep real registration within this fixture.
        runner = mockStatic(Runner.class, CALLS_REAL_METHODS);
        runner.when(Runner::resourceMgr).thenReturn(resourceMgr);
    }

    @AfterEach
    void releaseResources() {
        try {
            resourceMgr.release().toCompletableFuture().join();
        } finally {
            runner.close();
        }
    }

    @Test
    void registeredSkillToolsAreInvisibleUntilSkillIsActivated() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        Tool envLookupTool = tool("envLookupTool");

        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EnvSkill")
                .tools(List.of(envLookupTool))
                .build()).toCompletableFuture().join();

        assertThat(agent.findActiveSkillTool("envLookupTool", session)).isEmpty();
        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .doesNotContain("envLookupTool");
    }

    @Test
    void registerSkillWithoutBoundToolsRegistersMetaTool() {
        TestAgent agent = new TestAgent();
        registerSkill(agent, "PromptOnlySkill");
        MemorySession session = new MemorySession("prompt-only");

        assertThat(agent.getAbilityManager().getTools()).containsKey("skill_tool");
        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .contains("skill_tool");
    }

    @Test
    void replacingAbilityManagerReRegistersMetaTool() {
        TestAgent agent = new TestAgent();
        registerSkill(agent, "ReplacedManagerSkill");

        agent.setAbilityManager(new AbilityManager(agent.getCard().getId()));

        assertThat(agent.getAbilityManager().getTools()).containsKey("skill_tool");
    }

    @Test
    void activatingSkillStoresSessionStateAndExposesSkillTools() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        Tool first = tool("firstTool");
        Tool second = tool("secondTool");
        registerSkill(agent, "EnvSkill");

        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EnvSkill")
                .tools(List.of(first))
                .build()).toCompletableFuture().join();
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EnvSkill")
                .tools(List.of(second))
                .build()).toCompletableFuture().join();
        agent.activateSkill("EnvSkill", session);

        assertThat(session.getState(BaseAgent.ACTIVE_SKILL_NAMES_STATE_KEY)).isEqualTo(List.of("EnvSkill"));
        assertThat(agent.findActiveSkillTool("secondTool", session)).containsSame(second);
        assertThat(agent.listEffectiveToolInfo(session))
                .extracting(ToolInfo::getName)
                .contains("firstTool", "secondTool", "skill_tool");
    }

    @Test
    void activatingSkillUsesSessionStateWithoutDependingOnSessionId() {
        TestAgent agent = new TestAgent();
        SessionIdFailingSession session = new SessionIdFailingSession();
        registerSkill(agent, "EnvSkill");
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EnvSkill")
                .tools(List.of(tool("envTool")))
                .build()).toCompletableFuture().join();

        agent.activateSkill("EnvSkill", session);

        assertThat(agent.getActiveSkillNames(session)).containsExactly("EnvSkill");
    }

    @Test
    void activatingSkillFailsImmediatelyWhenToolNameConflictsWithGlobalTool() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        registerSkill(agent, "EchoSkill");
        agent.getAbilityManager().add(ToolCard.builder()
                .id("global-echo")
                .name("echo")
                .description("global echo")
                .inputParams(Map.of("type", "object"))
                .build());
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EchoSkill")
                .tools(List.of(tool("echo")))
                .build()).toCompletableFuture().join();

        assertThatThrownBy(() -> agent.activateSkill("EchoSkill", session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate effective tool name")
                .hasMessageContaining("echo")
                .hasMessageContaining("EchoSkill")
                .hasMessageContaining("global ability");
        assertThat(agent.getActiveSkillNames(session)).isEmpty();
    }

    @Test
    void activatingUnknownSkillFailsImmediately() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("session-a");
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("UnknownSkill")
                .tools(List.of(tool("unknownTool")))
                .build()).toCompletableFuture().join();

        assertThatThrownBy(() -> agent.activateSkill("UnknownSkill", session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill is not registered")
                .hasMessageContaining("UnknownSkill");
        assertThat(agent.getActiveSkillNames(session)).isEmpty();
    }

    @Test
    void registeringDuplicateToolNameForSameSkillFailsThroughCompletionStage() {
        TestAgent agent = new TestAgent();
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EchoSkill")
                .tools(List.of(tool("echo")))
                .build()).toCompletableFuture().join();

        assertThatThrownBy(() -> agent.registerSkillTools(SkillToolBinding.builder()
                        .skillName("EchoSkill")
                        .tools(List.of(tool("echo")))
                        .build()).toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasMessageContaining("Duplicate skill tool name")
                .hasMessageContaining("echo");
    }

    @Test
    void registerSkillReturnsFailedStageWhenMetadataNameValidationFails() throws IOException {
        TestAgent agent = new TestAgent();
        String sysOperationId = "base-agent-skill-test-" + UUID.randomUUID();
        Path skillDir = tempDir.resolve("MissingMetadataName");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Missing metadata name.
                ---
                Skill body.
                """);
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        try {
            agent.configure(new TestAgentConfig(sysOperationId));

            CompletionStage<Boolean> result = agent.registerSkill(skillDir.toString(), true);

            assertThat(result.toCompletableFuture()).isCompletedExceptionally();
            assertThatThrownBy(() -> result.toCompletableFuture().join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valid string name field");
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    private static Tool tool(String name) {
        return new Tool(ToolCard.builder()
                .id(name)
                .name(name)
                .description("tool " + name)
                .inputParams(Map.of("type", "object"))
                .build()) {
            // Only the tool card is used by binding tests.
        };
    }

    private void registerSkill(TestAgent agent, String skillName) {
        String sysOperationId = "base-agent-skill-test-" + UUID.randomUUID();
        Path skillDir = tempDir.resolve(skillName);
        try {
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: %s
                    description: Test skill.
                    ---
                    Skill body.
                    """.formatted(skillName));
            Runner.resourceMgr().addSysOperation(new SysOperationCard(
                    sysOperationId,
                    OperationMode.LOCAL,
                    LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
            ));
            agent.configure(new TestAgentConfig(sysOperationId));
            agent.registerSkill(skillDir.toString(), true).toCompletableFuture().join();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register test skill " + skillName, exception);
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
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
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private record TestAgentConfig(String sysOperationId) {
        public String getSysOperationId() {
            return sysOperationId;
        }
    }

    private static final class MemorySession implements AgentSessionApi {
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
            // This fixture only exercises state access; stream output is intentionally discarded.
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }

    private static final class SessionIdFailingSession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public String getSessionId() {
            throw new UnsupportedOperationException("skill activation must not depend on session id");
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
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
