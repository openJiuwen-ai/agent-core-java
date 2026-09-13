/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;
import java.util.function.Function;

/**
 * Mutable state interface with read/write capabilities.
 * <p>
 * Mirrors Python's {@code StateLike}.
 * 
 * @since 0.1.7
 */
public interface StateLike extends ReadableState, RecoverableState {
    /**
     * update.
     * 
     * @param data data
     * @since 0.1.7
     */
    void update(Map<String, Object> data);

    /**
     * Get value via transformer function.
     * 
     * @param transformer transformer
     * @return the result
     * @since 0.1.7
     */
    Object getByTransformer(Function<Object, Object> transformer);
}
