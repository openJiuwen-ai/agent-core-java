/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.Map;

/**
 * Sample recording sink seam for delayed judge dispatch.
 * <p>
 * Mirrors the callback role used by
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/judge_dispatcher.py}.
 */
public interface SampleRecordingSink {

    void recordSample(Map<String, Object> sample);
}
