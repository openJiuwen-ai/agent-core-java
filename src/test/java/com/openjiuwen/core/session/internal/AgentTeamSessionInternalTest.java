/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for the internal agent-team session.
 *
 * <p>Mirrors Python's {@code AgentTeamSession} in
 * {@code openjiuwen/core/session/internal/agent_team.py}.</p>
 */
class AgentTeamSessionInternalTest {

    @AfterEach
    void resetCheckpointerFactory() {
        CheckpointerFactory.releaseDefaultCheckpointer();
    }

    @Test
    void constructorRetainsExplicitCheckpointerAndCreatesDefaultCollaborators() {
        Config config = new Config();
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();

        AgentTeamSession session = new AgentTeamSession(
                "session-explicit",
                "team-a",
                config,
                checkpointer,
                null
        );

        assertSame(checkpointer, session.checkpointer());
        assertSame(config, session.config());
        assertInstanceOf(AgentStateCollection.class, session.state());
        assertNotNull(session.streamWriterManager());
        assertNotNull(session.tracer());
        assertNotNull(session.span());
        assertEquals("session-explicit", session.sessionId());
        assertEquals("team-a", session.teamId());
    }

    @Test
    void constructorCachesFactoryCheckpointerAtCreationTime() {
        RecordingCheckpointer first = new RecordingCheckpointer();
        RecordingCheckpointer second = new RecordingCheckpointer();
        installDefaultCheckpointer("unit-internal-team-first", first);

        AgentTeamSession session = new AgentTeamSession("session-default", "team-a", new Config());
        installDefaultCheckpointer("unit-internal-team-second", second);

        assertSame(first, session.checkpointer());
    }

    @Test
    void constructorRetainsExplicitStreamWriterManagerAndNullableValues() {
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();
        StreamWriterManager streamWriterManager = new StreamWriterManager(new StreamEmitter());

        AgentTeamSession session = new AgentTeamSession(
                "session-nullable",
                null,
                null,
                checkpointer,
                streamWriterManager
        );

        assertSame(streamWriterManager, session.streamWriterManager());
        assertSame(checkpointer, session.checkpointer());
        assertNull(session.config());
        assertNull(session.teamId());
        assertNotNull(session.span());
    }

    private static void installDefaultCheckpointer(String name, Checkpointer checkpointer) {
        CheckpointerFactory.register(name, conf -> checkpointer);
        CheckpointerFactory.installDefaultCheckpointer(new CheckpointerConfig(name, Map.of()));
    }

    private static final class RecordingCheckpointer extends Checkpointer {
    }
}
