/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.runner.base;

/**
 * Represents a failed operation result following the Result pattern.
 * <p>
 * Mirrors Python's {@code Error} class.
 *
 * @param <T> the expected success value type
 */
public final class Error<T> implements Result<T> {

    private final Exception error;

    public Error(Exception error) {
        this.error = error;
    }

    @Override
    public boolean isOk() {
        return false;
    }

    @Override
    public boolean isError() {
        return true;
    }

    @Override
    public T getValue() {
        throw new UnsupportedOperationException("Error does not contain a value");
    }

    @Override
    public Exception getError() {
        return error;
    }

    @Override
    public String toString() {
        return "Error(" + error + ")";
    }
}
