/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code StateLike} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public interface StateLike extends ReadableState, RecoverableState {

    void update(Map<String, Object> data);

    Object getByTransformer(Function<Object, Object> transformer);
}
