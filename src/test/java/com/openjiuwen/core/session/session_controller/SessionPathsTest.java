/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code TestSessionPaths} in
 * {@code tests/unit_tests/core/session/session_controller/test_utils.py}.</p>
 */
class SessionPathsTest {

    @Test
    void agentDirResolvesAgentDirectory() {
        assertThat(SessionPaths.agentDir(Path.of("/base"), "agent1"))
                .isEqualTo(Path.of("/base", "agent1"));
    }

    @Test
    void sessionsDirResolvesSessionsDirectory() {
        assertThat(SessionPaths.sessionsDir(Path.of("/base"), "agent1"))
                .isEqualTo(Path.of("/base", "agent1", "sessions"));
    }

    @Test
    void metaFileResolvesSessionsJson() {
        assertThat(SessionPaths.metaFile(Path.of("/base"), "agent1"))
                .isEqualTo(Path.of("/base", "agent1", "sessions", "sessions.json"));
    }

    @Test
    void sessionDirResolvesSessionDirectory() {
        assertThat(SessionPaths.sessionDir(Path.of("/base"), "agent1", "sess1"))
                .isEqualTo(Path.of("/base", "agent1", "sessions", "sess1"));
    }

    @Test
    void stateFileResolvesStateFile() {
        assertThat(SessionPaths.stateFile(Path.of("/sess1")))
                .isEqualTo(Path.of("/sess1", "state.data"));
    }

    @Test
    void downstreamsDirResolvesDownstreamsDirectory() {
        assertThat(SessionPaths.downstreamsDir(Path.of("/sess1")))
                .isEqualTo(Path.of("/sess1", "downstreams"));
    }

    @Test
    void linkFileResolvesLinkPath() {
        assertThat(SessionPaths.linkFile(Path.of("/sess1"), "agent2", "sess2"))
                .isEqualTo(Path.of("/sess1", "downstreams", "agent2_sess2.link"));
    }
}
