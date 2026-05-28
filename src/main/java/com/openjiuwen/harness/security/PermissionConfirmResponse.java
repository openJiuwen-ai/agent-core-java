/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User confirmation response for permission ASK scenario.
 *
 * <p>When {@code approved} and {@code autoConfirm} are both true, the rail follows
 * the path of merging permissions, updating memory, and writing to disk
 * (consistent with {@code PermissionInterruptRail._persist_allow_always}).
 * Only {@code approved} means one-time approval.
 *
 * <p>Mirrors Python's {@code PermissionConfirmResponse} in
 * {@code openjiuwen.harness.security.models}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionConfirmResponse {

    /** Whether the user approved the operation. */
    private boolean approved;

    /** User feedback or reason for rejection. */
    @Builder.Default
    private String feedback = "";

    /** Whether to persist the approval (remember for future). */
    @Builder.Default
    private boolean autoConfirm = false;

    /**
     * Create an approved response.
     */
    public static PermissionConfirmResponse approved() {
        return PermissionConfirmResponse.builder()
                .approved(true)
                .build();
    }

    /**
     * Create an approved response with auto-confirm.
     */
    public static PermissionConfirmResponse approvedAlways() {
        return PermissionConfirmResponse.builder()
                .approved(true)
                .autoConfirm(true)
                .build();
    }

    /**
     * Create a rejected response.
     */
    public static PermissionConfirmResponse rejected(String feedback) {
        return PermissionConfirmResponse.builder()
                .approved(false)
                .feedback(feedback != null ? feedback : "[PERMISSION_REJECTED] User rejected the request.")
                .build();
    }
}