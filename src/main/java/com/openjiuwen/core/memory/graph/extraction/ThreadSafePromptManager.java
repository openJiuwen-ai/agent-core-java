/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe prompt manager for entity extraction prompts.
 * <p>
 * Mirrors Python's {@code ThreadSafePromptManager} class from
 * <code>memory/graph/extraction/prompts/manager.py</code>.
 *
 * <p>Manages prompt templates with thread-safe access for concurrent LLM calls.
 */
public class ThreadSafePromptManager {

    private final Map<String, String> prompts = new ConcurrentHashMap<>();

    /**
     * Register a prompt template.
     *
     * @param key    the prompt key
     * @param prompt the prompt template string
     */
    public void register(String key, String prompt) {
        prompts.put(key, prompt);
    }

    /**
     * Get a prompt template by key.
     *
     * @param key the prompt key
     * @return the prompt template, or null if not found
     */
    public String get(String key) {
        return prompts.get(key);
    }

    /**
     * Get a prompt template, returning the default if not found.
     *
     * @param key          the prompt key
     * @param defaultValue the default value
     * @return the prompt template or default
     */
    public String getOrDefault(String key, String defaultValue) {
        return prompts.getOrDefault(key, defaultValue);
    }

    /**
     * Check if a prompt is registered.
     */
    public boolean has(String key) {
        return prompts.containsKey(key);
    }

    /**
     * Get all registered prompt keys.
     */
    public java.util.Set<String> keys() {
        return prompts.keySet();
    }
}
