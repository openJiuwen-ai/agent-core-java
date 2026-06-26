/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Result type for explicit success/error outcomes.
 *
 * <p>Mirrors Python's {@code Result} alias in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 *
 * @param <T> success value type
 * @param <E> error value type
 */
public sealed interface Result<T, E> permits Ok, ErrorResult {

    boolean isOk();

    boolean isErr();

    Object msg();
}
