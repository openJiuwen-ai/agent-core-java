/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers for building retrieval-related exceptions with concise call sites.
 * 
 * @since 0.1.7
 */
public final class RetrievalExceptions {
    /**
     * RetrievalExceptions.
     * 
     * @since 0.1.7
     */
    private RetrievalExceptions() {
    }

    /**
     * error.
     * 
     * @param status status
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    public static BaseError error(StatusCode status, String message) {
        return ErrorHelper.buildError(status, message, null, null, Map.of("error_msg", message));
    }

    /**
     * validation.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    public static ValidationError validation(String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", message);
        params.put("data", "");
        return new ValidationError(StatusCode.SCHEMA_VALIDATE_INVALID, message, null, null, params);
    }
}
