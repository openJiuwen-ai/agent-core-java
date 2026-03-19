/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Schema describing a plugin reference in agent configuration.
 * <p>
 * Mirrors Python's {@code PluginSchema} used in application agent configs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginSchema {

    private String id;

    private String name;

    private String description;
}
