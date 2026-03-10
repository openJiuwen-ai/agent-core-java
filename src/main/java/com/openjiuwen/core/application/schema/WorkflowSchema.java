/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Schema describing a workflow reference in agent configuration.
 * <p>
 * Mirrors Python's {@code WorkflowSchema} used in application agent configs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSchema {

    private String id;

    private String name;

    @Builder.Default
    private String version = "1.0";

    private String description;

    private Map<String, Object> inputParams;
}
