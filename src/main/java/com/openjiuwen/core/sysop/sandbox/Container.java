/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Durable descriptor for a Java fallback sandbox instance.
 * <p>
 * Mirrors the role of Python's launched-sandbox descriptor: a stable handle
 * that higher layers can persist and use for pause/resume/delete style flows
 * once deeper lifecycle management is added.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Container {
    private String key;

    /** Sandbox root or base URL, depending on the runtime model. */
    private String baseUrl;

    /** Opaque runtime identifier; for fallback mode this is the cache key. */
    private String sandboxId;

    /** Host-side mapped port when applicable. */
    private Integer hostPort;
}
