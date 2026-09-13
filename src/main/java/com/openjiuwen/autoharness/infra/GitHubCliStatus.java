/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class GitHubCliStatus used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubCliStatus {
    @Builder.Default
    private boolean isAvailable = false;
    @Builder.Default
    private boolean isAuthenticated = false;
    @Builder.Default
    private boolean isInstalledNow = false;
    @Builder.Default
    private String path = "";
}
