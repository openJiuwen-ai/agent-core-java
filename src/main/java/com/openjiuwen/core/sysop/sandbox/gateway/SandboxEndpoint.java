/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descriptor representing a sandbox endpoint connection.
 * <p>
 * Contains the base URL and sandbox identifier needed to interact with
 * a sandbox instance through its provider.
 * <p>
 * Mirrors Python's {@code SandboxEndpoint} in {@code sandbox/gateway/gateway.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxEndpoint {

    /** HTTP base URL for the sandbox service. */
    private String baseUrl;

    /** Opaque identifier assigned by the runtime (may be null for external sandboxes). */
    private String sandboxId;
}