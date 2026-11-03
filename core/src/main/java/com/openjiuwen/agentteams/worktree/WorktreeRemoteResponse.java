/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class WorktreeRemoteResponse used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorktreeRemoteResponse {
    @Builder.Default
    private boolean isSuccess = true;
    private String worktreePath;
    private String worktreeBranch;
    private String headCommit;
    @Builder.Default
    private boolean isExisted = false;
    @Builder.Default
    private boolean isExists = false;
    private String error;

    /**
     * toPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", isSuccess);
        payload.put("worktree_path", worktreePath);
        payload.put("worktree_branch", worktreeBranch);
        payload.put("head_commit", headCommit);
        payload.put("isExisted", isExisted);
        payload.put("isExists", isExists);
        payload.put("error", error);
        return payload;
    }

    /**
     * fromPayload.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    public static WorktreeRemoteResponse fromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return WorktreeRemoteResponse.builder().isSuccess(false).error("empty response").build();
        }
        return WorktreeRemoteResponse.builder().isSuccess(booleanValue(payload.get("success"), true))
                .worktreePath(stringValue(payload.get("worktree_path")))
                .worktreeBranch(stringValue(payload.get("worktree_branch")))
                .headCommit(stringValue(payload.get("head_commit")))
                .isExisted(booleanValue(payload.get("isExisted"), false))
                .isExists(booleanValue(payload.get("isExists"), false)).error(stringValue(payload.get("error")))
                .build();
    }

    /**
     * booleanValue.
     * 
     * @param value value
     * @param isDefaultValue isDefaultValue
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanValue(Object value, boolean isDefaultValue) {
        if (value instanceof Boolean isBool) {
            return isBool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return isDefaultValue;
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
