/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.List;
import java.util.Map;

/**
 * Triple extractor abstraction.
 * 
 * @since 0.1.7
 */
public abstract class Extractor implements Processor<List<TextChunk>, List<Triple>> {
    /**
     * extract.
     * 
     * @param chunks chunks
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public abstract List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options);

    /**
     * process.
     * 
     * @param input input
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Triple> process(List<TextChunk> input, Map<String, Object> options) {
        return extract(input, options);
    }
}
