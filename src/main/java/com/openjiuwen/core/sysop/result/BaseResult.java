/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import com.openjiuwen.core.common.exception.StatusCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Base result class for all sys_operation results.
 * <p>
 * Mirrors Python's {@code BaseResult[T]} in {@code sys_operation/result/base_result.py}.
 *
 * @param <T> the data type carried by this result
 */
@Data
@SuperBuilder
@NoArgsConstructor
/**
 * Auto-generated for codecheck compliance.
 */
public abstract class BaseResult<T> {

    /** Status code: 0 = success, non-0 = failure. */
    private int code;

    /** Message details. */
    private String message;

    /** Business data (returned only on success). */
    private T data;

    /** Explicit all-args constructor for subclass super() calls. */
    protected BaseResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Create a standardized error result object with specified error type and formatted message.
     *
     * @param errorType        StatusCode enum type
     * @param msgFormatKwargs  key-value pairs for formatting the error message template
     * @param resultFactory    factory to create the concrete result instance
     * @param data             optional data to carry in the error result
     * @param <T>              data type
     * @param <R>              result type
     * @return instantiated error result
     */
    public static <T, R extends BaseResult<T>> R buildOperationErrorResult(
            StatusCode errorType,
            Map<String, String> msgFormatKwargs,
            ResultFactory<R> resultFactory,
            T data) {
        String errorMessage = formatErrorMessage(errorType.getErrmsg(), msgFormatKwargs);
        R result = resultFactory.get();
        result.setCode(errorType.getCode());
        result.setMessage(errorMessage);
        @SuppressWarnings("unchecked")
        BaseResult<T> baseResult = result;
        baseResult.setData(data);
        return result;
    }

    /**
     * Convenience overload for simple execution/error_msg formatting.
     *
     * @param errorType     StatusCode enum type
     * @param execution     the operation name
     * @param errorMsg      the error message detail
     * @param resultFactory factory to create the concrete result instance
     * @param data          optional data to carry in the error result
     * @param <T>           data type
     * @param <R>           result type
     * @return instantiated error result
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static <T, R extends BaseResult<?>> R buildOperationErrorResult(
            StatusCode errorType,
            String execution, String errorMsg,
            ResultFactory<R> resultFactory,
            Object data) {
        String template = errorType.getErrmsg();
        String errorMessage = template
                .replace("{execution}", execution)
                .replace("{error_msg}", errorMsg);
        R result = resultFactory.get();
        result.setCode(errorType.getCode());
        result.setMessage(errorMessage);
        try {
            ((BaseResult<Object>) result).setData(data);
        } catch (ClassCastException ignored) {
            // data type mismatch, leave as null
        }
        return result;
    }

    /**
     * Factory interface for creating typed result instances (no-arg supplier).
     */
    @FunctionalInterface
    public interface ResultFactory<R> extends Supplier<R> {
    }

    private static String formatErrorMessage(String template, Map<String, String> kwargs) {
        if (kwargs == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : kwargs.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
