/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.base;

/**
 * Result type for type-safe error handling.
 * <p>
 * Mirrors Python's {@code Result = Ok | Error} pattern.
 *
 * @param <T> the success value type
 */
public sealed interface Result<T> permits Ok, Error {

    boolean isOk();

    boolean isError();

    T getValue();

    Exception getError();
}
