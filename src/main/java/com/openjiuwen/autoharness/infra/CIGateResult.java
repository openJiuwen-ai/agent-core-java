/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class CIGateResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class CIGateResult {
    @Builder.Default
    private boolean isPassed = true;
    @Builder.Default
    private List<String> executedCommands = new ArrayList<>();
    @Builder.Default
    private List<String> gateOutputs = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> gates = new ArrayList<>();
    @Builder.Default
    private String errors = "";
}
