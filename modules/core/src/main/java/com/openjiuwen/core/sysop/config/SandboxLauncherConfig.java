/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Launcher/runtime acquisition configuration for sandbox execution.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxLauncherConfig {
    private String launcherType;

    @Builder.Default
    private String gatewayUrl = "";

    @Builder.Default
    private String sandboxType = "mock";

    @Builder.Default
    private String onStop = "delete";

    private Integer idleTtlSeconds;

    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extraParams = new LinkedHashMap<>();

    private String baseUrl;
}
