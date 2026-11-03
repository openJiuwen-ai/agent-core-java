/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class BrowserProfile used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserProfile {
    private String name;
    @Builder.Default
    private String driverType = "managed";
    private String cdpUrl;
    private String userDataDir;
    private int debugPort;
    private String host;
}
