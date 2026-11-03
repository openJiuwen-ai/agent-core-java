/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.prompts;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code PromptSection} in
 * {@code openjiuwen/core/single_agent/prompts/builder.py}.
 */
public class PromptSection {
    private final String name;
    private final Map<String, String> content;
    private final int priority;

    public PromptSection(String name, Map<String, String> content) {
        this(name, content, 100);
    }

    public PromptSection(String name, Map<String, String> content, int priority) {
        this.name = Objects.requireNonNull(name, "name");
        this.content = new LinkedHashMap<>(Objects.requireNonNull(content, "content"));
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getContent() {
        return new LinkedHashMap<>(content);
    }

    public int getPriority() {
        return priority;
    }

    public String render() {
        return render(SystemPromptBuilder.DEFAULT_LANGUAGE);
    }

    public String render(String language) {
        if (language != null && content.containsKey(language)) {
            return content.get(language);
        }
        if (content.containsKey(SystemPromptBuilder.DEFAULT_LANGUAGE)) {
            return content.get(SystemPromptBuilder.DEFAULT_LANGUAGE);
        }
        Iterator<String> iterator = content.values().iterator();
        return iterator.hasNext() ? iterator.next() : "";
    }

    public int charCount() {
        return charCount(SystemPromptBuilder.DEFAULT_LANGUAGE);
    }

    public int charCount(String language) {
        return render(language).length();
    }
}
