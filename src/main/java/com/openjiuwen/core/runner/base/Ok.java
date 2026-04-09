  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.base;

/**
 * Represents a successful operation result following the Result pattern.
 * <p>
 * Mirrors Python's {@code Ok} class.
 *
 * @param <T> the success value type
 */
public final class Ok<T> implements Result<T> {

    private final T value;

    public Ok(T value) {
        this.value = value;
    }

    @Override
    public boolean isOk() {
        return true;
    }

    @Override
    public boolean isError() {
        return false;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public Exception getError() {
        throw new UnsupportedOperationException("Ok does not contain an error");
    }

    @Override
    public String toString() {
        return "Ok(" + value + ")";
    }
}
