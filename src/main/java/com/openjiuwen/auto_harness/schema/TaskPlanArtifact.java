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
 * Public class TaskPlanArtifact used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TaskPlanArtifact {
    @Builder.Default
    private List<OptimizationTask> tasks = new ArrayList<>();
    @Builder.Default
    private String rawPlan = "";
}
