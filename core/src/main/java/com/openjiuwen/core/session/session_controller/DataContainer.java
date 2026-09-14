/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Generic session data container contract.
 *
 * <p>Mirrors Python's {@code DataContainer} in
 * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
 */
public interface DataContainer {

    Object get(Object key);

    boolean update(Map<String, Object> data);

    CompletionStage<Object> dump();
}
