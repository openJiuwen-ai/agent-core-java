/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public workflow component metadata model.
 *
 * <p>Mirrors Python's {@code WorkflowComponentMetadata}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowComponentMetadata {
    private String nodeId;
    private String nodeType;
    private String nodeName;
}
