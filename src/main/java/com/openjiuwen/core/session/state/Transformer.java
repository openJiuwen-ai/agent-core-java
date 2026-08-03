/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.function.Function;

/**
 * Mirrors Python's {@code Transformer} callable alias in
 * {@code openjiuwen/core/session/state/base.py}.
 */
@FunctionalInterface
public interface Transformer extends Function<Object, Object> {
    @Override
    Object apply(Object state);
}
