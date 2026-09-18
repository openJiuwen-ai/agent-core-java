/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.agent;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Legacy controller-backed single-agent facade.
 *
 * <p>Mirrors Python's {@code ControllerAgent} in
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
public class ControllerAgent extends BaseAgent {
    private Object controller;

    public ControllerAgent(Object agentConfig) {
        this(agentConfig, null);
    }

    public ControllerAgent(Object agentConfig, Object controller) {
        super(agentConfig);
        this.controller = controller;
        setupController();
    }

    public Object getController() {
        return controller;
    }

    public void setController(Object controller) {
        this.controller = controller;
        setupController();
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        if (controller == null) {
            return failed(new RuntimeException(getClass().getSimpleName()
                    + " has no controller, subclass should create controller before invocation"));
        }
        AgentSessionApi agentSession = session == null ? createSession(inputs) : session;
        getContextEngine().createContext(null, agentSession);
        try {
            Object result = invokeCompatible(controller, "invoke", inputs, agentSession);
            if (result == NoMethod.INSTANCE) {
                return failed(new IllegalStateException("controller lacks invoke method"));
            }
            return toStage(result).whenComplete((value, error) -> {
                if (session == null && agentSession instanceof AgentSession created) {
                    getContextEngine().saveContexts(created);
                    created.closeStream();
                    created.commit();
                }
            });
        } catch (RuntimeException error) {
            if (session == null && agentSession instanceof AgentSession created) {
                created.closeStream();
                created.commit();
            }
            return failed(error);
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        if (controller == null) {
            throw new RuntimeException(getClass().getSimpleName()
                    + " has no controller, subclass should create controller before invocation");
        }
        AgentSessionApi agentSession = session == null ? createSession(inputs) : session;
        getContextEngine().createContext(null, agentSession);
        Object result = invokeCompatible(controller, "stream", inputs, agentSession, streamModes);
        if (result == NoMethod.INSTANCE) {
            result = invokeCompatible(controller, "stream", inputs, agentSession);
        }
        if (result == NoMethod.INSTANCE) {
            result = invoke(inputs, agentSession).toCompletableFuture().join();
        }
        if (result instanceof Iterator<?> iterator) {
            @SuppressWarnings("unchecked")
            Iterator<Object> typed = (Iterator<Object>) iterator;
            return typed;
        }
        if (result instanceof Iterable<?> iterable) {
            @SuppressWarnings("unchecked")
            Iterator<Object> typed = (Iterator<Object>) iterable.iterator();
            return typed;
        }
        if (isAnswerResult(result)) {
            return List.of((Object) new OutputSchema("answer", 0, result)).iterator();
        }
        return List.of(result).iterator();
    }

    @Override
    public CompletionStage<Void> clearSession(String sessionId) {
        return super.clearSession(sessionId).thenRun(() -> {
            if (controller != null) {
                invokeCompatible(controller, "cleanupConversation", sessionId);
                invokeCompatible(controller, "cleanup_conversation", sessionId);
            }
        });
    }

    private void setupController() {
        if (controller == null) {
            return;
        }
        Object result = invokeCompatible(controller, "setupFromAgent", this);
        if (result == NoMethod.INSTANCE) {
            invokeCompatible(controller, "setup_from_agent", this);
        }
    }

    private AgentSession createSession(Map<String, Object> inputs) {
        String sessionId = inputs != null && inputs.get("conversation_id") != null
                ? String.valueOf(inputs.get("conversation_id"))
                : "default_session";
        AgentCard card = new AgentCard(readAgentConfigId(), readAgentConfigId(), readAgentConfigDescription());
        AgentSession session = AgentSession.createAgentSession(sessionId, null, card);
        session.preRun(Map.of("inputs", inputs == null ? Map.of() : inputs));
        return session;
    }

    private String readAgentConfigId() {
        Object value = invokeCompatible(getAgentConfig(), "getId");
        if (value == NoMethod.INSTANCE) {
            value = invokeCompatible(getAgentConfig(), "id");
        }
        return value == NoMethod.INSTANCE || value == null ? "" : String.valueOf(value);
    }

    private String readAgentConfigDescription() {
        Object value = invokeCompatible(getAgentConfig(), "getDescription");
        if (value == NoMethod.INSTANCE) {
            value = invokeCompatible(getAgentConfig(), "description");
        }
        return value == NoMethod.INSTANCE || value == null ? "" : String.valueOf(value);
    }

    private static CompletionStage<Object> toStage(Object result) {
        if (result instanceof CompletionStage<?> stage) {
            return stage.thenApply(value -> value);
        }
        return CompletableFuture.completedFuture(result);
    }

    private static boolean isAnswerResult(Object result) {
        return result instanceof Map<?, ?> map && "answer".equals(map.get("result_type"));
    }
}
