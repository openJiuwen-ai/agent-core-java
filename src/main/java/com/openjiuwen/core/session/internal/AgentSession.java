/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.InMemoryCommitState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal single-agent session.
 *
 * <p>Mirrors Python's {@code AgentSession} in
 * {@code openjiuwen/core/session/internal/agent.py}.</p>
 */
public class AgentSession extends BaseSession {

    private final String sessionId;
    private final Config config;
    private final Object card;
    private final AgentStateCollection state = new AgentStateCollection();
    private final StreamWriterManager streamWriterManager;
    private final Tracer tracer = new Tracer();
    private final Checkpointer checkpointer;
    private final TraceAgentSpan agentSpan;
    private final CallbackManager callbackManager = new CallbackManager();

    public AgentSession(String sessionId, Config config, Object card, StreamWriterManager streamWriterManager) {
        this(sessionId, config, null, card, streamWriterManager);
    }

    public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card,
                        StreamWriterManager streamWriterManager) {
        this.sessionId = sessionId;
        this.config = config == null ? new Config() : config;
        this.card = card;
        this.streamWriterManager = streamWriterManager == null
                ? new StreamWriterManager(new StreamEmitter())
                : streamWriterManager;
        tracer.init(this.streamWriterManager);
        this.checkpointer = checkpointer == null ? CheckpointerFactory.getCheckpointer() : checkpointer;
        this.agentSpan = tracer.getTracerAgentSpanManager().createAgentSpan();
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public Tracer tracer() {
        return tracer;
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManager;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public Checkpointer checkpointer() {
        return checkpointer;
    }

    @Override
    public CallbackManager callbackManager() {
        return callbackManager;
    }

    public TraceAgentSpan span() {
        return agentSpan;
    }

    public WorkflowSession createWorkflowSession() {
        WorkflowCommitState workflowState = new WorkflowCommitState(
                new InMemoryCommitState(),
                new InMemoryCommitState(state.getGlobalStateLike()),
                new InMemoryCommitState(),
                new InMemoryCommitState(),
                new HashMap<>(),
                "",
                State.DEFAULT_NODE_ID
        );
        return new WorkflowSession(null, this, sessionId, workflowState, (Object) null);
    }

    public String agentId() {
        Object agentConfig = config.getAgentConfig();
        if (agentConfig != null) {
            return stringOrNull(readIdProperty(agentConfig));
        }
        return stringOrNull(readIdProperty(card));
    }

    public Object card() {
        return card;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Object readIdProperty(Object target) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get("id");
        }
        Object getterValue = invokeZeroArg(target, "getId");
        if (getterValue != null) {
            return getterValue;
        }
        Object accessorValue = invokeZeroArg(target, "id");
        if (accessorValue != null) {
            return accessorValue;
        }
        return readField(target, "id");
    }

    private static Object invokeZeroArg(Object target, String methodName) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // Try the next superclass.
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Try the next superclass.
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return null;
            }
        }
        return null;
    }

}
