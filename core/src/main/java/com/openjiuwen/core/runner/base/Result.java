/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Backward-compatible single-generic result contract for runner resource APIs.
 *
 * <p>Mirrors Python's {@code Result} alias in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 *
 * @param <T> success value type
 */
public interface Result<T> {

    boolean isOk();

    boolean isErr();

    Object msg();

    default T getValue() {
        return isOk() ? (T) msg() : null;
    }

    default boolean isError() {
        return isErr();
    }

    default Object getError() {
        return isErr() ? msg() : null;
    }
}
