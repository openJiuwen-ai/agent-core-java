/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;
import java.util.function.Function;

/**
 * Mutable state interface with read/write capabilities.
 * <p>
 * Mirrors Python's {@code StateLike}.
 */
public interface StateLike extends ReadableState, RecoverableState {

    /**
     * Update state with the given data.
     */
    void update(Map<String, Object> data);

    /**
     * Get value via transformer function.
     */
    Object getByTransformer(Function<Object, Object> transformer);
}
