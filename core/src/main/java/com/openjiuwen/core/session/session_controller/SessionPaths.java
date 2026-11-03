/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.nio.file.Path;

/**
 * Mirrors Python's {@code SessionPaths} in
 * {@code openjiuwen/core/session/session_controller/utils.py}.
 */
public final class SessionPaths {

    private SessionPaths() {
    }

    public static Path agentDir(Path basePath, String agentId) {
        return basePath.resolve(agentId);
    }

    public static Path sessionsDir(Path basePath, String agentId) {
        return basePath.resolve(agentId).resolve("sessions");
    }

    public static Path metaFile(Path basePath, String agentId) {
        return sessionsDir(basePath, agentId).resolve("sessions.json");
    }

    public static Path sessionDir(Path basePath, String agentId, String sessionId) {
        return sessionsDir(basePath, agentId).resolve(sessionId);
    }

    public static Path stateFile(Path sessionDir) {
        return sessionDir.resolve("state.data");
    }

    public static Path downstreamsDir(Path sessionDir) {
        return sessionDir.resolve("downstreams");
    }

    public static Path linkFile(Path sessionDir, String targetAgent, String targetSession) {
        return sessionDir.resolve("downstreams").resolve(targetAgent + "_" + targetSession + ".link");
    }
}
