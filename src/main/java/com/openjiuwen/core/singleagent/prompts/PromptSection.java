/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.prompts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named system-prompt section with multilingual content.
 * 
 * @since 0.1.7
 */
public class PromptSection {
    /**
     * DEFAULT_LANGUAGE.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_LANGUAGE = "cn";

    private final String name;
    private final Map<String, String> content;
    private final int priority;

    /**
     * Create a prompt section.
     * 
     * @param name section name
     * @param content multilingual content map
     * @param priority section ordering priority
     * @since 0.1.7
     */
    public PromptSection(String name, Map<String, String> content, int priority) {
        this.name = name != null ? name : "";
        this.content =
            content != null ? new LinkedHashMap<String, String>(content) : new LinkedHashMap<String, String>();
        this.priority = priority;
    }

    /**
     * Return the section name.
     * 
     * @return section name
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * Return a defensive copy of section content.
     * 
     * @return localized content map
     * @since 0.1.7
     */
    public Map<String, String> getContent() {
        return new LinkedHashMap<String, String>(content);
    }

    /**
     * Return the section priority.
     * 
     * @return section priority
     * @since 0.1.7
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Render the section in the requested language.
     * 
     * @param language target language code
     * @return rendered content
     * @since 0.1.7
     */
    public String render(String language) {
        if (language != null && content.containsKey(language)) {
            return content.get(language);
        }
        if (content.containsKey(DEFAULT_LANGUAGE)) {
            return content.get(DEFAULT_LANGUAGE);
        }
        return content.isEmpty() ? "" : content.values().iterator().next();
    }

    /**
     * Count rendered characters in the requested language.
     * 
     * @param language target language code
     * @return rendered character count
     * @since 0.1.7
     */
    public int charCount(String language) {
        return render(language).length();
    }
}
