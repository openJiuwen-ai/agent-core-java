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
 */
public class PromptApplier {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final String PROMPT_RESOURCE_DIR = "memory/prompt/";

    private static volatile PromptApplier instance;
    private final ConcurrentHashMap<String, PromptTemplate> promptCache = new ConcurrentHashMap<>();

    private PromptApplier() {
        MEMORY_LOGGER.info("PromptApplier singleton initialized");
    }

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

    public String apply(String filePrefix, Map<String, Object> variables) {
        PromptTemplate template = loadPromptTemplate(filePrefix);
        Object content = template.format(variables).getContent();
        return content == null ? "" : String.valueOf(content);
    }

    public void clearCache(String filePrefix) {
        if (filePrefix == null) {
            promptCache.clear();
        } else {
            promptCache.remove(filePrefix);
        }
    }

    public void clearCache() {
        clearCache(null);
    }

    public PromptTemplate getTemplate(String filePrefix) {
        return loadPromptTemplate(filePrefix);
    }
}
