/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registry for named chunkers.
 *
 * <p>Supports both zero-arg factories (via {@link Supplier}) and parameterized
 * factories (via {@link Function} accepting {@code Map<String, Object>}),
 * aligned with Python's {@code register_chunker / get_chunker(**kwargs)} pattern.</p>
 */
public final class ChunkerRegistry {

    private static final Map<String, Function<Map<String, Object>, Chunker>> REGISTRY = new ConcurrentHashMap<>();

    static {
        registerChunker("char", () -> new CharChunker(512, 50));
        registerChunker("token", () -> new TokenizerChunker(512, 50));
        registerChunker("text", () -> new TextChunker(512, 50, "token"));
        registerChunker("hybrid", () -> new HybridChunker(new TextChunker(512, 50, "token")));
    }

    private ChunkerRegistry() {
    }

    /**
     * Register a chunker with a zero-arg supplier (convenience overload).
     */
    public static void registerChunker(String name, Supplier<Chunker> factory) {
        REGISTRY.put(name, kwargs -> factory.get());
    }

    /**
     * Register a chunker with a parameterized factory accepting kwargs.
     * Corresponds to Python's {@code register_chunker(name, callable(**kwargs) -> Chunker)}.
     */
    public static void registerChunker(String name, Function<Map<String, Object>, Chunker> factory) {
        REGISTRY.put(name, factory);
    }

    /**
     * Get a chunker by name using default parameters.
     */
    public static Chunker getChunker(String name) {
        return getChunker(name, Map.of());
    }

    /**
     * Get a chunker by name, passing kwargs to the factory.
     * Corresponds to Python's {@code get_chunker(name, **kwargs)}.
     */
    public static Chunker getChunker(String name, Map<String, Object> kwargs) {
        Function<Map<String, Object>, Chunker> factory = REGISTRY.get(name);
        if (factory == null) {
            return null;
        }
        return factory.apply(kwargs != null ? kwargs : Map.of());
    }
}
