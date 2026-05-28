/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.prompts;

import java.util.*;

/**
 * A single prompt section with multilingual content.
 * <p>
 * Mirrors Python's {@code PromptSection} in
 * {@code openjiuwen.core.single_agent.prompts.builder}.
 */
public class PromptSection {

    /** Supported languages. */
    public static final String[] SUPPORTED_LANGUAGES = {"cn", "en"};
    /** Default language. */
    public static final String DEFAULT_LANGUAGE = "cn";

    private final String name;
    private final Map<String, String> content;
    private final int priority;

    public PromptSection(String name, Map<String, String> content, int priority) {
        this.name = name;
        this.content = new LinkedHashMap<>(content);
        this.priority = priority;
    }

    public PromptSection(String name, Map<String, String> content) {
        this(name, content, 100);
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getContent() {
        return Collections.unmodifiableMap(content);
    }

    public int getPriority() {
        return priority;
    }

    /** Render this section for the given language. */
    public String render(String language) {
        if (content.containsKey(language)) {
            return content.get(language);
        }
        return content.getOrDefault(DEFAULT_LANGUAGE,
                content.values().iterator().hasNext() ? content.values().iterator().next() : "");
    }

    /** Render in default language. */
    public String render() {
        return render(DEFAULT_LANGUAGE);
    }

    /** Character count for the given language. */
    public int charCount(String language) {
        return render(language).length();
    }
}
