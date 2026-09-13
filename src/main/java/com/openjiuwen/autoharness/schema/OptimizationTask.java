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

/**
 * Public class OptimizationTask used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationTask {
    private String topic;
    @Builder.Default
    private String description = "";
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> files = new ArrayList<>();
    private String issueRef;
    @Builder.Default
    private String expectedEffect = "";
    @Builder.Default
    private String pipelineName = "";
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;
}
