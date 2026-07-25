/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Backward-compatible result envelope for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code BaseResult} in
 * {@code openjiuwen/core/sys_operation/result/base_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.BaseResult}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class BaseResult<T> {

    private int code;
    private String message;
    private T data;

    public BaseResult() {
    }

    public BaseResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * Build an error result for a sys operation.
     *
     * @param statusCode the error status code
     * @param execution  the execution type
     * @param errorMsg   the error message
     * @param resultFactory factory to create the result instance
     * @param data       the result data
     * @param <R>        result type
     * @param <D>        data type
     * @return a new error result
     */
    public static <R extends BaseResult<D>, D> R buildOperationErrorResult(
            StatusCode statusCode,
            String execution,
            String errorMsg,
            java.util.function.Supplier<R> resultFactory,
            D data) {
        R result = resultFactory.get();
        result.setCode(statusCode.getCode());
        result.setMessage(errorMsg != null ? errorMsg : execution);
        result.setData(data);
        return result;
    }
}
