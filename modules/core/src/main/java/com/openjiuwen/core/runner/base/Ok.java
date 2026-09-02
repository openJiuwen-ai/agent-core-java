/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Represents a successful operation result following the Result pattern.
 * <p>
 * Mirrors Python's {@code Ok} class.
 * 
 * @since 0.1.7
 */
public final class Ok<T> implements Result<T> {
    private final T value;

    /**
     * Ok.
     * 
     * @param value value
     * @since 0.1.7
     */
    public Ok(T value) {
        this.value = value;
    }

    /**
     * isOk.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isOk() {
        return true;
    }

    /**
     * isError.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isError() {
        return false;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public T getValue() {
        return value;
    }

    /**
     * getError.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Exception getError() {
        throw new UnsupportedOperationException("Ok does not contain an error");
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "Ok(" + value + ")";
    }
}
