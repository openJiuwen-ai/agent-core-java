/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Registry for named chunkers.
 *
 * <p>Supports both zero-arg factories (via {@link Supplier}) and parameterized
 * factories (via {@link Function} accepting {@code Map<String, Object>}),
 * aligned with Python's {@code register_chunker / get_chunker(**kwargs)} pattern.</p>
 */
public final class ChunkerRegistry {

    private static final Map<String, Function<Map<String, Object>, Object>> REGISTRY = new ConcurrentHashMap<>();
    private static final Set<String> BUILT_INS = Set.of("char", "token", "text", "hybrid");

    static {
        registerChunker("char", kwargs -> new CharChunker(
                intValue(kwargs, "chunk_size", 512),
                intValue(kwargs, "chunk_overlap", 50)),
                true);
        registerChunker("token", kwargs -> new TokenizerChunker(
                intValue(kwargs, "chunk_size", 512),
                intValue(kwargs, "chunk_overlap", 50)),
                true);
        registerChunker("text", kwargs -> new TextChunker(
                intValue(kwargs, "chunk_size", 512),
                intValue(kwargs, "chunk_overlap", 50),
                String.valueOf(kwargs.getOrDefault("chunk_unit", "token"))),
                true);
        registerChunker("hybrid", ChunkerRegistry::buildHybridChunker, true);
    }

    private ChunkerRegistry() {
    }

    public static void registerChunker(String name, Function<Map<String, Object>, Object> factory) {
        registerChunker(name, factory, false);
    }

    public static void registerChunker(String name, Function<Map<String, Object>, Object> factory, boolean overwrite) {
        validateName(name);
        if (factory == null) {
            throw new IllegalArgumentException("chunker factory must not be null");
        }
        if (REGISTRY.containsKey(name) && !overwrite) {
            throw new IllegalArgumentException(
                    "Chunker '" + name + "' is already registered. Use overwrite=true to replace it.");
        }
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
        validateName(name);
        Function<Map<String, Object>, Object> factory = REGISTRY.get(name);
        if (factory == null) {
            throw new NoSuchElementException("Unknown chunker: " + name + ". Registered: " + REGISTRY.keySet());
        }
        Map<String, Object> params = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        Object chunker = factory.apply(params);
        if (!(chunker instanceof Chunker typedChunker)) {
            throw new IllegalArgumentException(
                    "Chunker entry '" + name + "' must return a Chunker instance, got "
                            + (chunker == null ? "null" : chunker.getClass().getSimpleName()));
        }
        return typedChunker;
    }

    public static boolean isRegistered(String name) {
        return REGISTRY.containsKey(name);
    }

    private static Object buildHybridChunker(Map<String, Object> kwargs) {
        Map<String, Object> params = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        Object inner = params.remove("inner_chunker");
        int chunkSize = intValue(params, "chunk_size", 512);
        int chunkOverlap = intValue(params, "chunk_overlap", 50);
        Predicate<Document> noSplitWhen = predicateValue(params.remove("no_split_when"));

        if (inner == null) {
            if (!params.isEmpty()) {
                throw new IllegalArgumentException("Unknown kwargs for 'hybrid' chunker: " + String.join(", ", params.keySet()));
            }
            return new HybridChunker(new CharChunker(chunkSize, chunkOverlap), noSplitWhen);
        }
        if (!(inner instanceof Chunker innerChunker)) {
            throw new IllegalArgumentException("inner_chunker must be a Chunker instance");
        }
        if (!params.isEmpty()) {
            throw new IllegalArgumentException("Unknown kwargs for 'hybrid' chunker: " + String.join(", ", params.keySet()));
        }
        return new HybridChunker(innerChunker, noSplitWhen);
    }

    private static int intValue(Map<String, Object> kwargs, String key, int defaultValue) {
        Object raw = kwargs.remove(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Document> predicateValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Predicate<?> predicate) {
            return (Predicate<Document>) predicate;
        }
        throw new IllegalArgumentException("no_split_when must be a Predicate<Document>");
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("chunker name must be a non-empty string");
        }
    }
}
