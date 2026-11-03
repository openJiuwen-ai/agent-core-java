/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Mirrors Python's {@code GroupSubject} in
 * {@code openjiuwen/core/session/session_controller/scope.py}.
 *
 * @param groupId unique group identifier
 */
public record GroupSubject(String groupId) implements Subject {

    public GroupSubject {
        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("GroupSubject groupId cannot be empty");
        }
    }

    public static GroupSubject fromString(String subjectString) {
        if (!subjectString.startsWith("group:")) {
            throw new IllegalArgumentException(
                    "GroupSubject must start with 'group:', got '" + subjectString + "'");
        }
        return new GroupSubject(subjectString.substring("group:".length()));
    }

    @Override
    public String toString() {
        return "group:" + groupId;
    }
}
