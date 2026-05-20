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
 * Public class PipelineSelectionArtifact used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class PipelineSelectionArtifact {
    @Builder.Default
    private String pipelineName = "meta_evolve_pipeline";
    @Builder.Default
    private String reason = "";
    @Builder.Default
    private List<String> alternatives = new ArrayList<>();
    @Builder.Default
    private double confidence = 0.0;
    @Builder.Default
    private String riskLevel = "";
    @Builder.Default
    private List<String> requiredInputs = new ArrayList<>();
    @Builder.Default
    private String fallbackPipeline = "";
}
