/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Map;

/**
 * Factory provider for a checkpointer implementation.
 *
 * <p>Mirrors Python's {@code CheckpointerProvider} in
 * {@code openjiuwen/core/session/checkpointer/checkpointer.py}.</p>
 */
public interface CheckpointerProvider {

    Checkpointer create(Map<String, Object> conf);
}
