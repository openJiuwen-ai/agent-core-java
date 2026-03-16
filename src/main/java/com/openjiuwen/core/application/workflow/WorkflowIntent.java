/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.controller.schema.Task;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Intent result used by {@link WorkflowController} compatibility APIs.
 */
public record WorkflowIntent(Type intentType, Task task, WorkflowSchema workflow, Map<String, Object> metadata) {

    public WorkflowIntent {
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }

    public enum Type {
        EXEC_NEW_TASK,
        RESUME_TASK,
        DEFAULT_RESPONSE
    }
}
