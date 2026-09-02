/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class BrowserRunGuardrails used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserRunGuardrails {
    @Builder.Default
    private int maxSteps = 8;
    @Builder.Default
    private int maxFailures = 2;
    @Builder.Default
    private int timeoutS = 60;
    @Builder.Default
    private boolean retryOnce = false;
    @Builder.Default
    private boolean resumeOnMaxIterations = false;
}
