/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.application.llm.LlmController;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.agent.ControllerAgent;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM Agent - ReAct style Agent based on the controller architecture.
 *
 * <p>Mirrors Python's {@code LLMAgent} in
 * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.</p>
 */
public class LLMAgent extends ControllerAgent {
    private static final String DEFAULT_SESSION_ID = "default_session";
    private static final String CONVERSATION_ID = "conversation_id";
    private static final String QUERY = "query";
    private static final String USER_ID = "user_id";

    private final LegacyReActAgentConfig agentConfig;
    private final LlmController llmController;
    private final LongTermMemory longTermMemoryInstance;
    private final String memoryScopeId;
    private final boolean enableMemory;
    private final AgentMemoryConfig memoryAgentConfig;

    public LLMAgent(LegacyReActAgentConfig agentConfig) {
        super(Objects.requireNonNull(agentConfig, "agentConfig"), null);
        if (agentConfig.getControllerType() != ControllerType.REACT_CONTROLLER) {
            throw new UnsupportedOperationException(
                    "LLMAgent requires ReActController, got " + agentConfig.getControllerType());
        }
        this.agentConfig = agentConfig;
        this.longTermMemoryInstance = LongTermMemory.getInstance();
        this.memoryScopeId = agentConfig.getMemoryScopeId();
        this.memoryAgentConfig = agentConfig.getAgentMemoryConfig();
        this.enableMemory = hasText(memoryScopeId)
                && (memoryAgentConfig.isEnableLongTermMem() || !memoryAgentConfig.getMemVariables().isEmpty());
        this.llmController = new LlmController(agentConfig, getContextEngine());
        setController(llmController);
    }

    public LlmController getLlmController() {
        return llmController;
    }

    public LegacyReActAgentConfig getTypedAgentConfig() {
        return agentConfig;
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        Map<String, Object> safeInputs = copyInputs(inputs);
        AgentSession ownedSession = null;
        AgentSessionApi activeSession = session;
        if (activeSession == null) {
            ownedSession = createOwnedSession(sessionIdFrom(safeInputs));
            ownedSession.preRun(Map.of("inputs", safeInputs));
            activeSession = ownedSession;
        }

        try {
            getContextEngine().createContext(null, activeSession);
            Object result = invokeController(safeInputs, activeSession);
            if (ownedSession != null) {
                getContextEngine().saveContexts(ownedSession);
                ownedSession.closeStream();
                ownedSession.commit();
            }
            if (enableMemory) {
                scheduleMemoryWrite(safeInputs, result, session, "invoke_add_memory_task");
            }
            return CompletableFuture.completedFuture(result);
        } catch (RuntimeException error) {
            if (ownedSession != null) {
                ownedSession.closeStream();
                ownedSession.commit();
            }
            return failed(error);
        }
    }

    public CompletionStage<Object> invoke(Map<String, Object> inputs) {
        return invoke(inputs, null);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        Map<String, Object> safeInputs = copyInputs(inputs);
        if (session == null) {
            AgentSession ownedSession = createOwnedSession(sessionIdFrom(safeInputs));
            ownedSession.preRun(Map.of("inputs", safeInputs));
            return streamWithOwnedSession(safeInputs, ownedSession);
        }

        syncToolsToExternalSession();
        Object result = runStreamProcess(safeInputs, session, false);
        return iteratorForResult(result);
    }

    public Iterator<Object> stream(Map<String, Object> inputs) {
        return stream(inputs, null, List.of(StreamMode.OUTPUT));
    }

    public Iterator<Object> innerStream(Map<String, Object> inputs,
                                        AgentSessionApi session,
                                        boolean needCleanup,
                                        boolean ownStream) {
        if (ownStream) {
            return streamWithOwnedSession(copyInputs(inputs), requireAgentSession(session));
        }
        Object result = runStreamProcess(copyInputs(inputs), session, needCleanup);
        return iteratorForResult(result);
    }

    public Iterator<Object> _inner_stream(Map<String, Object> inputs,
                                          AgentSessionApi session,
                                          boolean needCleanup,
                                          boolean ownStream) {
        return innerStream(inputs, session, needCleanup, ownStream);
    }

    public void setPromptTemplate(List<Map<String, Object>> promptTemplate) {
        List<Map<String, Object>> copiedPrompt = copyPromptTemplate(promptTemplate);
        agentConfig.setPromptTemplate(copiedPrompt);
        getConfigWrapper().setAgentConfig(agentConfig);
        llmController.setLlmControllerPromptTemplate(copiedPrompt);
    }

