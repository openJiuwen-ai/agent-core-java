/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

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
}
