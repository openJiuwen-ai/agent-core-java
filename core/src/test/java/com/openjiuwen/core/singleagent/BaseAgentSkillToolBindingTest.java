/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.openjiuwen.core.common.logging.LogCaptureFixture;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillSource;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.skills.repository.ClasspathSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.FileSystemSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.SkillContentValidator;
import com.openjiuwen.core.singleagent.skills.repository.WorkspaceSkillRepository;
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
import java.nio.charset.StandardCharsets;
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
    void skillRegistrationActivationExposesToolContract() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("closed-loop-session");
        Tool skillTool = tool("closedLoopTool");
        registerSkill(agent, "ClosedLoopSkill");
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("ClosedLoopSkill")
                .tools(List.of(skillTool))
                .build()).toCompletableFuture().join();

        assertThat(agent.getActiveSkillNames(session)).isEmpty();
        assertThat(visibleToolNames(agent, session)).contains("skill_tool")
                .doesNotContain("closedLoopTool");
        assertThat(agent.findActiveSkillTool("closedLoopTool", session)).isEmpty();

        agent.activateSkill("ClosedLoopSkill", session);
        assertThat(agent.getActiveSkillNames(session)).containsExactly("ClosedLoopSkill");
        assertThat(visibleToolNames(agent, session)).contains("closedLoopTool", "skill_tool");
        assertThat(agent.findActiveSkillTool("closedLoopTool", session)).containsSame(skillTool);

        agent.deactivateSkill("ClosedLoopSkill", session);
        assertThat(agent.getActiveSkillNames(session)).isEmpty();
        assertThat(visibleToolNames(agent, session)).contains("skill_tool")
                .doesNotContain("closedLoopTool");
        assertThat(agent.findActiveSkillTool("closedLoopTool", session)).isEmpty();
    }

    @Test
    void skillToolLifecycle_afterDeactivationAndManagerReplacement() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("lifecycle-session");
        Tool lifecycleTool = tool("lifecycleTool");
        registerSkill(agent, "LifecycleSkill");
        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("LifecycleSkill")
                                .tools(List.of(lifecycleTool))
                                .build())
                .toCompletableFuture()
                .join();

        agent.activateSkill("LifecycleSkill", session);
        assertThat(visibleToolNames(agent, session)).contains("lifecycleTool", "skill_tool");
        assertThat(executeTool(agent, session, "lifecycle-tool-active", "lifecycleTool"))
                .doesNotContain("not active");

        agent.deactivateSkill("LifecycleSkill", session);
        assertThat(visibleToolNames(agent, session)).contains("skill_tool")
                .doesNotContain("lifecycleTool");
        assertThat(executeTool(agent, session, "lifecycle-tool-inactive", "lifecycleTool"))
                .contains("not active");

        agent.setAbilityManager(new AbilityManager(agent.getCard().getId()));
        assertThat(agent.getAbilityManager().getTools()).containsKey("skill_tool");
        assertThat(visibleToolNames(agent, session)).contains("skill_tool")
                .doesNotContain("lifecycleTool");
        assertThat(executeTool(agent, session, "lifecycle-tool-replaced", "lifecycleTool"))
                .contains("not active");

        agent.activateSkill("LifecycleSkill", session);
        assertThat(visibleToolNames(agent, session)).contains("lifecycleTool", "skill_tool");
        assertThat(executeTool(agent, session, "lifecycle-tool-reactivated", "lifecycleTool"))
                .doesNotContain("not active");
        agent.deactivateSkill("LifecycleSkill", session);
        assertThat(visibleToolNames(agent, session)).contains("skill_tool")
                .doesNotContain("lifecycleTool");
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
    void staleSkillNameGrantsNoVisibilityOrExecution() {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("stale-skill-session");
        registerSkill(agent, "ReloadableSkill");
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("ReloadableSkill")
                .tools(List.of(tool("reloadableTool")))
                .build()).toCompletableFuture().join();
        agent.activateSkill("ReloadableSkill", session);
        assertThat(visibleToolNames(agent, session)).contains("reloadableTool");

        agent.getSkillUtil().getSkillManager().clear();

        assertThat(agent.getSkillUtil().getSkillManager().has("ReloadableSkill")).isFalse();
        assertThat(agent.getActiveSkillNames(session)).containsExactly("ReloadableSkill");
        assertThat(visibleToolNames(agent, session)).doesNotContain("reloadableTool");
        assertThat(agent.findActiveSkillTool("reloadableTool", session)).isEmpty();
        ToolCall staleCall = ToolCall.builder()
                .id("stale-call")
                .name("reloadableTool")
                .arguments("{}")
                .build();
        assertThat(agent.getAbilityManager()
                .execute(null, staleCall, session, false).get(0)
                .toolMessage().getContentAsString())
                .contains("not active")
                .contains("reloadableTool");

        agent.deactivateSkill("ReloadableSkill", session);
        registerSkill(agent, "ReloadableSkill");
        agent.activateSkill("ReloadableSkill", session);

        assertThat(visibleToolNames(agent, session)).contains("reloadableTool");
    }

    @Test
    void workspaceSkillRejectedBySafetyCheckDoesNotGrantStaleToolAccess()
            throws IOException {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("rejected-workspace-skill-session");
        String sysOperationId = "rejected-workspace-skill-test-" + UUID.randomUUID();
        Path workspaceRoot = tempDir.resolve("workspace");
        Path skillDirectory = workspaceRoot.resolve("RejectedWorkspaceSkill");
        Files.createDirectories(skillDirectory);
        writeSkillFile(skillDirectory.resolve("SKILL.md"), "RejectedWorkspaceSkill");
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        try {
            agent.configure(new TestAgentConfig(sysOperationId));
            agent.getSkillUtil().getSkillManager().setRepositories(
                    List.of(new WorkspaceSkillRepository(workspaceRoot, "user1")));
            agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());
            agent.registerSkillTools(SkillToolBinding.builder()
                    .skillName("RejectedWorkspaceSkill")
                    .tools(List.of(tool("rejectedSkillTool")))
                    .build()).toCompletableFuture().join();
            agent.activateSkill("RejectedWorkspaceSkill", session);
            assertThat(visibleToolNames(agent, session)).contains("rejectedSkillTool");

            Files.write(
                    skillDirectory.resolve("SKILL.md"),
                    new byte[SkillContentValidator.DEFAULT_WORKSPACE_MAX_BYTES + 1]);
            agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());

            assertThat(agent.getSkillUtil().getSkillManager().has("RejectedWorkspaceSkill"))
                    .isFalse();
            assertThat(agent.getActiveSkillNames(session))
                    .containsExactly("RejectedWorkspaceSkill");
            assertThat(visibleToolNames(agent, session)).doesNotContain("rejectedSkillTool");
            ToolCall rejectedCall = ToolCall.builder()
                    .id("rejected-skill-call")
                    .name("rejectedSkillTool")
                    .arguments("{}")
                    .build();
            assertThat(agent.getAbilityManager()
                    .execute(null, rejectedCall, session, false).get(0)
                    .toolMessage().getContentAsString())
                    .contains("not active")
                    .contains("rejectedSkillTool");

            writeSkillFile(skillDirectory.resolve("SKILL.md"), "RejectedWorkspaceSkill");
            agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());

            assertThat(visibleToolNames(agent, session)).contains("rejectedSkillTool");
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    @Test
    void workspaceSkillRejectedBySafetyCheckActivatesFallbackVersion() throws IOException {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("workspace-skill-fallback-session");
        String sysOperationId = "workspace-skill-fallback-test-" + UUID.randomUUID();
        Path filesystemRoot = tempDir.resolve("filesystem");
        Path workspaceRoot = tempDir.resolve("workspace");
        Path filesystemSkillDirectory = filesystemRoot.resolve("FallbackWorkspaceSkill");
        Path workspaceSkillDirectory = workspaceRoot.resolve("FallbackWorkspaceSkill");
        Files.createDirectories(filesystemSkillDirectory);
        Files.createDirectories(workspaceSkillDirectory);
        writeSkillFile(
                filesystemSkillDirectory.resolve("SKILL.md"), "FallbackWorkspaceSkill");
        Files.write(
                workspaceSkillDirectory.resolve("SKILL.md"),
                new byte[SkillContentValidator.DEFAULT_WORKSPACE_MAX_BYTES + 1]);
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        try {
            agent.configure(new TestAgentConfig(sysOperationId));
            try (LogCaptureFixture audit = LogCaptureFixture.attach("agent")) {
                agent.getSkillUtil().getSkillManager().setRepositories(
                        List.of(
                                new FileSystemSkillRepository(filesystemRoot),
                                new WorkspaceSkillRepository(workspaceRoot, "user1")));
                agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());

                assertThat(audit.count("WARNING", "SKILL_REJECTED")).isEqualTo(1);
            }
            agent.registerSkillTools(SkillToolBinding.builder()
                    .skillName("FallbackWorkspaceSkill")
                    .tools(List.of(tool("fallbackSkillTool")))
                    .build()).toCompletableFuture().join();
            agent.activateSkill("FallbackWorkspaceSkill", session);
            Skill activeSkill =
                    agent.getSkillUtil().getSkillManager().get("FallbackWorkspaceSkill");

            assertThat(activeSkill.getSource()).isEqualTo(SkillSource.FILESYSTEM);
            assertThat(visibleToolNames(agent, session)).contains("fallbackSkillTool");

            Files.delete(workspaceSkillDirectory.resolve("SKILL.md"));
            agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());
            activeSkill = agent.getSkillUtil().getSkillManager().get("FallbackWorkspaceSkill");

            assertThat(activeSkill.getSource()).isEqualTo(SkillSource.FILESYSTEM);
            assertThat(agent.getActiveSkillNames(session))
                    .containsExactly("FallbackWorkspaceSkill");
            assertThat(visibleToolNames(agent, session)).contains("fallbackSkillTool");
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    @Test
    void removingWorkspaceSkillRestoresClasspathVersion()
            throws IOException {
        TestAgent agent = new TestAgent();
        MemorySession session = new MemorySession("classpath-skill-fallback-session");
        String sysOperationId = "classpath-skill-fallback-test-" + UUID.randomUUID();
        Path workspaceRoot = tempDir.resolve("workspace");
        Path workspaceSkillDirectory = workspaceRoot.resolve("builtin_demo");
        Files.createDirectories(workspaceSkillDirectory);
        writeSkillFile(workspaceSkillDirectory.resolve("SKILL.md"), "builtin_demo");
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        try {
            agent.configure(new TestAgentConfig(sysOperationId));
            agent.registerSkill(workspaceSkillDirectory.toString(), true)
                    .toCompletableFuture()
                    .join();
            agent.getSkillUtil()
                    .getSkillManager()
                    .setRepositories(
                            List.of(
                                    new ClasspathSkillRepository("issue196/skills"),
                                    new WorkspaceSkillRepository(workspaceRoot, "user1")));
            agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());
            agent.registerSkillTools(SkillToolBinding.builder()
                    .skillName("builtin_demo")
                    .tools(List.of(tool("classpathFallbackTool")))
                    .build()).toCompletableFuture().join();
            agent.activateSkill("builtin_demo", session);

            assertThat(agent.getSkillUtil().getSkillManager().get("builtin_demo").getSource())
                    .isEqualTo(SkillSource.WORKSPACE);
            assertThat(agent.getActiveSkillNames(session)).containsExactly("builtin_demo");
            assertThat(visibleToolNames(agent, session)).contains("classpathFallbackTool");
            assertThat(executeTool(agent, session, "classpath-active", "classpathFallbackTool"))
                    .doesNotContain("not active");

            Files.delete(workspaceSkillDirectory.resolve("SKILL.md"));
            agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());

            assertThat(agent.getSkillUtil().getSkillManager().get("builtin_demo").getSource())
                    .isEqualTo(SkillSource.CLASSPATH);
            assertThat(agent.getActiveSkillNames(session)).containsExactly("builtin_demo");
            assertThat(visibleToolNames(agent, session)).contains("classpathFallbackTool");
            assertThat(executeTool(agent, session, "classpath-restored", "classpathFallbackTool"))
                    .doesNotContain("not active");
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
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

    private static List<String> visibleToolNames(BaseAgent agent, AgentSessionApi session) {
        return agent.listEffectiveToolInfo(session).stream()
                .map(ToolInfo::getName)
                .toList();
    }

    private static String executeTool(
            BaseAgent agent, AgentSessionApi session, String callId, String toolName) {
        ToolCall toolCall =
                ToolCall.builder().id(callId).name(toolName).arguments("{}").build();
        return agent.getAbilityManager()
                .execute(null, toolCall, session, false)
                .get(0)
                .toolMessage()
                .getContentAsString();
    }

    private static void writeSkillFile(Path skillFile, String skillName) throws IOException {
        Files.writeString(skillFile, """
                ---
                name: %s
                description: Test skill.
                ---
                Skill body.
                """.formatted(skillName), StandardCharsets.UTF_8);
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
