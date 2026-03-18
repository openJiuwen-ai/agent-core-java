/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.List;
import java.util.Map;

/**
 * Triple extractor abstraction.
 */
public abstract class Extractor implements Processor<List<TextChunk>, List<Triple>> {

    public abstract List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options);

    @Override
    public List<Triple> process(List<TextChunk> input, Map<String, Object> options) {
        return extract(input, options);
    }
}
