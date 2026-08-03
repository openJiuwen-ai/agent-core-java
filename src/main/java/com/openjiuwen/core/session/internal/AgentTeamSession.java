/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;

/**
 * Internal agent-team session.
 *
 * <p>Mirrors Python's {@code AgentTeamSession} in
 * {@code openjiuwen/core/session/internal/agent_team.py}.</p>
 */
public class AgentTeamSession extends BaseSession {

    private final String sessionId;
    private final String teamId;
    private final Config config;
    private final AgentStateCollection state = new AgentStateCollection();
    private final StreamWriterManager streamWriterManager;
    private final Tracer tracer = new Tracer();
    private final Checkpointer checkpointer;
    private final TraceAgentSpan teamSpan;

    public AgentTeamSession(String sessionId, String teamId) {
        this(sessionId, teamId, null, null, null);
    }

    public AgentTeamSession(String sessionId, String teamId, Config config) {
        this(sessionId, teamId, config, null, null);
    }

    public AgentTeamSession(String sessionId, String teamId, Config config, Checkpointer checkpointer) {
        this(sessionId, teamId, config, checkpointer, null);
    }

    public AgentTeamSession(String sessionId, String teamId, Config config, Checkpointer checkpointer,
                            StreamWriterManager streamWriterManager) {
        this.sessionId = sessionId;
        this.teamId = teamId;
        this.config = config;
        this.streamWriterManager = streamWriterManager == null
                ? new StreamWriterManager(new StreamEmitter())
                : streamWriterManager;
        tracer.init(this.streamWriterManager);
        this.checkpointer = checkpointer == null ? CheckpointerFactory.getCheckpointer() : checkpointer;
        this.teamSpan = tracer.getTracerAgentSpanManager().createAgentSpan();
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

    public TraceAgentSpan span() {
        return teamSpan;
    }

    public String teamId() {
        return teamId;
    }
}
