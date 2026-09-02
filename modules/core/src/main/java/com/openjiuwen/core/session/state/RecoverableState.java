/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Recoverable state interface supporting snapshot and restore.
 * <p>
 * Mirrors Python's {@code RecoverableStateLike}.
 * 
 * @since 0.1.7
 */
public interface RecoverableState {
    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> getState();

    /**
     * Set full state from a map.
     * 
     * @param state state
     * @since 0.1.7
     */
    void setState(Map<String, Object> state);
}
