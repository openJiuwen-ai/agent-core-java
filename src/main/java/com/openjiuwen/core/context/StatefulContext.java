/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.context;

import java.util.Map;

/**
 * Interface for ModelContext implementations that support state persistence.
 * <p>
 * Replaces the {@code hasattr(context, "save_state")} / {@code hasattr(context, "load_state")}
 * duck-typing pattern in Python, allowing any ModelContext subclass to participate
 * in ContextEngine's persistence flow without being tied to {@code SessionModelContext}.
 */
public interface StatefulContext {

    /**
     * Save context state for persistence.
     *
     * @return a serialisable map of the internal state
     */
    Map<String, Object> saveState();

    /**
     * Load context state from persistence.
     *
     * @param state the previously saved state map
     */
    void loadState(Map<String, Object> state);
}
