/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public workflow component configuration shell.
 *
 * <p>Mirrors Python's {@code ComponentConfig}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComponentConfig {
    private WorkflowComponentMetadata metadata;
}
