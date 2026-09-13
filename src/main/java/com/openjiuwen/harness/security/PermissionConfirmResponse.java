/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User confirmation for an ASK permission decision.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.models.PermissionConfirmResponse}.
 * The four states map onto persistence behavior in {@code PermissionInterruptRail}:
 * approved + autoConfirm + persistAllow writes a permanent allow rule to disk;
 * approved + autoConfirm without persistAllow stores a session-scoped auto-confirm;
 * approved alone allows this single invocation; not approved rejects.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionConfirmResponse {
    private boolean approved;
    @Builder.Default
    private String feedback = "";
    private boolean autoConfirm;
    private boolean persistAllow;
}
