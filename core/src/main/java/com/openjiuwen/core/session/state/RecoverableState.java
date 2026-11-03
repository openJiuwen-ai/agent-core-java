/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Mirrors Python's {@code RecoverableStateLike} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public interface RecoverableState {

    Map<String, Object> getState();

    void setState(Map<String, Object> state);
}
