/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentToolLifecycleStreamTest {

    @TempDir
    private Path tempDir;

    private final List<String> registeredToolIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String toolId : registeredToolIds) {
            Runner.resourceMgr.removeTool(toolId);
        }
    }

    @Test
    void streamEmitsToolCallBeforeSuccessfulGlobalToolResult() {
        CountingTool tool = new CountingTool(unique("lookup-tool"), "lookupEnv", Map.of("result", "prod"));
        ReActAgent agent = streamingAgent(List.of(
                toolChunk("call-global", "lookupEnv", "{\"key\":\"env\"}"),
                answerChunk("done")
        ));
        registerGlobalTool(agent, tool);

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "which env?"),
                new MemorySession("global-session"),
                List.of()
        ));

        OutputSchema toolCall = singleOutput(outputs, "tool_call");
        OutputSchema toolResult = singleOutput(outputs, "tool_result");
        assertThat(toolCall.getIndex()).isLessThan(toolResult.getIndex());
        assertThat(payload(toolCall))
                .containsEntry("tool_call_id", "call-global")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("arguments", "{\"key\":\"env\"}");
        assertThat(payload(toolResult))
                .containsEntry("tool_call_id", "call-global")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "completed")
                .containsEntry("result", "{result=prod}");
        assertThat(tool.invokeCount).isEqualTo(1);
    }

    @Test
    void streamEmitsErrorToolResultWhenToolThrows() {
        CountingTool tool = new CountingTool(unique("broken-tool"), "brokenTool",
                new IllegalStateException("boom"));
        ReActAgent agent = streamingAgent(List.of(
                toolChunk("call-broken", "brokenTool", "{\"key\":\"env\"}"),
                answerChunk("done")
        ));
        registerGlobalTool(agent, tool);

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "run broken"),
                new MemorySession("error-session"),
                List.of()
        ));

        assertThat(payload(singleOutput(outputs, "tool_result")))
                .containsEntry("tool_call_id", "call-broken")
                .containsEntry("tool_name", "brokenTool")
                .containsEntry("status", "error")
                .containsEntry("error", "Ability execution error: boom");
    }

    @Test
    void streamBackfillsMissingToolCallIdBeforeContextMessagesAndToolResult() {
        CountingTool tool = new CountingTool(unique("lookup-tool"), "lookupEnv", Map.of("result", "prod"));
        RecordingReActAgent agent = new RecordingReActAgent(
                ToolCall.builder().name("lookupEnv").arguments("{\"key\":\"env\"}").build()
        );
        registerGlobalTool(agent, tool);

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "which env?"),
                new MemorySession("missing-id-session"),
                List.of()
        ));

        String generatedId = String.valueOf(payload(singleOutput(outputs, "tool_call")).get("tool_call_id"));
        assertThat(generatedId).isNotBlank();
        assertThat(payload(singleOutput(outputs, "tool_result"))).containsEntry("tool_call_id", generatedId);
        assertThat(agent.getCapturedToolMessageIds()).contains(generatedId);
    }

    @Test
    void streamEmitsPairableEventsForMultipleToolsAndMonotonicIndexes() {
        CountingTool first = new CountingTool(unique("first-tool"), "firstTool", Map.of("result", "first"));
        CountingTool second = new CountingTool(unique("second-tool"), "secondTool", Map.of("result", "second"));
        ReActAgent agent = streamingAgent(List.of(
                multiToolChunk(List.of(
                        ToolCall.builder().id("call-first").name("firstTool").arguments("{\"key\":\"first\"}").build(),
                        ToolCall.builder().id("call-second").name("secondTool").arguments("{\"key\":\"second\"}").build()
                )),
                answerChunk("done")
        ));
        registerGlobalTool(agent, first);
        registerGlobalTool(agent, second);

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "run both"),
                new MemorySession("multi-session"),
                List.of()
        ));

        assertThat(toolCallIds(outputsOfType(outputs, "tool_call")))
                .containsExactlyInAnyOrder("call-first", "call-second");
        assertThat(toolCallIds(outputsOfType(outputs, "tool_result")))
                .containsExactlyInAnyOrder("call-first", "call-second");
        assertThat(outputs).extracting(OutputSchema::getIndex).doesNotHaveDuplicates();
        for (int i = 1; i < outputs.size(); i++) {
            assertThat(outputs.get(i).getIndex()).isGreaterThan(outputs.get(i - 1).getIndex());
        }
    }

    @Test
    void streamEmitsLifecycleEventsForActiveSkillTool() {
        CountingTool skillTool = new CountingTool(unique("skill-echo"), "echoTool", Map.of("echo", "hello"));
        RecordingReActAgent agent = new RecordingReActAgent(
                ToolCall.builder().id("call-skill").name("echoTool").arguments("{\"text\":\"hello\"}").build()
        );
        MemorySession session = new MemorySession("skill-session");
        registerSkill(agent, "EchoSkill");
        agent.registerSkillTools(SkillToolBinding.builder()
                .skillName("EchoSkill")
                .tools(List.of(skillTool))
                .build()).toCompletableFuture().join();
        agent.activateSkill("EchoSkill", session);

        List<OutputSchema> outputs = collectOutput(agent.stream(Map.of("query", "echo"), session, List.of()));

        assertThat(payload(singleOutput(outputs, "tool_result")))
                .containsEntry("tool_call_id", "call-skill")
                .containsEntry("tool_name", "echoTool")
                .containsEntry("status", "completed")
                .containsEntry("result", "{echo=hello}");
        assertThat(skillTool.invokeCount).isEqualTo(1);
    }

    @Test
    void invokeDoesNotExposeToolLifecycleEvents() {
        CountingTool tool = new CountingTool(unique("lookup-tool"), "lookupEnv", Map.of("result", "prod"));
        ReActAgent agent = invokeAgent(List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-invoke")
                                .name("lookupEnv")
                                .arguments("{\"key\":\"env\"}")
                                .build()))
                        .build(),
                new AssistantMessage("done")
        ));
        registerGlobalTool(agent, tool);

        Object result = agent.invoke(Map.of("query", "which env?"), new MemorySession("invoke-session"))
                .toCompletableFuture()
                .join();

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.toString()).doesNotContain("tool_call").doesNotContain("tool_result");
    }

    private static ReActAgent streamingAgent(List<AssistantMessageChunk> chunks) {
        ReActAgent agent = new ReActAgent(agentCard("stream-agent"));
        agent.setLlm(new Model(new ScriptedStreamModelClient(chunks)));
        return agent;
    }

    private static ReActAgent invokeAgent(List<AssistantMessage> messages) {
        ReActAgent agent = new ReActAgent(agentCard("invoke-agent"));
        agent.setLlm(new Model(new ScriptedInvokeModelClient(messages)));
        return agent;
    }

    private void registerGlobalTool(ReActAgent agent, CountingTool tool) {
        Runner.resourceMgr.addTool(tool);
        registeredToolIds.add(tool.getCard().getId());
        agent.getAbilityManager().add(tool.getCard());
    }

    private void registerSkill(ReActAgent agent, String skillName) {
        String sysOperationId = "react-agent-tool-lifecycle-" + UUID.randomUUID();
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

    private static AgentCard agentCard(String name) {
        return new AgentCard(name + "-" + System.nanoTime(), name, "test agent");
    }

    private static AssistantMessageChunk toolChunk(String id, String name, String arguments) {
        return multiToolChunk(List.of(ToolCall.builder().id(id).name(name).arguments(arguments).build()));
    }

    private static AssistantMessageChunk multiToolChunk(List<ToolCall> toolCalls) {
        return AssistantMessageChunk.builder()
                .content("")
                .toolCalls(toolCalls)
                .finishReason("tool_calls")
                .build();
    }

    private static AssistantMessageChunk answerChunk(String content) {
        return AssistantMessageChunk.builder().content(content).finishReason("stop").build();
    }

    private static List<OutputSchema> collectOutput(Iterator<Object> iterator) {
        List<OutputSchema> outputs = new ArrayList<>();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof OutputSchema outputSchema) {
                outputs.add(outputSchema);
            }
        }
        return outputs;
    }

    private static OutputSchema singleOutput(List<OutputSchema> outputs, String type) {
        List<OutputSchema> matches = outputsOfType(outputs, type);
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private static List<OutputSchema> outputsOfType(List<OutputSchema> outputs, String type) {
        return outputs.stream().filter(output -> Objects.equals(output.getType(), type)).toList();
    }

    private static List<String> toolCallIds(List<OutputSchema> outputs) {
        return outputs.stream().map(output -> String.valueOf(payload(output).get("tool_call_id"))).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        assertThat(output.getPayload()).isInstanceOf(Map.class);
        return (Map<String, Object>) output.getPayload();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static final class ScriptedStreamModelClient implements Model.ModelClient {
        private final List<AssistantMessageChunk> chunks;
        private int streamIndex;

        private ScriptedStreamModelClient(List<AssistantMessageChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("fallback"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            AssistantMessageChunk chunk = chunks.get(Math.min(streamIndex, chunks.size() - 1));
            streamIndex++;
            return List.of(chunk).iterator();
        }
    }

    private static final class ScriptedInvokeModelClient implements Model.ModelClient {
        private final List<AssistantMessage> messages;
        private int index;

        private ScriptedInvokeModelClient(List<AssistantMessage> messages) {
            this.messages = messages;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            AssistantMessage result = this.messages.get(Math.min(index, this.messages.size() - 1));
            index++;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return List.<AssistantMessageChunk>of(answerChunk("fallback")).iterator();
        }
    }

    private static final class RecordingReActAgent extends ReActAgent {
        private final ToolCall toolCall;
        private final List<String> capturedToolMessageIds = new ArrayList<>();
        private int callCount;

        private RecordingReActAgent(ToolCall toolCall) {
            super(agentCard("recording-agent"));
            this.toolCall = toolCall;
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            callCount++;
            for (BaseMessage message : context.getMessages(null, true)) {
                if (message instanceof ToolMessage toolMessage && toolMessage.getToolCallId() != null) {
                    capturedToolMessageIds.add(toolMessage.getToolCallId());
                }
            }
            if (callCount == 1) {
                return AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
            }
            return new AssistantMessage("done");
        }

        private List<String> getCapturedToolMessageIds() {
            return capturedToolMessageIds;
        }
    }

    private static final class CountingTool extends Tool {
        private final Object result;
        private int invokeCount;

        private CountingTool(String id, String name, Object result) {
            super(ToolCard.builder()
                    .id(id)
                    .name(name)
                    .description(name)
                    .inputParams(Map.of("type", "object"))
                    .build());
            this.result = result;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            invokeCount++;
            if (result instanceof Exception exception) {
                throw exception;
            }
            return result;
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
