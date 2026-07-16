/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.session.stream.StreamSchema;

/**
 * Top-level workflow chunk alias for streamed workflow outputs.
 * <p>
 * Mirrors Python's {@code WorkflowChunk = Union[OutputSchema, CustomSchema, TraceSchema]}.
 * </p>
 * 
 * @since 0.1.7
 */
public interface WorkflowChunk extends StreamSchema {
}
