/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import com.openjiuwen.core.context.schema.ContextEngineConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for workflow-based agent in the application layer.
 * <p>
 * Mirrors Python's {@code WorkflowAgentConfig} used by WorkflowAgent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAgentConfig {

    private String id;

    @Builder.Default
    private String version = "1.0";

    @Builder.Default
    private String description = "";

    @Builder.Default
    private List<WorkflowSchema> workflows = new ArrayList<>();

    private DefaultResponse defaultResponse;

    private ContextEngineConfig contextEngineConfig;
}
