/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class CLIOptions used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CLIOptions {
    private String provider;
    private String model;
    private String apiKey;
    private String apiBase;
    private String remote;
    @Builder.Default
    private boolean isVerbose = false;
    private String workspace;
}
