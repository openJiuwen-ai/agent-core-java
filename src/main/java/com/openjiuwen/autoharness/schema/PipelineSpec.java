/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import com.openjiuwen.autoharness.pipelines.BasePipeline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
/**
 * Public class PipelineSpec used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class PipelineSpec {
    private String name;
    private Class<? extends BasePipeline> pipelineCls;
    @Builder.Default
    private String description = "";
    @Builder.Default
    private List<String> expectedOutputs = new ArrayList<>();
}
