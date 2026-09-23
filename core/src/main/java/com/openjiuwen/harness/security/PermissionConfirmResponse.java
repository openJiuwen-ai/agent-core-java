/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * User confirmation for an ASK permission decision.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.models.PermissionConfirmResponse}.
 * The four states map onto persistence behavior in {@code PermissionInterruptRail}:
 * approved + autoConfirm + persistAllow writes a permanent allow rule to disk;
 * approved + autoConfirm without persistAllow stores a session-scoped auto-confirm;
 * approved alone allows this single invocation; not approved rejects.
 *
 * <p>A resume input is kept in the session state by {@code Checkpointer#preAgentExecute}, and a
 * persisting checkpointer writes that state with Java serialization. This type is therefore
 * {@link Serializable}: without it the save of the resuming turn fails as a whole, the snapshot
 * taken when the tool was interrupted stays in the store, and the next turn of the same session
 * recovers it and replays the tool call that was just answered.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionConfirmResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean approved;
    @Builder.Default
    private String feedback = "";
    private boolean autoConfirm;
    private boolean persistAllow;
}
