/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for sandbox launcher.
 * <p>
 * Mirrors Python's {@code SandboxLauncherConfig} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxLauncherConfig {

    /** Launcher type name (e.g., "pre_deploy", "docker"). */
    private String launcherType;

    /** Remote sandbox gateway service endpoint. */
    @Builder.Default
    private String gatewayUrl = "";

    /** Sandbox provider type (e.g., "aio", "e2b", "mock"). */
    @Builder.Default
    private String sandboxType = "mock";

    /** Behavior when sandbox is stopped: "delete", "pause", or "keep". */
    @Builder.Default
    private String onStop = "delete";

    /** Evict idle sandbox after this TTL (seconds). Null means no eviction. */
    private Integer idleTtlSeconds;

    /** Arbitrary parameters passed to the launcher. */
    @Builder.Default
    private Map<String, Object> extraParams = new HashMap<>();

    /** Container image name (for Docker-based launchers). */
    private String image;

    /** Environment variables for the container. */
    @Builder.Default
    private Map<String, String> env = new HashMap<>();

    /** Volume mount specifications. */
    @Builder.Default
    private Map<String, String> volumes = new HashMap<>();

    /** Resource limits for the container. */
    private Map<String, Object> resourceLimits;

    /** Network configuration. */
    private String network;

    /** Service port exposed by the container. */
    private Integer servicePort;
}