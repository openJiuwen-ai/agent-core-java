/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor;

import java.util.Map;

/**
 * Generic retrieval processor abstraction.
 * 
 * @since 0.1.7
 */
public interface Processor<I, O> {
    /**
     * process.
     * 
     * @param input input
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    O process(I input, Map<String, Object> options);
}
