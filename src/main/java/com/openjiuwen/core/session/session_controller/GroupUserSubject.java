/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Mirrors Python's {@code GroupUserSubject} in
 * {@code openjiuwen/core/session/session_controller/scope.py}.
 *
 * @param groupId group identifier
 * @param userId user identifier
 */
public record GroupUserSubject(String groupId, String userId) implements Subject {

    public GroupUserSubject {
        if (groupId == null || groupId.isEmpty() || userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("GroupUserSubject groupId and userId cannot be empty");
        }
    }

    public static GroupUserSubject fromString(String subjectString) {
        String[] parts = subjectString.split(":", -1);
        if (parts.length != 4 || !"group".equals(parts[0]) || !"user".equals(parts[2])) {
            throw new IllegalArgumentException(
                    "GroupUserSubject must have format 'group:{group_id}:user:{user_id}', got '"
                            + subjectString + "'");
        }
        return new GroupUserSubject(parts[1], parts[3]);
    }

    @Override
    public String toString() {
        return "group:" + groupId + ":user:" + userId;
    }
}
