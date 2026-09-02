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
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.List;
import java.util.Map;

/**
 * Agent session providing full session lifecycle for an agent execution.
 * <p>
 * Manages configuration, state (AgentStateCollection), stream emitter/writer,
 * callback manager, tracer, and checkpointer.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.agent.AgentSession}.
 * 
 * @since 0.1.7
 */
public class AgentSession extends BaseSession {
    private final String sessionId;
    private final Config configField;
    private final AgentStateCollection stateField;
    private final StreamWriterManager streamWriterManagerField;
    private final CallbackManager callbackManagerField;
    private final Tracer tracerField;
    private final Checkpointer checkpointerField;
    private final TraceAgentSpan agentSpan;
    private final Object card;

    /**
     * Create a new AgentSession.
     * 
     * @param sessionId the unique session identifier
     * @param config the session config (nullable)
     * @param checkpointer an explicit checkpointer, or null to use factory default
     * @param card the agent card (nullable)
     * @since 0.1.7
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card) {
        this(sessionId, config, checkpointer, card, null);
    }

    /**
     * Create a new AgentSession with explicit stream modes.
     * 
     * @param sessionId the unique session identifier
     * @param config the session config (nullable)
     * @param checkpointer an explicit checkpointer, or null to use factory default
     * @param card the agent card (nullable)
     * @param streamModes explicit enabled stream modes, null to use defaults
     * @since 0.1.7
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card,
            List<StreamMode> streamModes) {
        this.sessionId = sessionId;
        this.configField = config;
        this.stateField = new AgentStateCollection();
        this.streamWriterManagerField = new StreamWriterManager(new StreamEmitter(), streamModes);
        this.callbackManagerField = new CallbackManager();

        Tracer tracer = new Tracer();
        tracer.init(this.streamWriterManagerField, this.callbackManagerField);
        this.tracerField = tracer;

        this.checkpointerField = checkpointer != null ? checkpointer : CheckpointerFactory.getCheckpointer();

        this.agentSpan = tracer.getTracerAgentSpanManager().createAgentSpan(null);
        this.card = card;
    }

    /**
     * Convenience constructor without card.
     * 
     * @param sessionId sessionId
     * @param config config
     * @param checkpointer checkpointer
     * @since 0.1.7
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer) {
        this(sessionId, config, checkpointer, null);
    }

    /**
     * Convenience constructor with defaults.
     * 
     * @param sessionId sessionId
     * @param config config
     * @since 0.1.7
     */
    public AgentSession(String sessionId, Config config) {
        this(sessionId, config, null, null);
    }

    /**
     * Compatibility constructor for translated tests.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public AgentSession(String sessionId) {
        this(sessionId, new Config(), null, null);
    }

    /**
     * config.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Config config() {
        return configField;
    }

    /**
     * state.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public State state() {
        return stateField;
    }

    /**
     * tracer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object tracer() {
        return tracerField;
    }

    /**
     * Get the tracer (typed).
     * 
     * @return the result
     * @since 0.1.7
     */
    public Tracer tracerTyped() {
        return tracerField;
    }

    /**
     * streamWriterManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManagerField;
    }

    /**
     * callbackManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public CallbackManager callbackManager() {
        return callbackManagerField;
    }

    /**
     * sessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String sessionId() {
        return sessionId;
    }

    /**
     * checkpointer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object checkpointer() {
        return checkpointerField;
    }

    /**
     * Get the checkpointer (typed).
     * 
     * @return the result
     * @since 0.1.7
     */
    public Checkpointer checkpointerTyped() {
        return checkpointerField;
    }

    /**
     * Get the agent span for this session.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TraceAgentSpan span() {
        return agentSpan;
    }

    /**
     * createWorkflowSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public WorkflowSession createWorkflowSession() {
        Map<String, Object> globalData = (Map<String, Object>) stateField.getGlobal(null);
        WorkflowCommitState workflowState = InMemoryState.create(null, globalData, null, null, null);
        return new WorkflowSession(null, this, this.sessionId, workflowState, null);
    }

    /**
     * Get the agent ID from config or card.
     * 
     * @return agent ID
     * @since 0.1.7
     */
    public String agentId() {
        if (configField != null) {
            Object agentConfig = configField.getAgentConfig();
            if (agentConfig instanceof Config.MetadataLike meta && meta.getId() != null) {
                return meta.getId();
            }
        }
        // Fallback to card
        if (card instanceof com.openjiuwen.core.common.schema.BaseCard baseCard) {
            return baseCard.getId();
        }
        return null;
    }

    /**
     * Get the agent name from the card.
     * 
     * @return agent name or null
     * @since 0.1.7
     */
    public String agentName() {
        if (card instanceof com.openjiuwen.core.common.schema.BaseCard baseCard) {
            return baseCard.getName();
        }
        return null;
    }

    /**
     * Get the agent description from the card.
     * 
     * @return agent description or null
     * @since 0.1.7
     */
    public String agentDescription() {
        if (card instanceof com.openjiuwen.core.common.schema.BaseCard baseCard) {
            return baseCard.getDescription();
        }
        return null;
    }
}
