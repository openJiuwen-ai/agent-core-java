/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool;

import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for the Tool workflow component.
 * <p>
 * Mirrors Python's {@code ToolComponentConfig}.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolComponentConfig extends ComponentConfig {
    private String toolId;
}
