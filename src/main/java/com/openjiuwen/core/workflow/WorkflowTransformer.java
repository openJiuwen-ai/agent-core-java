/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import java.util.Map;

/**
 * Mirrors Python's {@code Transformer} field boundary used by {@code CompIOConfig} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
@FunctionalInterface
public interface WorkflowTransformer {

    /**
     * Applies the transformer to a readable workflow state snapshot.
     *
     * @param readableState dynamic state dictionary, matching Python's callable input boundary
     * @return transformer result, matching Python's {@code Any} return
     */
    Object apply(Map<String, Object> readableState);
}
