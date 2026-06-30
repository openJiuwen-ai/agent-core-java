/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class PermissionCheckResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class PermissionCheckResult {
    private PermissionLevel permission;
    private String matchedRule;
    private boolean isApprovalNeeded;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class PermissionCheckResultBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public PermissionCheckResultBuilder needsApproval(boolean value) {
            this.isApprovalNeeded = value;
            return this;
        }
    }

    /**
     * Auto-generated for compatibility.
     */
    public boolean isNeedsApproval() {
        return isApprovalNeeded;
    }
}
