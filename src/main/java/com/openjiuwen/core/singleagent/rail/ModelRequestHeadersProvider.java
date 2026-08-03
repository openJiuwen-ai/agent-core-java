/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ModelRequestHeadersProvider {
    CompletionStage<Map<String, String>> provide(AgentCallbackContext context);
}
