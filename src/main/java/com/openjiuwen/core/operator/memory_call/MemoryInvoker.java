/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.operator.memory_call;

import java.util.Map;

/**
 * Callback hook for non-standard memory invocation flows.
 */
@FunctionalInterface
public interface MemoryInvoker {

    Object invoke(Map<String, Object> inputs) throws Exception;
}
