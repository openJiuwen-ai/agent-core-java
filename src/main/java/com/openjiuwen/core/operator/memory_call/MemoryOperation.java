/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.operator.memory_call;

import java.util.Iterator;
import java.util.Map;

/**
 * Minimal memory contract required by {@link MemoryCallOperator}.
 */
public interface MemoryOperation {

    Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception;

    default boolean supportsStream() {
        return false;
    }

    default Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("memory stream not implemented");
    }
}
