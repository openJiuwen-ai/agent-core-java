/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.Map;

/**
 * Lifecycle hooks supported by stream-capable agent sessions.
 */
public interface AgentSessionLifecycle {

    AgentSessionLifecycle preRun(Map<String, Object> kwargs);

    void closeStream();

    void commit();
}
