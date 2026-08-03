/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.Iterator;
import java.util.Map;

/**
 * Narrow session surface consumed by controller infrastructure.
 *
 * <p>Mirrors Python's {@code Controller} session dependency in
 * {@code openjiuwen/core/controller/base.py}.</p>
 */
public interface AgentSessionApi {

    String getSessionId();

    Object getState(String key);

    void updateState(Map<String, Object> data);

    void writeStream(Object data);

    Iterator<Object> streamIterator();
}
