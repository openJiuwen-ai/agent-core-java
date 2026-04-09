/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Recoverable state interface supporting snapshot and restore.
 * <p>
 * Mirrors Python's {@code RecoverableStateLike}.
 */
public interface RecoverableState {

    /**
     * Get full state as a map.
     */
    Map<String, Object> getState();

    /**
     * Set full state from a map.
     */
    void setState(Map<String, Object> state);
}
