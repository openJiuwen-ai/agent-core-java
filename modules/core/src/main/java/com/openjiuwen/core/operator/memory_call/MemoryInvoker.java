/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import java.util.Map;

/**
 * Callback hook for non-standard memory invocation flows.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface MemoryInvoker {
    /**
     * invoke.
     * 
     * @param inputs inputs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Object invoke(Map<String, Object> inputs) throws Exception;
}
