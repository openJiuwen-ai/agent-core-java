/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Markdown template loader for agent-team prompts.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/prompts/loader.py}.</p>
 */
public final class PromptLoader {

    public static final String DEFAULT_LANGUAGE = "cn";
    private static final String RESOURCE_ROOT = "openjiuwen/agent_teams/prompts/";
    private static final Map<CacheKey, PromptTemplate> CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {
    }

    public static PromptTemplate loadTemplate(String name) {
        return loadTemplate(name, DEFAULT_LANGUAGE);
    }

    public static PromptTemplate loadTemplate(String name, String language) {
        return load(name, language);
    }

    public static PromptTemplate loadSharedTemplate(String name) {
        return load(name, null);
    }

    static void clearCacheForTests() {
        CACHE.clear();
    }

    private static PromptTemplate load(String name, String language) {
        CacheKey key = new CacheKey(name, language == null || language.isEmpty() ? null : language);
        return CACHE.computeIfAbsent(key, PromptLoader::readTemplate);
    }

    private static PromptTemplate readTemplate(CacheKey key) {
        String resourceName = key.language() == null
                ? RESOURCE_ROOT + key.name() + ".md"
                : RESOURCE_ROOT + key.language() + "/" + key.name() + ".md";
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = PromptLoader.class.getClassLoader();
        }
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new UncheckedIOException(new NoSuchFileException(resourceName));
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return PromptTemplate.builder()
                    .name(key.name())
                    .content(content)
                    .build();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record CacheKey(String name, String language) {
    }
}
