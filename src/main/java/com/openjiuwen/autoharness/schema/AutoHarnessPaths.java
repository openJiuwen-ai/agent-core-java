/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class AutoHarnessPaths used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoHarnessPaths {
    @Builder.Default
    private String dataDir = "";
    @Builder.Default
    private String experienceDir = "";
    @Builder.Default
    private String worktreesDir = "";
    @Builder.Default
    private String runsDir = "";
    @Builder.Default
    private String cacheRepoDir = "";
}
