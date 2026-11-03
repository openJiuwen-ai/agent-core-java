/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.io.Serial;
import java.io.Serializable;

/**
 * Mirrors Python's {@code PermissionConfirmResponse} in
 * {@code openjiuwen/harness/security/models.py}.
 *
 * <p>The four states map onto persistence behavior in the permission interrupt rail:
 * approved + autoConfirm + persistAllow writes a permanent allow rule to disk;
 * approved + autoConfirm without persistAllow stores a session-scoped auto-confirm;
 * approved alone allows this single invocation; not approved rejects.
 *
 * <p>A resume input is kept in the session state by {@code Checkpointer#preAgentExecute}, and a
 * persisting checkpointer writes that state with Java serialization. This type is therefore
 * {@link Serializable}: without it the save of the resuming turn fails as a whole, the snapshot
 * taken when the tool was interrupted stays in the store, and the next turn of the same session
 * recovers it and replays the tool call that was just answered.
 */
public final class PermissionConfirmResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
