/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures for FEAT-057 run-budget tests: scripted model double, recording logger
 * binding and echo-tool registration. Helpers stay package-private; test classes carry the
 * scenario javadoc.
 */
final class RunBudgetTestFixture {
    private RunBudgetTestFixture() {
    }

    static ReActAgent newAgent(String agentId, int maxIterations) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(agentId).name(agentId).description("run budget test agent").build());
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(maxIterations);
        agent.configure(config);
        return agent;
    }

    static ReActAgentConfig configOf(ReActAgent agent) {
        Object config = agent.getConfig();
        if (config instanceof ReActAgentConfig reactConfig) {
            return reactConfig;
        }
        throw new IllegalStateException("unexpected config type: " + (config == null ? "null" : config.getClass()));
    }

    static RunBudgetRail registerBudgetRail(ReActAgent agent, RunBudgetConfig config) {
        RunBudgetRail rail = new RunBudgetRail(config);
        agent.registerRail(rail).toCompletableFuture().join();
        return rail;
    }

    static void unregisterRail(ReActAgent agent, RunBudgetRail rail) {
        if (agent != null && rail != null) {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    static AgentSessionApi newSession(String sessionId, ReActAgent agent) {
        return AgentSession.createAgentSession(sessionId, null, agent.getCard());
    }

    static AssistantMessage answer(String content) {
        return AssistantMessage.builder().content(content).toolCalls(List.of()).build();
    }

    static AssistantMessage toolCall(String content, String toolId, int callSeq) {
        return AssistantMessage.builder()
                .content(content)
                .toolCalls(List.of(ToolCall.builder()
                        .id("tc-" + callSeq).name(toolId).arguments("{}").build()))
                .build();
    }

    static ScriptedModel scriptedModel(List<AssistantMessage> script, AssistantMessage tail) {
        return new ScriptedModel(script, tail, 0L);
    }

    static ScriptedModel scriptedModel(List<AssistantMessage> script, AssistantMessage tail, long latencyMillis) {
        return new ScriptedModel(script, tail, latencyMillis);
    }

    static String registerEchoTool(ReActAgent agent, String prefix, List<String> cleanup,
                                   long latencyMillis, AtomicInteger callCount) {
        String toolId = prefix + "-" + UUID.randomUUID();
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("echo tool").inputParams(Map.of(
                        "type", "object", "properties", Map.of(), "required", List.of())).build(),
                inputs -> {
                    callCount.incrementAndGet();
                    if (latencyMillis > 0) {
                        LockSupport.parkNanos(latencyMillis * 1_000_000L);
                    }
                    return "echo:" + toolId;
                });
        Runner.resourceMgr().addTool(tool, null);
        cleanup.add(toolId);
        agent.getAbilityManager().add(tool.getCard());
        return toolId;
    }

    static void cleanupTools(List<String> toolIds) {
        for (String toolId : toolIds) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
        toolIds.clear();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> runSync(ReActAgent agent, Object inputs, AgentSessionApi session) {
        Object result = agent.invoke(inputs, session);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> runStreaming(ReActAgent agent, Object inputs, AgentSessionApi session) {
        Object result = agent.invoke(inputs, session, Map.of("_streaming", true));
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * Scripted {@link Model} double backed by Mockito, recording messages and options per call.
     */
    static final class ScriptedModel {
        private final Queue<AssistantMessage> script;
        private final AssistantMessage tail;
        private final long latencyMillis;
        private final AtomicInteger invokeCount = new AtomicInteger();
        private final List<List<BaseMessage>> messagesLog = new CopyOnWriteArrayList<>();
        private final List<ModelInvokeOptions> optionsLog = new CopyOnWriteArrayList<>();
        private Model model;

        private ScriptedModel(List<AssistantMessage> script, AssistantMessage tail, long latencyMillis) {
            this.script = new ConcurrentLinkedQueue<>(script);
            this.tail = tail;
            this.latencyMillis = latencyMillis;
            this.model = buildMock();
        }

        private Model buildMock() {
            Model mocked = mock(Model.class);
            when(mocked.supportsKvCacheRelease()).thenReturn(false);
            when(mocked.buildKvCacheInvokeKwargs(any(), anyBoolean())).thenReturn(Map.of());
            when(mocked.invoke(anyList(), any(ModelInvokeOptions.class))).thenAnswer(this::answerInvoke);
            when(mocked.stream(anyList(), any(ModelInvokeOptions.class))).thenAnswer(this::answerStream);
            return mocked;
        }

        private AssistantMessage nextResponse() {
            AssistantMessage next = script.poll();
            return next != null ? next : tail;
        }

        private CompletionStage<AssistantMessage> answerInvoke(InvocationOnMock invocation) {
            recordCall(invocation);
            return CompletableFuture.completedFuture(nextResponse());
        }

        private Iterator<AssistantMessageChunk> answerStream(InvocationOnMock invocation) {
            recordCall(invocation);
            AssistantMessage next = nextResponse();
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(next.getContent() == null ? "" : next.getContent())
                    .toolCalls(next.getToolCalls() == null ? List.of() : next.getToolCalls())
                    .build();
            return List.of(chunk).iterator();
        }

        private void recordCall(InvocationOnMock invocation) {
            List<BaseMessage> messages = invocation.getArgument(0);
            messagesLog.add(new ArrayList<>(messages));
            optionsLog.add(invocation.getArgument(1));
            invokeCount.incrementAndGet();
            if (latencyMillis > 0) {
                LockSupport.parkNanos(latencyMillis * 1_000_000L);
            }
        }

        Model model() {
            return model;
        }

        int invokeCount() {
            return invokeCount.get();
        }

        Optional<ModelInvokeOptions> lastOptions() {
            if (optionsLog.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(optionsLog.get(optionsLog.size() - 1));
        }

        boolean sawSteeringContaining(String fragment) {
            for (List<BaseMessage> messages : messagesLog) {
                for (BaseMessage message : messages) {
                    if (message instanceof UserMessage userMessage
                            && String.valueOf(userMessage.getContent()).contains(fragment)) {
                        return true;
                    }
                }
            }
            return false;
        }

        long steeringMessageCount(String fragment) {
            long count = 0;
            for (List<BaseMessage> messages : messagesLog) {
                boolean seenInCall = false;
                for (BaseMessage message : messages) {
                    if (!seenInCall && message instanceof UserMessage userMessage
                            && String.valueOf(userMessage.getContent()).contains(fragment)) {
                        seenInCall = true;
                        count++;
                    }
                }
            }
            return count;
        }
    }

    /**
     * Recording logger binding that captures budget events emitted through the degraded
     * (non-DefaultLogger) facade path; restores the original logger on close.
     */
    static final class RecordingEvents implements AutoCloseable {
        private final LoggerProtocol original;
        private final List<String> types = new CopyOnWriteArrayList<>();
        private final List<Map<String, Object>> payloads = new CopyOnWriteArrayList<>();

        private RecordingEvents(LoggerProtocol original) {
            this.original = original;
        }

        static RecordingEvents install() {
            LoggerProtocol original = LogManager.getLogger("agent");
            LoggerProtocol recording = mock(LoggerProtocol.class);
            RecordingEvents holder = new RecordingEvents(original);
            doAnswer(holder::recordInfo).when(recording).info(anyString(), Mockito.any(Object[].class));
            LogManager.registerLogger("agent", recording);
            return holder;
        }

        private Object recordInfo(InvocationOnMock invocation) {
            Object[] arguments = invocation.getArguments();
            Object last = arguments.length == 0 ? null : arguments[arguments.length - 1];
            if (arguments.length >= 3 && last instanceof Map<?, ?> map) {
                types.add(String.valueOf(arguments[arguments.length - 2]));
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                payloads.add(copy);
            }
            return null;
        }

        List<Map<String, Object>> payloadsOf(String eventTypeValue) {
            List<Map<String, Object>> matched = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) {
                if (eventTypeValue.equals(types.get(i))) {
                    matched.add(payloads.get(i));
                }
            }
            return matched;
        }

        long countOf(String eventTypeValue) {
            return types.stream().filter(eventTypeValue::equals).count();
        }

        List<String> types() {
            return new ArrayList<>(types);
        }

        @Override
        public void close() {
            LogManager.registerLogger("agent", original);
        }
    }
}
