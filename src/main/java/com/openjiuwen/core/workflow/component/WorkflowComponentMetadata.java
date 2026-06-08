/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code WorkflowComponentMetadata} in
 * {@code openjiuwen/core/workflow/components/base.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowComponentMetadata {
    private String nodeId;
    private String nodeType;
    private String nodeName;
}
