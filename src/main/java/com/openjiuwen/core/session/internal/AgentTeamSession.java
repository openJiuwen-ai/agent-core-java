/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session implementation for agent teams, providing full session lifecycle
 * for team-oriented agent execution.
 * <p>
 * Mirrors Python's {@code AgentTeamSession} in
 * {@code session/internal/agent_team.py}.
 * <p>
 * Analogous to {@link AgentSession} but scoped to team execution with a
 * {@code teamId} instead of an individual agent card.
 */
public class AgentTeamSession extends BaseSession {

    private final String sessionId;
    private final String teamId;
    private final Config configField;
    private final AgentStateCollection stateField;
    private final StreamWriterManager streamWriterManagerField;
    private final Tracer tracerField;
    private final Checkpointer checkpointerField;
    private final TraceAgentSpan teamSpan;
    private boolean preRunDone;
    private boolean postRunDone;
    private int streamIndex;

    /**
     * Create a new AgentTeamSession.
     *
     * @param sessionId the unique session identifier
     * @param teamId    the team identifier
     * @param config    the session config (nullable)
     * @param checkpointer an explicit checkpointer, or null to use factory default
     */
    public AgentTeamSession(String sessionId, String teamId, Config config,
                            Checkpointer checkpointer) {
        this(sessionId, teamId, config, checkpointer, null);
    }
    
    /**
     * Create a new AgentTeamSession with defaults.
     *
     * @param sessionId the unique session identifier
     * @param teamId    the team identifier
     */
    public AgentTeamSession(String sessionId, String teamId) {
        this(sessionId, teamId, null, null, null);
    }

    /**
     * Create a new AgentTeamSession with an explicit stream writer manager.
     *
     * @param sessionId    the unique session identifier
     * @param teamId       the team identifier
     * @param config       the session config (nullable)
     * @param checkpointer an explicit checkpointer, or null to use factory default
     * @param streamWriterManager explicit stream writer manager, or null to create default
     */
    public AgentTeamSession(String sessionId, String teamId, Config config,
                            Checkpointer checkpointer,
                            StreamWriterManager streamWriterManager) {
        this.sessionId = sessionId;
        this.teamId = teamId;
        this.configField = config;
        this.stateField = new AgentStateCollection();
        this.streamWriterManagerField = streamWriterManager != null
                ? streamWriterManager
                : new StreamWriterManager(new StreamEmitter());
        Tracer tracer = new Tracer();
        tracer.init(this.streamWriterManagerField, new CallbackManager());
        this.tracerField = tracer;
        this.checkpointerField = checkpointer != null
                ? checkpointer
                : CheckpointerFactory.getCheckpointer();
        this.teamSpan = tracerField != null
                ? tracerField.getTracerAgentSpanManager().createAgentSpan(null)
                : null;
        this.preRunDone = false;
        this.postRunDone = false;
        this.streamIndex = 0;
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

    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManagerField;
    }

    @Override
    public CallbackManager callbackManager() {
        // Python 0.1.12 AgentTeamSession has no callback manager.
        // Return a fresh instance for Java API compatibility.
        return new CallbackManager();
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
     * Get the team span for tracing.
     *
     * @return the trace agent span for this team session
     */
    public TraceAgentSpan span() {
        return teamSpan;
    }

    /**
     * Execute team-session pre-run checkpointer logic once.
     *
     * @param inputs invocation inputs
     */
    public void preRun(Object inputs) {
        if (preRunDone) {
            return;
        }
        CheckpointerFactory.getCheckpointer().preAgentExecute(this, inputs);
        preRunDone = true;
    }

    /**
     * Close the team stream and execute post-run checkpointer logic once.
     */
    public void postRun() {
        if (postRunDone) {
            return;
        }
        streamWriterManagerField.getStreamEmitter().close();
        if (checkpointerField != null) {
            checkpointerField.postAgentExecute(this);
        }
        postRunDone = true;
    }

    @Override
    public Object getState(String key) {
        return stateField.getGlobal(key);
    }

    @Override
    public void updateState(Map<String, Object> stateMap) {
        if (stateMap != null) {
            stateField.updateGlobal(stateMap);
        }
    }

    /**
     * Get the team identifier.
     *
     * @return the team ID
     */
    public String teamId() {
        return teamId;
    }

    /**
     * Create a child agent session whose stream output is forwarded to the
     * team stream with source tags.
     *
     * <p>Mirrors Python's {@code AgentTeamSession.create_agent_session}.</p>
     */
    public AgentSessionApi createAgentSession(String agentId) {
        String childId = sessionId + ":" + (agentId != null ? agentId : "agent");
        return new ChildAgentSession(childId, agentId);
    }

    /**
     * Write a team-level stream item with the team source tag attached.
     */
    @Override
    public void writeStream(Object data) {
        streamWriterManagerField.getOutputWriter().write(new OutputSchema(
                "message",
                ++streamIndex,
                withSourceTags(data, null)
        ));
    }

    /**
     * Return a blocking iterator over team stream items.
     */
    public Iterator<Object> streamIterator() {
        return streamWriterManagerField.streamIterator();
    }

    private void writeChildStream(String agentId, Object data) {
        streamWriterManagerField.getOutputWriter().write(new OutputSchema(
                "message",
                ++streamIndex,
                withSourceTags(data, agentId)
        ));
    }

    @SuppressWarnings("unchecked")
    private Object withSourceTags(Object data, String agentId) {
        Map<String, Object> tagged = new LinkedHashMap<>();
        if (data instanceof Map<?, ?> map) {
            map.forEach((key, value) -> tagged.put(String.valueOf(key), value));
        } else {
            tagged.put("payload", data);
        }
        tagged.put("source_team_id", teamId);
        if (agentId != null && !agentId.isBlank()) {
            tagged.put("source_agent_id", agentId);
        }
        return tagged;
    }

    private final class ChildAgentSession extends AgentSessionApi {
        private final String agentId;

        private ChildAgentSession(String childSessionId, String agentId) {
            super(childSessionId, null, null);
            this.agentId = agentId;
        }

        @Override
        public void writeStream(Object data) {
            writeChildStream(agentId, data);
            super.writeStream(data);
        }
    }
}
