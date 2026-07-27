/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Registry for named chunkers.
 *
 * <p>Supports both zero-arg factories (via {@link Supplier}) and parameterized factories (via
 * {@link Function} accepting {@code Map<String, Object>}), aligned with Python's {@code
 * register_chunker / get_chunker(**kwargs)} pattern.
 */
public final class ChunkerRegistry {

  private static final Map<String, Function<Map<String, Object>, Chunker>> REGISTRY =
      new ConcurrentHashMap<>();

  static {
    registerChunker("char", ChunkerRegistry::createCharChunker);
    registerChunker("hybrid", ChunkerRegistry::createHybridChunker);
  }

  private ChunkerRegistry() {}

  /** Register a chunker with a zero-arg supplier (convenience overload). */
  public static void registerChunker(String name, Supplier<Chunker> factory) {
    registerChunker(name, kwargs -> factory.get(), false);
  }

  /**
   * Register a chunker with a parameterized factory accepting kwargs. Corresponds to Python's
   * {@code register_chunker(name, callable(**kwargs) -> Chunker)}.
   */
  public static void registerChunker(String name, Function<Map<String, Object>, Chunker> factory) {
    registerChunker(name, factory, false);
  }

  /**
   * Register a chunker with overwrite control, matching Python's register_chunker(...,
   * overwrite=False).
   */
  public static void registerChunker(
      String name, Function<Map<String, Object>, Chunker> factory, boolean canOverwrite) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("chunker name must be a non-empty string");
    }
    if (factory == null) {
      throw new IllegalArgumentException("chunker factory must not be null");
    }
    if (REGISTRY.containsKey(name) && !canOverwrite) {
      throw new IllegalArgumentException(
          "Chunker '" + name + "' is already registered. Use overwrite=true to isReplace it.");
    }
    REGISTRY.put(name, factory);
  }

  /** Get a chunker by name using default parameters. */
  public static Chunker getChunker(String name) {
    return getChunker(name, Map.of());
  }

  /**
   * Get a chunker by name, passing kwargs to the factory. Corresponds to Python's {@code
   * get_chunker(name, **kwargs)}.
   */
  public static Chunker getChunker(String name, Map<String, Object> kwargs) {
    Function<Map<String, Object>, Chunker> factory = REGISTRY.get(name);
    if (factory == null) {
      throw new IllegalArgumentException(
          "Unknown chunker: " + name + ". Registered: " + REGISTRY.keySet());
    }
    Chunker chunker = factory.apply(kwargs != null ? kwargs : Map.of());
    if (chunker == null) {
      throw new IllegalArgumentException(
          "Chunker entry '" + name + "' must return a Chunker instance");
    }
    return chunker;
  }

  /** Auto-generated for codecheck compliance. */
  public static boolean contains(String name) {
    return REGISTRY.containsKey(name);
  }

  /** Auto-generated for codecheck compliance. */
  public static void unregisterChunker(String name) {
    REGISTRY.remove(name);
  }

  private static Chunker createCharChunker(Map<String, Object> kwargs) {
    Map<String, Object> params = new java.util.LinkedHashMap<>(kwargs != null ? kwargs : Map.of());
    return new CharChunker(
        intValue(params, "chunk_size", 512), intValue(params, "chunk_overlap", 50));
  }

  @SuppressWarnings("unchecked")
  private static Chunker createHybridChunker(Map<String, Object> kwargs) {
    Map<String, Object> params = new java.util.LinkedHashMap<>(kwargs != null ? kwargs : Map.of());
    Object inner = params.remove("inner_chunker");
    int chunkSize = intValue(params, "chunk_size", 512);
    int chunkOverlap = intValue(params, "chunk_overlap", 50);
    Object noSplitWhen = params.remove("no_split_when");

    Chunker innerChunker;
    if (inner == null) {
      Chunker chunker = new CharChunker(chunkSize, chunkOverlap);
      innerChunker = chunker;
    } else if (inner instanceof Chunker chunker) {
      params.remove("chunk_size");
      params.remove("chunk_overlap");
      innerChunker = chunker;
    } else {
      throw new IllegalArgumentException("inner_chunker must be a Chunker instance");
    }
    if (!params.isEmpty()) {
      throw new IllegalArgumentException(
          "Unknown kwargs for 'hybrid' chunker: " + String.join(", ", params.keySet()));
    }
    if (noSplitWhen instanceof Predicate<?> predicate) {
      return new HybridChunker(
          innerChunker, (Predicate<com.openjiuwen.core.retrieval.common.Document>) predicate);
    }
    return new HybridChunker(innerChunker);
  }

  private static int intValue(Map<String, Object> kwargs, String key, int defaultValue) {
    Object value = kwargs != null ? kwargs.remove(key) : null;
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String string && !string.isBlank()) {
      return Integer.parseInt(string);
    }
    return defaultValue;
  }
}
