/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of variable extraction from memory analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableResult {
    @Builder.Default
    private String variableKey = "";
    @Builder.Default
    private String variableValue = "";
}
