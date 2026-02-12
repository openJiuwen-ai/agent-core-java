// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result;

/**
 * Base result class for all operation results.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.base_result.BaseResult
 * 
 * <p>All operation results extend this generic class with specific data types.
 * 
 * @param <T> the type of data payload
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class BaseResult<T> {

    /**
     * Status code: 0 = success, non-0 = failure.
     */
    private final int code;

    /**
     * Message details.
     */
    private final String message;

    /**
     * Business data (returned only on success).
     */
    private final T data;

    /**
     * Constructs a BaseResult with all fields.
     * 
     * @param code status code
     * @param message message details
     * @param data business data
     */
    public BaseResult(int code, String message, T data) {
        this.code = code;
        this.message = message != null ? message : "";
        this.data = data;
    }

    /**
     * Creates a success result with data.
     * 
     * @param data the data payload
     * @param <T> the data type
     * @return success result
     */
    public static <T> BaseResult<T> success(T data) {
        return new BaseResult<>(0, "success", data);
    }

    /**
     * Creates a success result with custom message.
     * 
     * @param message the success message
     * @param data the data payload
     * @param <T> the data type
     * @return success result
     */
    public static <T> BaseResult<T> success(String message, T data) {
        return new BaseResult<>(0, message, data);
    }

    /**
     * Creates a failure result.
     * 
     * @param code error code
     * @param message error message
     * @param <T> the data type
     * @return failure result
     */
    public static <T> BaseResult<T> failure(int code, String message) {
        return new BaseResult<>(code, message, null);
    }

    /**
     * Checks if this result represents a success.
     * 
     * @return true if code is 0
     */
    public boolean isSuccess() {
        return code == 0;
    }

    /**
     * Checks if this result represents a failure.
     * 
     * @return true if code is not 0
     */
    public boolean isFailure() {
        return code != 0;
    }

    /**
     * Gets the status code.
     * 
     * @return the status code
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the message.
     * 
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the data payload.
     * 
     * @return the data, may be null
     */
    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "code=" + code +
            ", message='" + message + '\'' +
            ", data=" + data +
            '}';
    }
}
