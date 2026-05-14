/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.Map;

/**
 * Minimal sample recording sink seam.
 * <p>
 * Mirrors the callback role used by Python's judge dispatcher.
 */
public interface SampleRecordingSink {

    void recordSample(Map<String, Object> sample);
}
