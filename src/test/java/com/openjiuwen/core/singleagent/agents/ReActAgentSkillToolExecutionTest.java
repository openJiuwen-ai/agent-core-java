/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentSkillToolExecutionTest {
    @TempDir
    private Path tempDir;

    @Test
    void activeSkillToolExecutesConcreteToolInstance() {
        EchoTool echoTool = new EchoTool();
        RecordingReActAgent agent = new RecordingReActAgent();
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(2);
        agent.configure(config);
        MemorySession session = new MemorySession("session-1");
        registerSkill(agent, "EchoSkill");
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EchoSkill")
                .tools(List.of(echoTool))
                .build()).toCompletableFuture().join();
        agent.activateSkill("EchoSkill", session);

        agent.invoke(Map.of("query", "echo"), session).toCompletableFuture().join();

        assertThat(echoTool.invokedText).isEqualTo("hello");
    }

    @Test
    void executingToolCallFailsWhenActiveSkillToolConflictsWithGlobalTool() {
        EchoTool echoTool = new EchoTool();
        RecordingReActAgent agent = new RecordingReActAgent();
        MemorySession session = new MemorySession("session-conflict");
        registerSkill(agent, "EchoSkill");
        ModelContext context = agent.initContext(session);
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EchoSkill")
                .tools(List.of(echoTool))
                .build()).toCompletableFuture().join();
        agent.activateSkill("EchoSkill", session);
        agent.getAbilityManager().add(ToolCard.builder()
                .id("global-echo")
                .name("echoTool")
                .description("global echo")
                .inputParams(Map.of("type", "object"))
                .build());

        assertThatThrownBy(() -> agent.executeToolCall(
                new AgentCallbackContext(agent),
                List.of(ToolCall.builder()
                        .id("call-echo")
                        .name("echoTool")
                        .arguments("{\"text\":\"hello\"}")
                        .build()),
                session,
                context
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate effective tool name")
                .hasMessageContaining("echoTool");
        assertThat(echoTool.invokedText).isNull();
    }

    private void registerSkill(RecordingReActAgent agent, String skillName) {
        String sysOperationId = "react-agent-skill-test-" + UUID.randomUUID();
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
            ReActAgentConfig config = new ReActAgentConfig();
            config.setSysOperationId(sysOperationId);
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            config.setMaxIterations(2);
            agent.configure(config);
            agent.registerSkill(skillDir.toString(), true).toCompletableFuture().join();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to register test skill " + skillName, exception);
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    private static final class EchoTool extends Tool {
        private String invokedText;

        private EchoTool() {
            super(ToolCard.builder()
                    .id("echoTool")
                    .name("echoTool")
                    .description("echo")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokedText = String.valueOf(inputs.get("text"));
            return Map.of("echo", invokedText);
        }
    }

    private static final class RecordingReActAgent extends ReActAgent {
        private int callCount;

        private RecordingReActAgent() {
            super(new AgentCard("agent", "agent", "test"));
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            callCount++;
            if (callCount == 1) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-echo")
                                .name("echoTool")
                                .arguments("{\"text\":\"hello\"}")
                                .build()))
                        .build();
            }
            return new AssistantMessage("done");
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
