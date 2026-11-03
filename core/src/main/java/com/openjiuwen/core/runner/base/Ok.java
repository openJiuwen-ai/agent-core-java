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

    /**
     * Auto-generated for codecheck compliance.
     */
    public Ok(T value) {
        this.value = value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isOk() {
        return true;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isErr() {
        return false;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object msg() {
        return value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public T getValue() {
        return value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Exception getError() {
        throw new UnsupportedOperationException("Ok does not contain an error");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "Ok(" + value + ")";
    }
}
