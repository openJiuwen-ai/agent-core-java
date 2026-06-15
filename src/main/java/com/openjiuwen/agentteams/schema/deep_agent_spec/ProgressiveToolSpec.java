/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Progressive tool exposure configuration.
 * Mirrors Python ProgressiveToolSpec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressiveToolSpec {

    @Builder.Default
    private boolean enabled = true;
    @Builder.Default
    private List<String> alwaysVisibleTools = new ArrayList<>();
    @Builder.Default
    private List<String> defaultVisibleTools = new ArrayList<>();
    @Builder.Default
    private int maxLoadedTools = 12;
}
