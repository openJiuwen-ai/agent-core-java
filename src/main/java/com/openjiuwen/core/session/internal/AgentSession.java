/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
     * @param sessionId    the unique session identifier
     * @param config       the session config (nullable)
     * @param checkpointer an explicit checkpointer, or null to use factory default
     * @param card         the agent card (nullable)
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card) {
        this(sessionId, config, checkpointer, card, null);
    }

    /**
     * Create a new AgentSession with explicit stream modes.
     *
     * @param sessionId    the unique session identifier
     * @param config       the session config (nullable)
     * @param checkpointer an explicit checkpointer, or null to use factory default
     * @param card         the agent card (nullable)
     * @param streamModes  explicit enabled stream modes, null to use defaults
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

        this.checkpointerField = checkpointer != null
                ? checkpointer
                : CheckpointerFactory.getCheckpointer();

        this.agentSpan = tracer.getTracerAgentSpanManager().createAgentSpan(null);
        this.card = card;
    }

    /**
     * Convenience constructor without card.
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer) {
        this(sessionId, config, checkpointer, null);
    }

    /**
     * Convenience constructor with defaults.
     */
    public AgentSession(String sessionId, Config config) {
        this(sessionId, config, null, null);
    }

    /**
     * Compatibility constructor for translated tests.
     */
    public AgentSession(String sessionId) {
        this(sessionId, new Config(), null, null);
    }

    @Override
    public Config config() {
        return configField;
    }

    @Override
    public State state() {
        return stateField;
    }

    @Override
    public Object tracer() {
        return tracerField;
    }

    /**
     * Get the tracer (typed).
     */
    public Tracer tracerTyped() {
        return tracerField;
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManagerField;
    }

    @Override
    public CallbackManager callbackManager() {
        return callbackManagerField;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public Object checkpointer() {
        return checkpointerField;
    }

    /**
     * Get the checkpointer (typed).
     */
    public Checkpointer checkpointerTyped() {
        return checkpointerField;
    }

    /**
     * Get the agent span for this session.
     */
    public TraceAgentSpan span() {
        return agentSpan;
    }

    /**
     * Create a workflow session from this agent session.
     * <p>
     * The workflow session shares the agent's global state.
     *
     * @return a new WorkflowSession
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
     */
    public String agentDescription() {
        if (card instanceof com.openjiuwen.core.common.schema.BaseCard baseCard) {
            return baseCard.getDescription();
        }
        return null;
    }
}
