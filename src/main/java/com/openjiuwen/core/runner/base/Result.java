/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Result type for type-safe error handling.
 * <p>
 * Mirrors Python's {@code Result = Ok | Error} pattern.
 * 
 * @since 0.1.7
 */
public sealed interface Result<T> permits Ok, Error {
    /**
     * isOk.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isOk();

    /**
     * isError.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isError();

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    T getValue();

    /**
     * getError.
     * 
     * @return the result
     * @since 0.1.7
     */
    Exception getError();
}
