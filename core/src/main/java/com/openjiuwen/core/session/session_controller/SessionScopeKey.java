/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.util.Objects;

/**
 * Mirrors Python's {@code SessionScopeKey} in
 * {@code openjiuwen/core/session/session_controller/scope.py}.
 *
 * @param agentId owning agent identifier
 * @param sessionScope scope descriptor
 */
public record SessionScopeKey(String agentId, SessionScope sessionScope) {

    public SessionScopeKey {
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be empty");
        }
        Objects.requireNonNull(sessionScope, "sessionScope");
    }

    public static SessionScopeKey fromString(String keyString) {
        if (!keyString.startsWith("agent:")) {
            throw new IllegalArgumentException("SessionScopeKey must start with 'agent:'");
        }
        String rest = keyString.substring("agent:".length());
        int separatorIndex = rest.indexOf(':');
        String agentId = separatorIndex >= 0 ? rest.substring(0, separatorIndex) : rest;
        String sessionScopeString = separatorIndex >= 0 ? rest.substring(separatorIndex + 1) : "";
        return new SessionScopeKey(agentId, SessionScope.fromString(sessionScopeString));
    }

    @Override
    public String toString() {
        return "agent:" + agentId + ":" + sessionScope;
    }
}