    public void set_prompt_template(List<Map<String, Object>> promptTemplate) {
        setPromptTemplate(promptTemplate);
    }

    protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
        Event event = llmController.createMessage(inputs);
        return llmController.handleEvent(event, session);
    }

    protected AgentSession createOwnedSession(String sessionId) {
        return AgentSession.createAgentSession(
                sessionId,
                null,
                new AgentCard(agentConfig.getId(), agentConfig.getId(), agentConfig.getDescription()));
    }

    CompletionStage<Void> writeMessagesToMemory(Map<String, Object> inputs,
                                                Object result,
                                                AgentSessionApi session) {
        Map<String, Object> safeInputs = copyInputs(inputs);
        Object rawUserId = safeInputs.get(USER_ID);
        if (rawUserId == null || !hasText(String.valueOf(rawUserId)) || longTermMemoryInstance == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<BaseMessage> messages = new ArrayList<>();
        Object query = safeInputs.get(QUERY);
        if (query instanceof String text && !text.isEmpty()) {
            messages.add(new UserMessage(text));
        } else if (query != null && !(query instanceof String)) {
            Loggers.AGENT.warning("Unexpected query in write_messages_to_memory: {}", query);
            return CompletableFuture.completedFuture(null);
        }

        if (result != null) {
            AssistantMessage assistantMessage = convertResponseToMessage(result);
            if (assistantMessage != null && !assistantMessage.getContentAsString().isEmpty()) {
                messages.add(assistantMessage);
            }
        }

        String sessionId = session != null
                ? session.getSessionId()
                : String.valueOf(safeInputs.getOrDefault(CONVERSATION_ID, DEFAULT_SESSION_ID));
        try {
            longTermMemoryInstance.addMessages(
                    messages,
                    memoryAgentConfig,
                    String.valueOf(rawUserId),
                    memoryScopeId,
                    sessionId,
                    ZonedDateTime.now(),
                    true,
                    2
            ).toCompletableFuture().join();
        } catch (RuntimeException error) {
            Loggers.AGENT.error("Add memory failed: {}", error.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    CompletionStage<Void> _write_messages_to_memory(Map<String, Object> inputs,
                                                    Object result,
                                                    AgentSessionApi session) {
        return writeMessagesToMemory(inputs, result, session);
    }

    static String extractAnswerOutput(Object result) {
        Object payload = readPayload(result);
        if (!(payload instanceof Map<?, ?> map)) {
            return "";
        }
        Object resultType = map.get("result_type");
        Object output = map.get("output");
        if ("answer".equals(resultType) && output instanceof String text) {
            return text;
        }
        return "";
    }

    static String _extract_answer_output(Object result) {
        return extractAnswerOutput(result);
    }

    static AssistantMessage convertResponseToMessage(Object result) {
        if (result instanceof OutputSchema outputSchema
                && "answer".equals(outputSchema.getType())
                && outputSchema.getPayload() instanceof Map<?, ?> payload) {
            Object response = payload.get("output");
            if (response instanceof String text && !text.isEmpty()) {
                return new AssistantMessage(text);
            }
            return null;
        }
        if (result instanceof Map<?, ?> map
                && "answer".equals(map.get("result_type"))
                && map.get("output") instanceof String text) {
            return new AssistantMessage(text);
        }
        if (result instanceof String text) {
            return new AssistantMessage(text);
        }
        return null;
    }

    static AssistantMessage _convert_response_to_message(Object result) {
        return convertResponseToMessage(result);
    }

    private Iterator<Object> streamWithOwnedSession(Map<String, Object> inputs, AgentSession ownedSession) {
        AtomicReference<Object> finalResultHolder = new AtomicReference<>();
        CompletableFuture<Object> task = CompletableFuture.supplyAsync(() -> {
            Object result = runStreamProcess(inputs, ownedSession, true);
            finalResultHolder.set(result);
            return result;
        });
        return new OwnedStreamIterator(ownedSession.streamIterator(), task, finalResultHolder);
    }

    private Object runStreamProcess(Map<String, Object> inputs, AgentSessionApi session, boolean needCleanup) {
        try {
            getContextEngine().createContext(null, session);
            Object result = invokeController(inputs, session);
            if (enableMemory) {
                scheduleMemoryWrite(inputs, result, session, "stream_add_memory_task");
            }
            return result;
        } finally {
            if (needCleanup && session instanceof AgentSession agentSession) {
                agentSession.closeStream();
                agentSession.commit();
            }
        }
    }

    private void scheduleMemoryWrite(Map<String, Object> inputs,
                                     Object result,
                                     AgentSessionApi session,
                                     String taskName) {
        CompletableFuture.runAsync(() -> writeMessagesToMemory(inputs, result, session).toCompletableFuture().join())
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        Loggers.AGENT.info("add memory task [{}] completed successfully", taskName);
                    } else {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause()
                                : error;
                        Loggers.AGENT.exception("add memory task [" + taskName + "] failed", cause);
                    }
                });
    }

    private void syncToolsToExternalSession() {
        List<Object> tools = getTools();
        if (tools.isEmpty()) {
            return;
        }
        for (Object tool : tools) {
            if (tool instanceof Tool typedTool) {
                try {
                    Runner.resourceMgr().addTool(typedTool, List.of(agentConfig.getId()), true);
                } catch (RuntimeException error) {
                    Loggers.AGENT.warning("Failed to register tool {} for LLMAgent external session: {}",
                            typedTool.getCard().getName(), error.getMessage());
                }
            }
        }
    }

    private static Iterator<Object> iteratorForResult(Object result) {
        if (result instanceof Iterator<?> iterator) {
            @SuppressWarnings("unchecked")
            Iterator<Object> typed = (Iterator<Object>) iterator;
            return typed;
        }
        if (result instanceof Iterable<?> iterable && !(result instanceof Map<?, ?>)) {
            @SuppressWarnings("unchecked")
            Iterator<Object> typed = (Iterator<Object>) iterable.iterator();
            return typed;
        }
        return Collections.singletonList(result).iterator();
    }

    private static AgentSession requireAgentSession(AgentSessionApi session) {
        if (session instanceof AgentSession agentSession) {
            return agentSession;
        }
        throw new IllegalArgumentException("ownStream requires AgentSession");
    }

    private static Map<String, Object> copyInputs(Map<String, Object> inputs) {
        return inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
    }

    private static String sessionIdFrom(Map<String, Object> inputs) {
        Object value = inputs.get(CONVERSATION_ID);
        return value == null ? DEFAULT_SESSION_ID : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static Object readPayload(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof OutputSchema outputSchema) {
            return outputSchema.getPayload();
        }
        try {
            Method method = result.getClass().getMethod("getPayload");
            return method.invoke(result);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static List<Map<String, Object>> copyPromptTemplate(List<Map<String, Object>> promptTemplate) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (promptTemplate == null) {
            return copy;
        }
        for (Map<String, Object> item : promptTemplate) {
            copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
        }
        return copy;
    }

    /**
     * Mirrors Python's owned stream iterator branch in
     * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.
     */
    private static final class OwnedStreamIterator implements Iterator<Object> {
        private final Iterator<Object> delegate;
        private final CompletableFuture<Object> task;
        private final AtomicReference<Object> finalResultHolder;
        private boolean taskAwaited;

        private OwnedStreamIterator(Iterator<Object> delegate,
                                    CompletableFuture<Object> task,
                                    AtomicReference<Object> finalResultHolder) {
            this.delegate = delegate;
            this.task = task;
            this.finalResultHolder = finalResultHolder;
        }

        @Override
        public boolean hasNext() {
            try {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    awaitTask();
                }
                return hasNext;
            } catch (RuntimeException error) {
                drainAfterIteratorFailure();
                awaitTask();
                throw error;
            }
        }

        @Override
        public Object next() {
            try {
                return delegate.next();
            } catch (RuntimeException error) {
                drainAfterIteratorFailure();
                awaitTask();
                throw error;
            }
        }

        private void drainAfterIteratorFailure() {
            try {
                while (delegate.hasNext()) {
                    delegate.next();
                    Loggers.AGENT.debug("Consuming remaining stream message after exception");
                }
            } catch (RuntimeException drainError) {
                Loggers.AGENT.debug("Error while consuming remaining stream messages: {}", drainError.getMessage());
            }
        }

        private void awaitTask() {
            if (taskAwaited) {
                return;
            }
            taskAwaited = true;
            task.join();
            finalResultHolder.get();
        }
    }
}
