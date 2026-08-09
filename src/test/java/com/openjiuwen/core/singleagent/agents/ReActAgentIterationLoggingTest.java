/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentIterationLoggingTest {

    @Test
    void logsIterationStartAndEndWhenModelReturnsAnswer() {
        ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("done"));

        try (AgentLogCapture logs = new AgentLogCapture()) {
            Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

            assertThat(result).containsEntry("output", "done")
                    .containsEntry("result_type", "answer");
            assertThat(logs.reactMessages()).containsExactly(
                    "ReAct iteration 1/1 started",
                    "ReAct iteration 1/1 ended"
            );
            assertThat(logs.record("ReAct iteration 1/1 started").getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.record("ReAct iteration 1/1 ended").getLevel()).isEqualTo(Level.INFO);
        }
    }

    @Test
    void logsIterationFailureWithOriginalExceptionAndStillLogsEnd() {
        IllegalStateException failure = new IllegalStateException("model exploded");
        ScriptedAgent agent = scriptedAgent(2, failure);

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> invokeMap(agent, Map.of("query", "hello")))
                    .hasRootCauseMessage("model exploded");

            assertThat(logs.reactMessages()).containsExactly(
                    "ReAct iteration 1/2 started",
                    "ReAct iteration 1/2 failed",
                    "ReAct iteration 1/2 ended"
            );
            assertThat(logs.record("ReAct iteration 1/2 failed").getThrown()).isSameAs(failure);
            assertThat(logs.record("ReAct iteration 1/2 failed").getLevel()).isEqualTo(Level.SEVERE);
            assertThat(logs.reactMessages()).doesNotContain("ReActAgent invoke failed");
        }
    }

    @Test
    void logsStreamingIterationFailureWithoutDuplicateInvokeFailure() {
        IllegalStateException failure = new IllegalStateException("streaming model exploded");
        ScriptedAgent agent = scriptedAgent(1, failure);
        MemorySession session = new MemorySession();

        try (AgentLogCapture logs = new AgentLogCapture()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) agent.innerInvoke(
                    session,
                    Map.of("query", "hello"),
                    "hello",
                    false,
                    null,
                    Map.of("_streaming", true)
            );

            assertThat(result).containsEntry("output", "IllegalStateException: streaming model exploded")
                    .containsEntry("result_type", "error");
            assertThat(logs.reactMessages()).containsExactly(
                    "ReAct iteration 1/1 started",
                    "ReAct iteration 1/1 failed",
                    "ReAct iteration 1/1 ended"
            );
            assertThat(logs.record("ReAct iteration 1/1 failed").getThrown()).isSameAs(failure);
            assertThat(logs.reactMessages()).doesNotContain("ReActAgent invoke failed");
        }
    }

    @Test
    void logsStreamingErrorHandlingFailureWithOriginalException() {
        RuntimeException iterationFailure = new IllegalStateException("streaming model exploded");
        RuntimeException writeFailure = new IllegalStateException("streaming error write exploded");
        ScriptedAgent agent = scriptedAgent(1, iterationFailure);
        MemorySession session = new StreamFailureSession(writeFailure);

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> agent.innerInvoke(
                    session,
                    Map.of("query", "hello"),
                    "hello",
                    false,
                    null,
                    Map.of("_streaming", true)
            )).isSameAs(writeFailure);

            assertThat(logs.reactMessages()).containsExactly(
                    "ReAct iteration 1/1 started",
                    "ReAct iteration 1/1 failed",
                    "ReAct iteration 1/1 ended",
                    "ReActAgent streaming error handling failed"
            );
            assertThat(logs.record("ReAct iteration 1/1 failed").getThrown()).isSameAs(iterationFailure);
            assertThat(logs.record("ReAct iteration 1/1 failed").getLevel()).isEqualTo(Level.SEVERE);
            assertThat(logs.record("ReActAgent streaming error handling failed").getThrown()).isSameAs(writeFailure);
            assertThat(logs.record("ReActAgent streaming error handling failed").getLevel()).isEqualTo(Level.SEVERE);
            assertThat(logs.reactMessages()).doesNotContain("ReActAgent invoke failed");
        }
    }

    @Test
    void logsInvokeFailureOutsideIterationWithOriginalException() {
        ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("unused"));

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> invokeMap(agent, Map.of()))
                    .hasRootCauseMessage("Input must contain 'query'");

            assertThat(logs.reactMessages()).containsExactly("ReActAgent invoke failed");
            assertThat(logs.record("ReActAgent invoke failed").getThrown())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Input must contain 'query'");
            assertThat(logs.record("ReActAgent invoke failed").getLevel()).isEqualTo(Level.SEVERE);
        }
    }

    @Test
    void logsInitializationFailureWithOriginalException() {
        RuntimeException failure = new IllegalStateException("initialization exploded");
        ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("unused"));
        Map<String, Object> kwargs = new LinkedHashMap<>() {
            @Override
            public Object get(Object key) {
                if ("_streaming".equals(key)) {
                    throw failure;
                }
                return super.get(key);
            }
        };

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> agent.innerInvoke(
                    new MemorySession(),
                    Map.of("query", "hello"),
                    "hello",
                    false,
                    null,
                    kwargs
            )).isSameAs(failure);

            assertThat(logs.reactMessages()).containsExactly("ReActAgent invoke failed");
            assertThat(logs.record("ReActAgent invoke failed").getThrown()).isSameAs(failure);
            assertThat(logs.record("ReActAgent invoke failed").getLevel()).isEqualTo(Level.SEVERE);
        }
    }

    @Test
    void propagatesStreamingInitializationFailureWithOriginalException() {
        RuntimeException failure = new IllegalStateException("streaming initialization exploded");
        ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("unused"));
        Map<String, Object> inputs = new LinkedHashMap<>() {
            @Override
            public Object get(Object key) {
                if ("user_id".equals(key)) {
                    throw failure;
                }
                return super.get(key);
            }
        };
        inputs.put("query", "hello");

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> agent.innerInvoke(
                    new MemorySession(),
                    inputs,
                    "hello",
                    false,
                    null,
                    Map.of("_streaming", true)
            )).isSameAs(failure);

            assertThat(logs.reactMessages()).containsExactly("ReActAgent invoke failed");
            assertThat(logs.record("ReActAgent invoke failed").getThrown()).isSameAs(failure);
        }
    }

    @Test
    void preservesInitializationFailureWithoutCleanup() {
        RuntimeException initializationFailure = new IllegalStateException("initialization exploded");
        RuntimeException cleanupFailure = new IllegalStateException("unexpected cleanup exploded");
        ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("unused"));
        CleanupFailureSession session = new CleanupFailureSession(cleanupFailure);
        Map<String, Object> kwargs = new LinkedHashMap<>() {
            @Override
            public Object get(Object key) {
                if ("_streaming".equals(key)) {
                    throw initializationFailure;
                }
                return super.get(key);
            }
        };

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> agent.innerInvoke(
                    session,
                    Map.of("query", "hello"),
                    "hello",
                    true,
                    null,
                    kwargs
            )).isSameAs(initializationFailure);

            assertThat(session.closeStreamCalled).isFalse();
            assertThat(session.commitCalled).isFalse();
            assertThat(logs.reactMessages()).containsExactly("ReActAgent invoke failed");
            assertThat(logs.record("ReActAgent invoke failed").getThrown()).isSameAs(initializationFailure);
        }
    }

    @Test
    void logsEveryIterationAndErrorWhenMaxIterationsAreReached() {
        AssistantMessage toolRequest = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall("call-1", "echo", "{}")))
                .build();
        ScriptedAgent agent = scriptedAgent(2, toolRequest);
        agent.getAbilityManager().add(new EchoTool());

        try (AgentLogCapture logs = new AgentLogCapture()) {
            Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

            assertThat(result).containsEntry("result_type", "error")
                    .containsEntry("output", "Max iterations reached without completion");
            assertThat(logs.reactMessages()).containsExactly(
                    "ReAct iteration 1/2 started",
                    "ReAct iteration 1/2 ended",
                    "ReAct iteration 2/2 started",
                    "ReAct iteration 2/2 ended",
                    "ReActAgent reached max iterations without completion: 2"
            );
            assertThat(logs.record("ReActAgent reached max iterations without completion: 2").getLevel())
                    .isEqualTo(Level.SEVERE);
        }
    }

    @Test
    void logsCleanupFailureWithOriginalException() {
        IllegalStateException failure = new IllegalStateException("commit exploded");
        ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("done"));
        CleanupFailureSession session = new CleanupFailureSession(failure);

        try (AgentLogCapture logs = new AgentLogCapture()) {
            assertThatThrownBy(() -> agent.innerInvoke(
                    session,
                    Map.of("query", "hello"),
                    "hello",
                    true,
                    null,
                    Map.of()
            )).isSameAs(failure);

            assertThat(logs.reactMessages()).containsExactly(
                    "ReAct iteration 1/1 started",
                    "ReAct iteration 1/1 ended",
                    "ReActAgent cleanup failed"
            );
            assertThat(logs.record("ReActAgent cleanup failed").getThrown()).isSameAs(failure);
            assertThat(logs.record("ReActAgent cleanup failed").getLevel()).isEqualTo(Level.SEVERE);
            assertThat(session.closeStreamCalled).isTrue();
            assertThat(session.commitCalled).isTrue();
        }
    }

    private static ScriptedAgent scriptedAgent(int maxIterations, Object... responses) {
        ScriptedAgent agent = new ScriptedAgent(List.of(responses));
        ReActAgentConfig config = new ReActAgentConfig().configureMaxIterations(maxIterations);
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        agent.configure(config);
        return agent;
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMap(ReActAgent agent, Object inputs) {
        return (Map<String, Object>) agent.invoke(inputs, new MemorySession())
                .toCompletableFuture()
                .join();
    }

    private static final class ScriptedAgent extends ReActAgent {
        private final List<Object> responses;
        private int callCount;

        private ScriptedAgent(List<Object> responses) {
            super(new AgentCard("logging-agent", "logging-agent", "Logging test agent"));
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            int index = Math.min(callCount++, responses.size() - 1);
            Object response = responses.get(index);
            if (response instanceof RuntimeException exception) {
                throw exception;
            }
            return response;
        }
    }

    private static final class EchoTool extends Tool {
        private EchoTool() {
            super(new ToolCard("echo-id", "echo", "Echo tool", Map.of()));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return inputs;
        }
    }

    private static final class AgentLogCapture extends Handler implements AutoCloseable {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        private AgentLogCapture() {
            Loggers.AGENT.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        private List<String> reactMessages() {
            return records.stream()
                    .map(LogRecord::getMessage)
                    .filter(message -> message.startsWith("ReAct"))
                    .toList();
        }

        private LogRecord record(String message) {
            return records.stream()
                    .filter(record -> message.equals(record.getMessage()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing log record: " + message));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            Loggers.AGENT.removeHandler(this);
        }
    }

    private static class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        @Override
        public String getSessionId() {
            return "iteration-logging-session";
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

    private static final class StreamFailureSession extends MemorySession {
        private final RuntimeException failure;

        private StreamFailureSession(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void writeStream(Object data) {
            throw failure;
        }
    }

    private static final class CleanupFailureSession extends MemorySession {
        private final RuntimeException failure;
        private boolean closeStreamCalled;
        private boolean commitCalled;

        private CleanupFailureSession(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void closeStream() {
            closeStreamCalled = true;
        }

        @Override
        public void commit() {
            commitCalled = true;
            throw failure;
        }
    }
}
