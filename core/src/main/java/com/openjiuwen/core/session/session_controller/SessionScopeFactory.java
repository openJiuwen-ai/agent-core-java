/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Mirrors Python's {@code SessionScopeFactory} in
 * {@code openjiuwen/core/session/session_controller/scope_factory.py}.
 */
public final class SessionScopeFactory {

    private SessionScopeFactory() {
    }

    public static SessionScope createMain() {
        return new SessionScope(new MainScope(), null);
    }

    public static SessionScope createDirect(String userId) {
        return new SessionScope(new MainScope(), new DirectSubject(userId));
    }

    public static SessionScope createGroup(String groupId) {
        return new SessionScope(new MainScope(), new GroupSubject(groupId));
    }

    public static SessionScope createGroupUser(String groupId, String userId) {
        return new SessionScope(new MainScope(), new GroupUserSubject(groupId, userId));
    }

    public static SessionScope createCustom(Scope scope, Subject subject) {
        return new SessionScope(scope, subject);
    }

    public static SessionScope fromString(String keyString) {
        return SessionScope.fromString(keyString);
    }
}
