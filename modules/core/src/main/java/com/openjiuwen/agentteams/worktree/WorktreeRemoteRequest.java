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
 * Public class WorktreeRemoteRequest used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorktreeRemoteRequest {
    private String action;
    private String slug;
    private String repoUrl;
    private String baseBranch;
    private String worktreePath;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> config = new LinkedHashMap<>();

    /**
     * toPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("slug", slug);
        payload.put("repo_url", repoUrl);
        payload.put("base_branch", baseBranch);
        payload.put("worktree_path", worktreePath);
        payload.put("config", config != null ? config : new LinkedHashMap<String, Object>());
        return payload;
    }

    /**
     * fromPayload.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static WorktreeRemoteRequest fromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return WorktreeRemoteRequest.builder().build();
        }
        Object rawConfig = payload.get("config");
        Map<String, Object> configPayload =
            rawConfig instanceof Map<?, ?> rawMap ? stringifyMap(rawMap) : new LinkedHashMap<>();
        return WorktreeRemoteRequest.builder().action(stringValue(payload.get("action")))
                .slug(stringValue(payload.get("slug"))).repoUrl(stringValue(payload.get("repo_url")))
                .baseBranch(stringValue(payload.get("base_branch")))
                .worktreePath(stringValue(payload.get("worktree_path"))).config(configPayload).build();
    }

    /**
     * stringifyMap.
     * 
     * @param rawMap rawMap
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> stringifyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
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
