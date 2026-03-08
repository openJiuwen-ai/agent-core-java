/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry for named chunkers.
 */
public final class ChunkerRegistry {

    private static final Map<String, Supplier<Chunker>> REGISTRY = new ConcurrentHashMap<>();

    static {
        registerChunker("char", () -> new CharChunker(512, 50));
        registerChunker("token", () -> new TokenizerChunker(512, 50));
        registerChunker("text", () -> new TextChunker(512, 50, "token"));
        registerChunker("hybrid", () -> new HybridChunker(new TextChunker(512, 50, "token")));
    }

    private ChunkerRegistry() {
    }

    public static void registerChunker(String name, Supplier<Chunker> factory) {
        REGISTRY.put(name, factory);
    }

    public static Chunker getChunker(String name) {
        Supplier<Chunker> supplier = REGISTRY.get(name);
        return supplier == null ? null : supplier.get();
    }
}
