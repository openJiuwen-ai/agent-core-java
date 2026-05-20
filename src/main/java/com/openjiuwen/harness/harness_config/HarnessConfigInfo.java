/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class HarnessConfigInfo used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class HarnessConfigInfo {
    private String id;
    private String name;
    private String version;
    private String packageName;
    private Path configPath;
    @Builder.Default
    private boolean isEnabled = true;
}
