/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor;

import java.util.Map;

/**
 * Generic retrieval processor abstraction.
 */
public interface Processor<I, O> {

    O process(I input, Map<String, Object> options);
}
