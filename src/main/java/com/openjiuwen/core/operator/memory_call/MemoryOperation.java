/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import java.util.Iterator;
import java.util.Map;

/**
 * Minimal memory contract required by {@link MemoryCallOperator}.
 * 
 * @since 0.1.7
 */
public interface MemoryOperation {
    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception;

    /**
     * supportsStream.
     * 
     * @return the result
     * @since 0.1.7
     */
    default boolean supportsStream() {
        return false;
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    default Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("memory stream not implemented");
    }
}
