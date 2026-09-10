/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Mirrors Python's {@code PermissionConfirmResponse} in
 * {@code openjiuwen/harness/security/models.py}.
 *
 * <p>The four states map onto persistence behavior in the permission interrupt rail:
 * approved + autoConfirm + persistAllow writes a permanent allow rule to disk;
 * approved + autoConfirm without persistAllow stores a session-scoped auto-confirm;
 * approved alone allows this single invocation; not approved rejects.
 */
public final class PermissionConfirmResponse {

    private final boolean approved;
    private final String feedback;
    private final boolean autoConfirm;
    private final boolean persistAllow;

    public PermissionConfirmResponse(boolean approved) {
        this(approved, "", false, false);
    }

    public PermissionConfirmResponse(boolean approved, String feedback, boolean autoConfirm) {
        this(approved, feedback, autoConfirm, false);
    }

    public PermissionConfirmResponse(boolean approved, String feedback, boolean autoConfirm, boolean persistAllow) {
        this.approved = approved;
        this.feedback = feedback == null ? "" : feedback;
        this.autoConfirm = autoConfirm;
        this.persistAllow = persistAllow;
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

    /**
     * Whether the response requests persisting a permanent allow rule to disk.
     *
     * @return true when the allow decision should be written back to the permissions config
     */
    public boolean isPersistAllow() {
        return persistAllow;
    }
}
