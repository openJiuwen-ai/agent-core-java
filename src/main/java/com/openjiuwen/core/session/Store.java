/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.Map;

/**
 * Mirrors Python's {@code Store} in
 * {@code openjiuwen/core/session/store.py}.
 */
public interface Store {

    Object read(Object key);

    void write(Map<String, Object> value);
}
