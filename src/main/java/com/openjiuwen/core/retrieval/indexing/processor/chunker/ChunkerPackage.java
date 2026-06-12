/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge and registry for chunker exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.retrieval.indexing.processor.chunker} module in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py}.
 * </p>
 */
public final class ChunkerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py";
    public static final Class<Chunker> CHUNKER = Chunker.class;
    public static final Class<CharChunker> CHAR_CHUNKER = CharChunker.class;
    public static final Class<HybridChunker> HYBRID_CHUNKER = HybridChunker.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Chunker",
            "CharChunker",
            "HybridChunker",
            "CHUNKER_REGISTRY",
            "register_chunker",
            "get_chunker"
    );

    private static final Map<String, ChunkerFactory> CHUNKER_REGISTRY = new LinkedHashMap<>();

    static {
        registerChunker("char", options -> new CharChunker(options.getChunkSize(), options.getChunkOverlap()), true);
        registerChunker("hybrid", ChunkerPackage::hybridFactory, true);
    }

    private ChunkerPackage() {
    }

    public static Map<String, ChunkerFactory> chunkerRegistry() {
        return Map.copyOf(CHUNKER_REGISTRY);
    }

    public static List<String> registeredChunkerNames() {
        return List.copyOf(CHUNKER_REGISTRY.keySet());
    }

    public static void registerChunker(String name, ChunkerFactory chunkerFactory) {
        registerChunker(name, chunkerFactory, false);
    }

    public static void registerChunker(String name, ChunkerFactory chunkerFactory, boolean overwrite) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("chunker name must be a non-empty string");
        }
        if (chunkerFactory == null) {
            throw new IllegalArgumentException("chunker factory must not be null");
        }
        if (CHUNKER_REGISTRY.containsKey(name) && !overwrite) {
            throw new IllegalArgumentException("Chunker '" + name + "' is already registered. "
                    + "Use overwrite=true to replace it.");
        }
        CHUNKER_REGISTRY.put(name, chunkerFactory);
    }

    public static Chunker getChunker(String name) {
        return getChunker(name, ChunkerOptions.defaults());
    }

    public static Chunker getChunker(String name, ChunkerOptions options) {
        if (!CHUNKER_REGISTRY.containsKey(name)) {
            throw new IllegalArgumentException("Unknown chunker: " + name + ". Registered: " + registeredChunkerNames());
        }
        Chunker chunker = CHUNKER_REGISTRY.get(name).create(options == null ? ChunkerOptions.defaults() : options);
        if (chunker == null) {
            throw new IllegalStateException("Chunker entry '" + name + "' must return a Chunker instance");
        }
        return chunker;
    }

    public static Chunker getChunker(String name, Map<String, ?> keywordArgs) {
        return getChunker(name, ChunkerOptions.fromKeywordArgs(keywordArgs));
    }

    private static Chunker hybridFactory(ChunkerOptions options) {
        Chunker innerChunker = options.getInnerChunker();
        if (innerChunker == null) {
            innerChunker = new CharChunker(options.getChunkSize(), options.getChunkOverlap());
        } else if (!options.getUnknownKeywordNames().isEmpty()) {
            List<String> unknownNames = new ArrayList<>(options.getUnknownKeywordNames());
            unknownNames.sort(Comparator.naturalOrder());
            throw new IllegalArgumentException("Unknown kwargs for 'hybrid' chunker: " + String.join(", ", unknownNames));
        }
        return new HybridChunker(innerChunker, options.getNoSplitWhen());
    }

    /**
     * Mirrors Python's {@code ChunkerEntry} callable in
     * {@code openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py}.
     */
    @FunctionalInterface
    public interface ChunkerFactory {

        Chunker create(ChunkerOptions options);
    }
}
