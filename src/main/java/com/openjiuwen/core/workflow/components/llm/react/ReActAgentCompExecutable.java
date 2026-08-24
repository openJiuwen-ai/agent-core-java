/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code ReActAgentCompExecutable} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_executable.py}.
 */
public class ReActAgentCompExecutable extends ComponentExecutable<Object, Object> {
    private static final String WORKFLOW_AGENT_ID = "react_agent_workflow_executable";
    private static final String WORKFLOW_AGENT_NAME = "ReAct Agent Workflow Executable";
    private static final String WORKFLOW_AGENT_DESCRIPTION = "ReAct agent for workflow execution";

    private final ReActAgentCompConfig config;
    private final ReActAgent reactAgent;

    public ReActAgentCompExecutable(ReActAgentCompConfig config) {
        this(config, new ReActAgent(workflowAgentCard()));
    }

    ReActAgentCompExecutable(ReActAgentCompConfig config, ReActAgent reactAgent) {
        this.config = config;
        this.reactAgent = Objects.requireNonNull(reactAgent, "reactAgent");
        this.reactAgent.configure(config);
    }

    public ReActAgentCompConfig getConfig() {
        return config;
    }

    public AbilityManager getAbilityManager() {
        return reactAgent.getAbilityManager();
    }

    @Override
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        try {
            return reactAgent.invoke(inputs, adaptSession(session)).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            return executionError(exception);
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
        try {
            AgentSession agentSession = AgentSession.createAgentSession(
                    session == null ? null : session.getSessionId(),
                    null,
                    reactAgent.getCard()
            );
            Iterator<Object> chunks = reactAgent.stream(inputs, agentSession, List.of(StreamMode.OUTPUT));
            return new NormalizingStreamIterator(chunks);
        } catch (RuntimeException exception) {
            return List.<Object>of(streamingError(exception)).iterator();
        }
    }

    ReActAgent getReactAgent() {
        return reactAgent;
    }

    private static AgentCard workflowAgentCard() {
        return new AgentCard(WORKFLOW_AGENT_ID, WORKFLOW_AGENT_NAME, WORKFLOW_AGENT_DESCRIPTION);
    }

    private static AgentSessionApi adaptSession(BaseSession session) {
        if (session instanceof AgentSessionApi agentSessionApi) {
            return agentSessionApi;
        }
        return new WorkflowAgentSessionAdapter(session);
    }

    private static Object normalizeChunk(Object chunk) {
        if (!(chunk instanceof OutputSchema outputSchema)) {
            return chunk;
        }
        Object payload = outputSchema.getPayload();
        if ("llm_output".equals(outputSchema.getType()) && payload instanceof Map<?, ?> map
                && map.containsKey("content")) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("output", map.get("content"));
            return result;
        }
        return payload;
    }

    private static Map<String, Object> executionError(Throwable exception) {
        LinkedHashMap<String, Object> error = new LinkedHashMap<>();
        error.put("output", "Error in ReAct execution: " + exceptionMessage(exception));
        error.put("result_type", "error");
        return error;
    }

    private static Map<String, Object> streamingError(Throwable exception) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("output", "Error in ReAct streaming: " + exceptionMessage(exception));
        payload.put("result_type", "error");

        LinkedHashMap<String, Object> error = new LinkedHashMap<>();
        error.put("type", "error");
        error.put("payload", payload);
        return error;
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }

    /**
     * Mirrors Python's workflow-session handoff used by {@code ReActAgentCompExecutable} in
     * {@code openjiuwen/core/workflow/components/llm/react/react_executable.py}.
     */
    private static final class WorkflowAgentSessionAdapter implements AgentSessionApi {
        private final BaseSession session;

        private WorkflowAgentSessionAdapter(BaseSession session) {
            this.session = session;
        }

        @Override
        public String getSessionId() {
            return session == null ? null : session.getSessionId();
        }

        @Override
        public Object getState(String key) {
            return session == null ? null : session.getState(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            if (session != null) {
                session.updateState(data);
            }
        }

        @Override
        public void writeStream(Object data) {
            invokeSessionMethod("writeStream", Object.class, data);
            invokeSessionMethod("write_stream", Object.class, data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            Object iterator = invokeSessionMethod("streamIterator");
            if (iterator == null) {
                iterator = invokeSessionMethod("stream_iterator");
            }
            if (iterator instanceof Iterator<?> typedIterator) {
                return castIterator(typedIterator);
            }
            return List.<Object>of().iterator();
        }

        private Object invokeSessionMethod(String methodName) {
            if (session == null) {
                return null;
            }
            try {
                Method method = session.getClass().getMethod(methodName);
                return method.invoke(session);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private void invokeSessionMethod(String methodName, Class<?> parameterType, Object value) {
            if (session == null) {
                return;
            }
            try {
                Method method = session.getClass().getMethod(methodName, parameterType);
                method.invoke(session, value);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @SuppressWarnings("unchecked")
        private static Iterator<Object> castIterator(Iterator<?> iterator) {
            return (Iterator<Object>) iterator;
        }
    }

    /**
     * Mirrors Python's chunk normalization loop in
     * {@code openjiuwen/core/workflow/components/llm/react/react_executable.py}.
     */
    private static final class NormalizingStreamIterator implements Iterator<Object> {
        private final Iterator<Object> delegate;
        private Object pendingError;
        private boolean exhausted;

        private NormalizingStreamIterator(Iterator<Object> delegate) {
            this.delegate = delegate == null ? List.<Object>of().iterator() : delegate;
        }

        @Override
        public boolean hasNext() {
            if (pendingError != null) {
                return true;
            }
            if (exhausted) {
                return false;
            }
            try {
                return delegate.hasNext();
            } catch (RuntimeException exception) {
                pendingError = streamingError(exception);
                exhausted = true;
                return true;
            }
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (pendingError != null) {
                Object error = pendingError;
                pendingError = null;
                return error;
            }
            try {
                return normalizeChunk(delegate.next());
            } catch (RuntimeException exception) {
                exhausted = true;
                return streamingError(exception);
            }
        }
    }
}
