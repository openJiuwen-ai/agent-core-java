/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class Gap used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class Gap {
    @Builder.Default
    private String id = "";
    @Builder.Default
    private String competitor = "";
    @Builder.Default
    private String feature = "";
    @Builder.Default
    private String currentState = "";
    @Builder.Default
    private String gapDescription = "";
    @Builder.Default
    private double impact = 0.0;
    @Builder.Default
    private double feasibility = 0.0;
    @Builder.Default
    private String suggestedApproach = "";
    @Builder.Default
    private List<String> targetFiles = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public double priority() {
        return impact * feasibility;
    }
}
