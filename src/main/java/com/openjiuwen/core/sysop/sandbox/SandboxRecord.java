/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted sandbox runtime record used by the gateway store.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxRecord {
    private String sandboxId;

    private String baseUrl;

    private SandboxStatus status;

    private String launcherType;

    private String sandboxType;

    private String containerConfigHash;

    @Builder.Default
    private double createdTs = System.currentTimeMillis() / 1000.0;

    @Builder.Default
    private double lastUsedTs = System.currentTimeMillis() / 1000.0;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
