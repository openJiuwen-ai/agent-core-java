/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Successful result wrapper.
 *
 * <p>Mirrors Python's {@code Ok} in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 *
 * @param <T> success value type
 */
public final class Ok<T> implements Result<T, Void> {

    private final T value;

    public Ok(T value) {
        this.value = value;
    }

    @Override
    public boolean isOk() {
        return true;
    }

    @Override
    public boolean isErr() {
        return false;
    }

    @Override
    public T msg() {
        return value;
    }

    public T value() {
        return value;
    }
}
