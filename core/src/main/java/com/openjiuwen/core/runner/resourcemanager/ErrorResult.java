/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Failed result wrapper.
 *
 * <p>Mirrors Python's {@code Error} in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 *
 * @param <E> error value type
 */
public final class ErrorResult<E> implements Result<Void, E> {

    private final E error;

    public ErrorResult() {
        this(null);
    }

    public ErrorResult(E error) {
        this.error = error;
    }

    @Override
    public boolean isOk() {
        return false;
    }

    @Override
    public boolean isErr() {
        return true;
    }

    @Override
    public E msg() {
        return error;
    }

    public E error() {
        return error;
    }
}
