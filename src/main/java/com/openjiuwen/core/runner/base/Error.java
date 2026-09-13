/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Represents a failed operation result following the Result pattern.
 * <p>
 * Mirrors Python's {@code Error} class.
 * 
 * @since 0.1.7
 */
public final class Error<T> implements Result<T> {
    private final Exception error;

    /**
     * Error.
     * 
     * @param error error
     * @since 0.1.7
     */
    public Error(Exception error) {
        this.error = error;
    }

    /**
     * isOk.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isOk() {
        return false;
    }

    /**
     * isError.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isError() {
        return true;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public T getValue() {
        throw new UnsupportedOperationException("Error does not contain a value");
    }

    /**
     * getError.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Exception getError() {
        return error;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "Error(" + error + ")";
    }
}
