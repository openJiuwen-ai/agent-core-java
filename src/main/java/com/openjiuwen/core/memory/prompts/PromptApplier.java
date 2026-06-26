/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.prompts;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads, caches, and applies memory prompt markdown templates.
 *
 * <p>Mirrors Python's {@code PromptApplier} in
 * {@code openjiuwen/core/memory/prompts/prompt_applier.py}.</p>
 */
public class PromptApplier {

    private static final String PROMPT_RESOURCE_DIR = "openjiuwen/core/memory/prompts";
    private static final Map<String, PromptTemplate> PROMPT_CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean INITIALIZED_LOGGED = new AtomicBoolean(false);
    private static final PromptApplier INSTANCE = new PromptApplier(false);

    public PromptApplier() {
        this(true);
    }

    private PromptApplier(boolean logInitialization) {
        if (logInitialization && INITIALIZED_LOGGED.compareAndSet(false, true)) {
            Loggers.MEMORY.info("PromptApplier singleton initialized");
        }
    }

    public static PromptApplier getInstance() {
        return INSTANCE;
    }

    public String apply(String filePrefix, Map<String, String> variables) {
        PromptTemplate template = loadPromptTemplate(filePrefix);
        PromptTemplate formatted = template.format(toObjectMap(variables));
        Object content = formatted.getContent();
        String result = content == null ? "" : String.valueOf(content);
        Loggers.MEMORY.debug("Applied prompt template: {}", filePrefix);
        return result;
    }

    public void clearCache() {
        clearCache(null);
    }

    public void clearCache(String filePrefix) {
        if (filePrefix == null) {
            PROMPT_CACHE.clear();
            Loggers.MEMORY.info("Cleared all prompt template cache");
        } else if (PROMPT_CACHE.remove(filePrefix) != null) {
            Loggers.MEMORY.info("Cleared prompt template cache: {}", filePrefix);
        }
    }

    public PromptTemplate getTemplate(String filePrefix) {
        return loadPromptTemplate(filePrefix);
    }

    private PromptTemplate loadPromptTemplate(String filePrefix) {
        Objects.requireNonNull(filePrefix, "filePrefix");
        PromptTemplate cached = PROMPT_CACHE.get(filePrefix);
        if (cached != null) {
            Loggers.MEMORY.debug("Using cached prompt template: {}", filePrefix);
            return cached;
        }

        PromptTemplate loaded = readPromptTemplate(filePrefix);
        PromptTemplate previous = PROMPT_CACHE.putIfAbsent(filePrefix, loaded);
        PromptTemplate result = previous == null ? loaded : previous;
        if (previous == null) {
            Loggers.MEMORY.info("Loaded and cached prompt template: {}", filePrefix);
        }
        return result;
    }

    private PromptTemplate readPromptTemplate(String filePrefix) {
        String resourcePath = PROMPT_RESOURCE_DIR + "/" + filePrefix + ".md";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = PromptApplier.class.getClassLoader();
        }
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Prompt file not found: " + resourcePath);
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return PromptTemplate.builder().content(content).build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read prompt file: " + resourcePath, exception);
        }
    }

    private static Map<String, Object> toObjectMap(Map<String, String> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values != null) {
            result.putAll(values);
        }
        return result;
    }
}
