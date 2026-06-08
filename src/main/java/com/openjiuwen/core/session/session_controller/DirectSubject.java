/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Mirrors Python's {@code DirectSubject} in
 * {@code openjiuwen/core/session/session_controller/scope.py}.
 *
 * @param userId unique user identifier
 */
public record DirectSubject(String userId) implements Subject {

    public DirectSubject {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("DirectSubject userId cannot be empty");
        }
    }

    public static DirectSubject fromString(String subjectString) {
        if (!subjectString.startsWith("direct:")) {
            throw new IllegalArgumentException(
                    "DirectSubject must start with 'direct:', got '" + subjectString + "'");
        }
        return new DirectSubject(subjectString.substring("direct:".length()));
    }

    @Override
    public String toString() {
        return "direct:" + userId;
    }
}
