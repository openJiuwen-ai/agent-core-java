/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ExternalTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentExternalToolStreamTest {
    private static final String EXTERNAL_TOOL_NAME = "frontend_read_text_input";
    private static final String NORMAL_TOOL_NAME = "countingTool";

    private final List<String> registeredToolIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        CheckpointerFactory.setDefaultCheckpointer(null);
        for (String toolId : registeredToolIds) {
            Runner.resourceMgr().removeTool(toolId);
        }
    }

    @Test
    void streamEmitsToolCallThenExternalPendingAndNoToolResultBeforeResume() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("stream-pending-session");

        List<OutputSchema> outputs = collectOutput(agent.stream(
                Map.of("query", "read frontend"),
                session,
                List.of()
        ));

        assertThat(outputs).extracting(OutputSchema::getType)
                .contains("tool_call", "external_tool_call_required")
                .doesNotContain("tool_result", "answer");
        assertThat(toolCallIds(outputsOfType(outputs, "tool_call")))
                .containsExactly("call-external", "call-normal");

        OutputSchema pendingOutput = singleOutput(outputs, "external_tool_call_required");
        int lastToolCallIndex = toolCallOutputs(outputs).stream()
                .mapToInt(OutputSchema::getIndex)
                .max()
                .orElseThrow();
        assertThat(lastToolCallIndex).isLessThan(pendingOutput.getIndex());
        assertThat(payload(pendingOutput))
                .containsEntry("result_type", "external_tool_call_required");
        assertThat(externalToolCalls(payload(pendingOutput)))
                .containsExactly(Map.of(
                        "tool_call_id", "call-external",
                        "tool_name", EXTERNAL_TOOL_NAME,
                        "arguments", "{\"field_id\":\"demo_external_text\"}"
                ));
        assertIndexesStrictlyIncreasing(outputs);
        assertThat(normalToolInvokes).hasValue(0);
    }

    @Test
    void streamResumeEmitsToolResultsAndFinalAnswer() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("stream-resume-session");
        agent.stream(Map.of("query", "read frontend"), session, List.of());
        int firstStreamOutputCount = session.streamSize();

        List<OutputSchema> allOutputs = collectOutput(agent.stream(Map.of(
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session, List.of()));
        List<OutputSchema> resumeOutputs = allOutputs.subList(firstStreamOutputCount, allOutputs.size());

        assertThat(toolCallIds(outputsOfType(resumeOutputs, "tool_result")))
                .containsExactly("call-external", "call-normal");
        assertThat(payload(singleOutput(resumeOutputs, "answer")))
                .containsEntry("result_type", "answer")
                .containsEntry("output", "final answer");
        assertThat(normalToolInvokes).hasValue(1);
    }

    @Test
    void streamIteratorDoesNotCompleteBeforeCloseTimeCommitFinishes() throws Exception {
        ScriptedReActAgent agent = new ScriptedReActAgent(List.of(new AssistantMessage("final answer")));
        BlockingPostAgentCheckpointer checkpointer = new BlockingPostAgentCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);

        Iterator<Object> stream = agent.stream(
                Map.of("query", "say hello"),
                new AgentSession("stream-close-commit-" + UUID.randomUUID(), null, agent.getCard()),
                List.of()
        );
        CompletableFuture<List<OutputSchema>> drained = CompletableFuture.supplyAsync(() -> collectOutput(stream));

        assertThat(checkpointer.awaitCommitStarted()).isTrue();
        try {
            assertThatThrownBy(() -> drained.get(200, TimeUnit.MILLISECONDS))
                    .as("stream iterator must stay open until close-time checkpoint commit finishes")
                    .isInstanceOf(TimeoutException.class);
        } finally {
            checkpointer.releaseCommit();
        }

        assertThat(payload(singleOutput(drained.get(5, TimeUnit.SECONDS), "answer")))
                .containsEntry("result_type", "answer")
                .containsEntry("output", "final answer");
    }

    private ScriptedReActAgent pendingAgent(AtomicInteger normalToolInvokes) {
        ScriptedReActAgent agent = new ScriptedReActAgent(List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(externalToolCall(), normalToolCall()))
                        .build(),
                new AssistantMessage("final answer")
        ));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(3);
        agent.configure(config);
        agent.getAbilityManager().add(externalTool());
        registerNormalTool(agent, normalToolInvokes);
        return agent;
    }

    private void registerNormalTool(ReActAgent agent, AtomicInteger normalToolInvokes) {
        CountingTool tool = new CountingTool(unique("counting-tool"), normalToolInvokes);
        Runner.resourceMgr().addTool(tool);
        registeredToolIds.add(tool.getCard().getId());
        agent.getAbilityManager().add(tool.getCard());
    }

    private static ToolCall externalToolCall() {
        return ToolCall.builder()
                .id("call-external")
                .name(EXTERNAL_TOOL_NAME)
                .arguments("{\"field_id\":\"demo_external_text\"}")
                .build();
    }

    private static ToolCall normalToolCall() {
        return ToolCall.builder()
                .id("call-normal")
                .name(NORMAL_TOOL_NAME)
                .arguments("{\"value\":\"later\"}")
                .build();
    }

    private static ExternalTool externalTool() {
        return new ExternalTool(ToolCard.builder()
                .id("external." + EXTERNAL_TOOL_NAME)
                .name(EXTERNAL_TOOL_NAME)
                .description("Read frontend text input")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build());
    }

    private static Map<String, Object> externalResult(String toolCallId, String text) {
        return Map.of(
                "tool_call_id", toolCallId,
                "result", Map.of("field_id", "demo_external_text", "text", text)
        );
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

    private static List<OutputSchema> toolCallOutputs(List<OutputSchema> outputs) {
        return outputsOfType(outputs, "tool_call");
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> externalToolCalls(Map<String, Object> payload) {
        assertThat(payload.get("external_tool_calls")).isInstanceOf(List.class);
        return (List<Map<String, Object>>) payload.get("external_tool_calls");
    }

    private static void assertIndexesStrictlyIncreasing(List<OutputSchema> outputs) {
        assertThat(outputs).extracting(OutputSchema::getIndex).doesNotHaveDuplicates();
        for (int i = 1; i < outputs.size(); i++) {
            assertThat(outputs.get(i).getIndex()).isGreaterThan(outputs.get(i - 1).getIndex());
        }
    }

    private static AgentCard agentCard(String name) {
        return new AgentCard(name + "-" + System.nanoTime(), name, "test agent");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static final class ScriptedReActAgent extends ReActAgent {
        private final List<AssistantMessage> responses;
        private int index;

        private ScriptedReActAgent(List<AssistantMessage> responses) {
            super(agentCard("external-stream-agent"));
            this.responses = responses;
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            AssistantMessage response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }
    }

    private static final class CountingTool extends Tool {
        private final AtomicInteger invokes;

        private CountingTool(String id, AtomicInteger invokes) {
            super(ToolCard.builder()
                    .id(id)
                    .name(NORMAL_TOOL_NAME)
                    .description("Counting tool")
                    .inputParams(Map.of("type", "object"))
                    .build());
            this.invokes = invokes;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokes.incrementAndGet();
            return Map.of("value", inputs.get("value"), "status", "counted");
        }
    }

    private static final class BlockingPostAgentCheckpointer extends InMemoryCheckpointer {
        private final CountDownLatch commitStarted = new CountDownLatch(1);
        private final CountDownLatch releaseCommit = new CountDownLatch(1);
        private final AtomicBoolean blocked = new AtomicBoolean();

        @Override
        public void postAgentExecute(BaseSession session) {
            if (blocked.compareAndSet(false, true)) {
                commitStarted.countDown();
                try {
                    if (!releaseCommit.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release commit");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            super.postAgentExecute(session);
        }

        boolean awaitCommitStarted() throws InterruptedException {
            return commitStarted.await(5, TimeUnit.SECONDS);
        }

        void releaseCommit() {
            releaseCommit.countDown();
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

        private int streamSize() {
            return stream.size();
        }
    }
}
