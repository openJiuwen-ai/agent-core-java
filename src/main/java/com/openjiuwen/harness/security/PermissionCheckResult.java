/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class PermissionCheckResult used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResult {
    private PermissionLevel permission;
    private String matchedRule;
    private boolean isApprovalNeeded;

    /**
     * PermissionCheckResultBuilder.
     * 
     * @since 0.1.7
     */
    public static class PermissionCheckResultBuilder {
        /**
         * needsApproval.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public PermissionCheckResultBuilder needsApproval(boolean value) {
            this.isApprovalNeeded = value;
            return this;
        }
    }

    /**
     * Auto-generated for compatibility.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isNeedsApproval() {
        return isApprovalNeeded;
    }
}
