/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class AutoHarnessRuntimeState used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoHarnessRuntimeState {
    @Builder.Default
    private String currentWorkspace = "";
    @Builder.Default
    private String selectedPipeline = "";
    @Builder.Default
    private boolean isConfigBootstrapped = false;
    @Builder.Default
    private String suggestedLocalRepo = "";
}
