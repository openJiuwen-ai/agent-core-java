/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared state surface used by {@code BaseSession} callers.
 *
 * <p>Mirrors Python's duck-typed state access in
 * {@code openjiuwen/core/session/state/base.py}.</p>
 */
public interface SessionStateAccess {

    Object get(Object key);

    void update(Map<String, Object> data);

    default Object getGlobal(Object key) {
        return null;
    }

    default void updateGlobal(Map<String, Object> data) {
    }

    default void updateTrace(Object span) {
    }

    default Map<String, Object> dump() {
        return new LinkedHashMap<>();
    }

    default Map<String, Object> getState() {
        return dump();
    }

    default void setState(Map<String, Object> state) {
    }
}
