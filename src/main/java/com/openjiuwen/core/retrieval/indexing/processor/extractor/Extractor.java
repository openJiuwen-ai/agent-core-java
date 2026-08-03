/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code Extractor} in
 * {@code openjiuwen/core/retrieval/indexing/processor/extractor/base.py}.
 */
public abstract class Extractor implements Processor<List<Triple>> {

    public abstract CompletableFuture<List<Triple>> extract(List<TextChunk> chunks);

    public CompletableFuture<List<Triple>> process(List<TextChunk> chunks) {
        return extract(chunks);
    }

    @Override
    public CompletableFuture<List<Triple>> process(Object... args) {
        @SuppressWarnings("unchecked")
        List<TextChunk> chunks = (List<TextChunk>) args[0];
        return process(chunks);
    }
}
