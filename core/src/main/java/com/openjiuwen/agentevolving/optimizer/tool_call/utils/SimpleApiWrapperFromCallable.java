/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import java.util.Map;

/**
 * Callable-specialized wrapper for direct tool injection.
 *
 * <p>Mirrors Python's {@code SimpleAPIWrapperFromCallable} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_api.py}.</p>
 */
public class SimpleApiWrapperFromCallable extends SimpleApiWrapper {

    public SimpleApiWrapperFromCallable(Object toolCallable, String name, Map<String, Object> config) {
        super(null, name, Map.of(name, toolCallable), null);
    }
}
