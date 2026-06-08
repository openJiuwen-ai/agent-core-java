/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Mirrors Python's {@code PermissionConfirmResponse} in
 * {@code openjiuwen/harness/security/models.py}.
 */
public final class PermissionConfirmResponse {

    private final boolean approved;
    private final String feedback;
    private final boolean autoConfirm;

    public PermissionConfirmResponse(boolean approved) {
        this(approved, "", false);
    }

    public PermissionConfirmResponse(boolean approved, String feedback, boolean autoConfirm) {
        this.approved = approved;
        this.feedback = feedback == null ? "" : feedback;
        this.autoConfirm = autoConfirm;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getFeedback() {
        return feedback;
    }

    public boolean isAutoConfirm() {
        return autoConfirm;
    }
}
