/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Model (LLM) error. */
public class ModelError extends ExecutionError {
    public ModelError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public ModelError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public ModelError(StatusCode status) { super(status); }
}
