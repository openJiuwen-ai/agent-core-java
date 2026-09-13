/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Permission decision produced by the harness permission engine.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.models.PermissionResult}: the
 * final {@link PermissionLevel} after merging the tool-level and path-level pipelines,
 * the matched rule summary, an optional human-readable reason, and any external paths
 * that triggered a path-level raise.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResult {
    private PermissionLevel permission;
    private String matchedRule;
    private String reason;
    @Builder.Default
    private List<String> externalPaths = new ArrayList<>();

    /**
     * Whether the decision allows execution without confirmation.
     *
     * @return true when permission is ALLOW
     * @since 0.1.15
     */
    public boolean isAllowed() {
        return permission == PermissionLevel.ALLOW;
    }

    /**
     * Whether the decision denies execution.
     *
     * @return true when permission is DENY
     * @since 0.1.15
     */
    public boolean isDenied() {
        return permission == PermissionLevel.DENY;
    }

    /**
     * Whether the decision requires user approval before execution.
     *
     * @return true when permission is ASK
     * @since 0.1.15
     */
    public boolean needsApproval() {
        return permission == PermissionLevel.ASK;
    }
}
