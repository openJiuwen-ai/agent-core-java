/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

/**
 * Mirrors Python's {@code ReadableStateLike} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public interface ReadableState {

    Object get(Object key);

    Object getByPrefix(Object key, String nestedPrefix);
}
