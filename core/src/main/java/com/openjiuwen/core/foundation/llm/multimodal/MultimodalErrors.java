/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for structured multimodal contract errors.
 *
 * <p>Every error reuses {@link StatusCode#INTERACTION_INPUT_INVALID} and
 * carries the structured {@code reason} plus locators (block id, field name,
 * source id, stage, original input index) in its details map. Errors built
 * here are validation-style failures: retrying the same input will not help.</p>
 *
 * @since 0.1.17
 */
public final class MultimodalErrors {
    private MultimodalErrors() {
    }

    /**
     * Build a contract error for invalid content block structure.
     *
     * @param message human-readable failure description
     * @param blockId content block id locator; may be {@code null}
     * @param field offending field name locator; may be {@code null}
     * @param index original input index locator; may be {@code null}
     * @return error carrying {@code reason=CONTENT_INVALID} details
     */
    public static BaseError contentInvalid(String message, String blockId, String field, Integer index) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("blockId", blockId);
        locators.put("field", field);
        locators.put("index", index);
        return failed(MultimodalErrorReasons.CONTENT_INVALID, message, locators, null);
    }

    /**
     * Build a contract error with an explicit reason and locator details.
     *
     * @param reason one of the {@link MultimodalErrorReasons} constants
     * @param message human-readable failure description
     * @param locators locator entries (blockId, field, sourceId, stage, index,
     *                 limit, actual, items); {@code null} values are dropped
     * @param cause original exception; may be {@code null}
     * @return error carrying {@code details.reason} and the locators
     */
    public static BaseError failed(String reason, String message, Map<String, Object> locators, Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        if (locators != null) {
            locators.forEach((key, value) -> {
                if (value != null) {
                    details.put(key, value);
                }
            });
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", reason + ": " + message);
        return ErrorHelper.buildError(StatusCode.INTERACTION_INPUT_INVALID,
                reason + ": " + message, details, cause, params);
    }

    /**
     * Extract the structured reason from an error built by this factory.
     *
     * @param error error to inspect; may be {@code null}
     * @return reason carried in {@code details.reason}, or empty when absent
     */
    public static Optional<String> reasonOf(BaseError error) {
        return stringDetail(error, "reason");
    }

    /**
     * Extract a string locator from an error built by this factory.
     *
     * @param error error to inspect; may be {@code null}
     * @param key locator key such as {@code stage} or {@code sourceId}
     * @return locator value, or empty when absent
     */
    public static Optional<String> stringDetail(BaseError error, String key) {
        if (error == null || !(error.getDetails() instanceof Map<?, ?> details)) {
            return Optional.empty();
        }
        Object value = details.get(key);
        return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
    }
}
