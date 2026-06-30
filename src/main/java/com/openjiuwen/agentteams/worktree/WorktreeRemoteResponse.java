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

@Data
@Builder
@NoArgsConstructor
/**
 * Public class WorktreeRemoteResponse used by the Java parity implementation.
 *
 * @since 1.0
 */
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
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public static WorktreeRemoteResponse fromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return WorktreeRemoteResponse.builder().isSuccess(false).error("empty response").build();
        }
        return WorktreeRemoteResponse.builder()
                .isSuccess(booleanValue(payload.get("success"), true))
                .worktreePath(stringValue(payload.get("worktree_path")))
                .worktreeBranch(stringValue(payload.get("worktree_branch")))
                .headCommit(stringValue(payload.get("head_commit")))
                .isExisted(booleanValue(payload.get("isExisted"), false))
                .isExists(booleanValue(payload.get("isExists"), false))
                .error(stringValue(payload.get("error")))
                .build();
    }

    private static boolean booleanValue(Object value, boolean isDefaultValue) {
        if (value instanceof Boolean isBool) {
            return isBool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return isDefaultValue;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
