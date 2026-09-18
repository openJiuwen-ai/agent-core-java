/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.util.Objects;

/**
 * Mirrors Python's {@code SessionScope} in
 * {@code openjiuwen/core/session/session_controller/scope.py}.
 *
 * @param scope scope component
 * @param subject optional subject component
 */
public record SessionScope(Scope scope, Subject subject) {

    public SessionScope {
        Objects.requireNonNull(scope, "scope");
    }

    public static SessionScope fromString(String keyString) {
        String[] parts = keyString.split(":", 2);
        String scopeString = parts[0];
        String subjectString = parts.length > 1 ? parts[1] : null;

        Scope parsedScope;
        if ("main".equals(scopeString)) {
            parsedScope = new MainScope();
        } else {
            throw new IllegalArgumentException("Unknown scope: " + scopeString);
        }

        Subject parsedSubject = null;
        if (subjectString != null && !subjectString.isEmpty()) {
            if (subjectString.startsWith("direct:")) {
                parsedSubject = DirectSubject.fromString(subjectString);
            } else if (subjectString.startsWith("group:") && subjectString.contains(":user:")) {
                parsedSubject = GroupUserSubject.fromString(subjectString);
            } else if (subjectString.startsWith("group:")) {
                parsedSubject = GroupSubject.fromString(subjectString);
            } else {
                throw new IllegalArgumentException("Unknown subject format: " + subjectString);
            }
        }

        return new SessionScope(parsedScope, parsedSubject);
    }

    @Override
    public String toString() {
        return subject == null ? scope.toString() : scope + ":" + subject;
    }
}
