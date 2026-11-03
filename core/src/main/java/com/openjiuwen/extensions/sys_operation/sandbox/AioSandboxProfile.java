/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AIO sandbox provider preset for Java sandbox gateway configuration.
 * 
 * @since 0.1.7
 */
public final class AioSandboxProfile {
    /**
     * AioSandboxProfile.
     * 
     * @since 0.1.7
     */
    private AioSandboxProfile() {
    }

    /**
     * config.
     * 
     * @param gatewayUrl gatewayUrl
     * @return the result
     * @since 0.1.7
     */
    public static SandboxGatewayConfig config(String gatewayUrl) {
        return config(gatewayUrl, Map.of());
    }

    /**
     * config.
     * 
     * @param gatewayUrl gatewayUrl
     * @param extraParams extraParams
     * @return the result
     * @since 0.1.7
     */
    public static SandboxGatewayConfig config(String gatewayUrl, Map<String, Object> extraParams) {
        return SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy").gatewayUrl(gatewayUrl)
                        .baseUrl(gatewayUrl).sandboxType("aio").extraParams(new LinkedHashMap<>(extraParams)).build())
                .gatewayUrl(gatewayUrl).build();
    }
}
