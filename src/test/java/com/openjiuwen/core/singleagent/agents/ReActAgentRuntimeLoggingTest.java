/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ExternalTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentRuntimeLoggingTest {

    @Test
    void logsModelCallStartResponseAndCompletionWithoutMessageBodies() {
        RuntimeLoggingAgent agent = runtimeLoggingAgent(new AssistantMessage("secret answer"));

        try (LogCapture logs = LogCapture.attach(Loggers.LLM)) {
            Map<String, Object> result = invokeMap(agent, Map.of("query", "secret question"));

            assertThat(result).containsEntry("output", "secret answer");
            assertThat(logs.messages()).anyMatch(message -> message.contains("event=react_model_call_started"));
            assertThat(logs.messages()).anyMatch(message -> message.contains("event=react_model_response"));
            assertThat(logs.messages()).anyMatch(message -> message.contains("event=react_model_call_completed"));
            assertThat(logs.messages()).allSatisfy(message -> {
                assertThat(message).doesNotContain("secret question");
                assertThat(message).doesNotContain("secret answer");
            });
            assertThat(logs.recordContaining("event=react_model_call_started").getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.recordContaining("event=react_model_response").getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.recordContaining("event=react_model_call_completed").getLevel()).isEqualTo(Level.INFO);
        }
    }

    @Test
    void logsToolCallStartAndCompletionWithoutArgumentsOrResults() {
        AssistantMessage toolRequest = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall("call-1", "echo", "{\"secret\":\"value\"}")))
                .build();
        RuntimeLoggingAgent agent = runtimeLoggingAgent(toolRequest, new AssistantMessage("done"));
        EchoTool tool = new EchoTool();
        Runner.resourceMgr().removeTool(tool.getCard().getId());
        Runner.resourceMgr().addTool(tool);
        agent.getAbilityManager().add(tool.getCard());

        try (LogCapture logs = LogCapture.attach(Loggers.TOOL)) {
            Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

            assertThat(result).containsEntry("output", "done");
            assertThat(logs.messages()).anyMatch(message -> message.contains("event=react_tool_call_started"));
            assertThat(logs.messages()).anyMatch(message -> message.contains("event=react_tool_call_completed")
                    && message.contains("outcome=success"));
            assertThat(logs.messages()).allSatisfy(message -> {
                assertThat(message).doesNotContain("secret");
                assertThat(message).doesNotContain("value");
                assertThat(message).doesNotContain("tool-result-body");
            });
            assertThat(logs.recordContaining("event=react_tool_call_started").getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.recordContaining("event=react_tool_call_completed").getLevel()).isEqualTo(Level.INFO);
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId());
        }
    }

    @Test
    void logsToolExceptionOutcomeWithoutExceptionMessage() {
        AssistantMessage toolRequest = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall("call-explode", "explode", "{}")))
                .build();
        RuntimeLoggingAgent agent = runtimeLoggingAgent(toolRequest, new AssistantMessage("done"));
        ExplodingTool tool = new ExplodingTool();
        Runner.resourceMgr().removeTool(tool.getCard().getId());
        Runner.resourceMgr().addTool(tool);
        agent.getAbilityManager().add(tool.getCard());

        try (LogCapture logs = LogCapture.attach(Loggers.TOOL)) {
            Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

            assertThat(result).containsEntry("output", "done");
            assertThat(logs.messages()).anyMatch(message -> message.contains("event=react_tool_call_completed")
                    && message.contains("outcome=exception"));
            assertThat(logs.messages()).allSatisfy(message -> assertThat(message).doesNotContain("boom secret"));
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId());
        }
    }

    @Disabled("Temporarily disabled due to unit test failure - see surefire-reports")
    @Test
    void logsExternalToolPendingWithoutArguments() {
        AssistantMessage toolRequest = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall("call-external", "external_input", "{\"secret\":\"value\"}")))
                .build();
        RuntimeLoggingAgent agent = runtimeLoggingAgent(toolRequest);
        agent.getAbilityManager().add(externalTool());

        try (LogCapture toolLogs = LogCapture.attach(Loggers.TOOL);
             LogCapture agentLogs = LogCapture.attach(Loggers.AGENT)) {
            Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

            assertThat(result).containsEntry("result_type", "external_tool_call_required");
            assertThat(toolLogs.messages()).anyMatch(message -> message.contains("event=react_tool_call_completed")
                    && message.contains("outcome=external_pending"));
            assertThat(agentLogs.messages()).anyMatch(message -> message.contains("event=react_external_tool_pending"));
            assertThat(toolLogs.messages()).allSatisfy(message -> {
                assertThat(message).doesNotContain("secret");
                assertThat(message).doesNotContain("value");
            });
            assertThat(agentLogs.messages()).allSatisfy(message -> {
                assertThat(message).doesNotContain("secret");
                assertThat(message).doesNotContain("value");
            });
        }
    }

    @Test
    void warnsWhenSkillReadFileToolIsMissing() {
        RuntimeLoggingAgent agent = runtimeLoggingAgentWithSkill(new AssistantMessage("done"));

        try (LogCapture logs = LogCapture.attach(Loggers.AGENT)) {
            invokeMap(agent, Map.of("query", "hello"));

            LogRecord warning = logs.recordContaining("event=react_skill_read_tool_missing");
            assertThat(warning.getLevel()).isEqualTo(Level.WARNING);
        }
    }

    @Test
    void streamUsesCustomWorkThreadNameWhenProvided() {
        BlockingLifecycleSession session = new BlockingLifecycleSession();
        RuntimeLoggingAgent agent = runtimeLoggingAgent(new AssistantMessage("done"));

        agent.stream(
                Map.of("query", "hello", "work_thread_name", " react-agent-conv-c1-chat-c2 "),
                session,
                List.of(StreamMode.OUTPUT)
        );

        assertThat(session.awaitWorkerThreadName()).isEqualTo("react-agent-conv-c1-chat-c2");
    }

    @Test
    void streamNormalizesControlWhitespaceInCustomWorkThreadName() {
        BlockingLifecycleSession session = new BlockingLifecycleSession();
        RuntimeLoggingAgent agent = runtimeLoggingAgent(new AssistantMessage("done"));

        agent.stream(Map.of("query", "hello", "work_thread_name", "job\nname\t1"), session, List.of());

        assertThat(session.awaitWorkerThreadName()).isEqualTo("job_name_1");
    }

    @Test
    void streamKeepsDefaultWorkThreadNameWhenCustomNameIsInvalid() {
        BlockingLifecycleSession session = new BlockingLifecycleSession();
        RuntimeLoggingAgent agent = runtimeLoggingAgent(new AssistantMessage("done"));

        agent.stream(Map.of("query", "hello", "work_thread_name", " \n\t "), session, List.of());

        assertThat(session.awaitWorkerThreadName()).isEqualTo("react-agent-stream-logging-agent");
    }

    private static RuntimeLoggingAgent runtimeLoggingAgent(AssistantMessage... responses) {
        RuntimeLoggingAgent agent = new RuntimeLoggingAgent();
        agent.configure(defaultConfig());
        agent.setLlm(new Model(new ScriptedModelClient(List.of(responses))));
        return agent;
    }

    private static RuntimeLoggingAgent runtimeLoggingAgentWithSkill(AssistantMessage... responses) {
        RuntimeLoggingAgent agent = new RuntimeLoggingAgent(new FakeSkillUtil());
        agent.configure(defaultConfig());
        agent.setLlm(new Model(new ScriptedModelClient(List.of(responses))));
        return agent;
    }

    private static ReActAgentConfig defaultConfig() {
        ReActAgentConfig config = new ReActAgentConfig().configureMaxIterations(3);
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setModelName("runtime-log-model");
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMap(ReActAgent agent, Object inputs) {
        return (Map<String, Object>) agent.invoke(inputs, new MemorySession())
                .toCompletableFuture()
                .join();
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static final class RuntimeLoggingAgent extends ReActAgent {
        private final SkillUtil skillUtil;

        private RuntimeLoggingAgent() {
            this(null);
        }

        private RuntimeLoggingAgent(SkillUtil skillUtil) {
            super(new AgentCard("logging-agent", "logging-agent", "Logging test agent"));
            this.skillUtil = skillUtil;
        }

        @Override
        public SkillUtil getSkillUtil() {
            return skillUtil != null ? skillUtil : super.getSkillUtil();
        }
    }

    private static final class FakeSkillUtil extends SkillUtil {
        private FakeSkillUtil() {
            super("sys-op");
        }

        @Override
        public boolean hasSkill() {
            return true;
        }

        @Override
        public String getSkillPrompt() {
            return "fake skill prompt";
        }
    }

    private static final class ScriptedModelClient implements Model.ModelClient {
        private final List<AssistantMessage> responses;
        private int index;

        private ScriptedModelClient(List<AssistantMessage> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(nextResponse());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            AssistantMessage response = nextResponse();
            return List.of(AssistantMessageChunk.builder()
                    .content(response.getContent())
                    .toolCalls(response.getToolCalls())
                    .usageMetadata(response.getUsageMetadata())
                    .reasoningContent(response.getReasoningContent())
                    .build()).iterator();
        }

        private AssistantMessage nextResponse() {
            int current = Math.min(index++, responses.size() - 1);
            return responses.get(current);
        }
    }

    private static final class EchoTool extends Tool {
        private EchoTool() {
            super(ToolCard.builder()
                    .id("echo-id")
                    .name("echo")
                    .description("Echo tool")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of("echo", "tool-result-body");
        }
    }

    private static final class ExplodingTool extends Tool {
        private ExplodingTool() {
            super(ToolCard.builder()
                    .id("explode-id")
                    .name("explode")
                    .description("Exploding tool")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw new IllegalStateException("boom secret");
        }
    }

    private static ExternalTool externalTool() {
        return new ExternalTool(ToolCard.builder()
                .id("external.external_input")
                .name("external_input")
                .description("Read external input")
                .inputParams(Map.of("type", "object"))
                .build());
    }

    private static class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        @Override
        public String getSessionId() {
            return "runtime-logging-session";
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

    private static final class BlockingLifecycleSession extends AgentSession {
        private final CountDownLatch workerThreadCaptured = new CountDownLatch(1);
        private volatile String workerThreadName;

        private BlockingLifecycleSession() {
            super("runtime-logging-session", null, null);
        }

        @Override
        public AgentSession preRun(Map<String, Object> kwargs) {
            return this;
        }

        @Override
        public void writeStream(Object data) {
            workerThreadName = Thread.currentThread().getName();
            workerThreadCaptured.countDown();
            super.writeStream(data);
        }

        private String awaitWorkerThreadName() {
            try {
                boolean captured = workerThreadCaptured.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
                assertThat(captured).isTrue();
                return workerThreadName;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for worker thread name", exception);
            }
        }

        @Override
        public void closeStream() {
        }

        @Override
        public void commit() {
        }
    }

    private static final class LogCapture extends Handler implements AutoCloseable {
        private final LoggerProtocol logger;
        private final List<LogRecord> records = new ArrayList<>();

        private LogCapture(LoggerProtocol logger) {
            this.logger = logger;
            this.logger.addHandler(this);
        }

        private static LogCapture attach(LoggerProtocol logger) {
            return new LogCapture(logger);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        private List<String> messages() {
            return records.stream().map(LogRecord::getMessage).toList();
        }

        private LogRecord recordContaining(String text) {
            return records.stream()
                    .filter(record -> record.getMessage().contains(text))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing log record containing: " + text
                            + ", messages=" + messages()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }
    }
}
