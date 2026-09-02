/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.prompt;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Singleton prompt applier that loads .md prompt templates from classpath resources
 * and applies variable substitution.
 * 
 * @since 0.1.7
 */
public class PromptApplier {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final String PROMPT_RESOURCE_DIR = "memory/prompt/";

    private static volatile PromptApplier instance;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, PromptTemplate> promptCache = new ConcurrentHashMap<>();

    /**
     * PromptApplier.
     * 
     * @since 0.1.7
     */
    private PromptApplier() {
        MEMORY_LOGGER.info("PromptApplier singleton initialized");
    }

    /**
     * getInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static PromptApplier getInstance() {
        if (instance == null) {
            synchronized (PromptApplier.class) {
                if (instance == null) {
                    instance = new PromptApplier();
                }
            }
        }
        return instance;
    }

    /**
     * loadPromptTemplate.
     * 
     * @param filePrefix filePrefix
     * @return the result
     * @since 0.1.7
     */
    private PromptTemplate loadPromptTemplate(String filePrefix) {
        PromptTemplate cached = promptCache.get(filePrefix);
        if (cached != null) {
            return cached;
        }

        String resourcePath = PROMPT_RESOURCE_DIR + filePrefix + ".md";
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Prompt file not found: " + resourcePath);
            }
            String content;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }
            PromptTemplate template = PromptTemplate.builder().content(content).build();
            promptCache.put(filePrefix, template);
            return template;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt template: " + resourcePath, e);
        }
    }

    /**
     * apply.
     * 
     * @param filePrefix filePrefix
     * @param variables variables
     * @return the result
     * @since 0.1.7
     */
    public String apply(String filePrefix, Map<String, Object> variables) {
        PromptTemplate template = loadPromptTemplate(filePrefix);
        Object content = template.format(variables).getContent();
        return content == null ? "" : String.valueOf(content);
    }

    /**
     * clearCache.
     * 
     * @param filePrefix filePrefix
     * @since 0.1.7
     */
    public void clearCache(String filePrefix) {
        if (filePrefix == null) {
            promptCache.clear();
        } else {
            promptCache.remove(filePrefix);
        }
    }

    /**
     * clearCache.
     * 
     * @since 0.1.7
     */
    public void clearCache() {
        clearCache(null);
    }

    /**
     * getTemplate.
     * 
     * @param filePrefix filePrefix
     * @return the result
     * @since 0.1.7
     */
    public PromptTemplate getTemplate(String filePrefix) {
        return loadPromptTemplate(filePrefix);
    }
}
