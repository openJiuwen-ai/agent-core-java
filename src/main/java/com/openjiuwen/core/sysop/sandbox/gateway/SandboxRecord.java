/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Record representing a sandbox instance in the store.
 * <p>
 * Mirrors Python's {@code SandboxRecord} dataclass in {@code sandbox/gateway/sandbox_store.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxRecord {

    /** Unique identifier assigned by the runtime (Docker container id, E2B sandbox id, etc.). */
    private String sandboxId;

    /** HTTP base URL for the sandbox service. */
    private String baseUrl;

    /** Current lifecycle status of the sandbox. */
    private SandboxStatus status;

    /** Launcher type that created this sandbox (e.g., "pre_deploy", "docker"). */
    private String launcherType;

    /** Sandbox provider type (e.g., "aio", "e2b", "mock"). */
    private String sandboxType;

    /** Hash of container-level configuration for cache matching. */
    private String containerConfigHash;

    /** Timestamp when the sandbox was created (epoch seconds). */
    @Builder.Default
    private double createdTs = System.currentTimeMillis() / 1000.0;

    /** Timestamp when the sandbox was last used (epoch seconds). */
    @Builder.Default
    private double lastUsedTs = System.currentTimeMillis() / 1000.0;

    /** Arbitrary metadata attached to the sandbox. */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}