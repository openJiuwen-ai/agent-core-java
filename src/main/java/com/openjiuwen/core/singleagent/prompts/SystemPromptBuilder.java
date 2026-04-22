/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.prompts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Section-based system prompt builder.
 *
 * @since 0.1.7
 */
public class SystemPromptBuilder {
    private final String language;
    private final Map<String, PromptSection> sections = new LinkedHashMap<String, PromptSection>();

    /**
     * Create a prompt builder with the default language.
     */
    public SystemPromptBuilder() {
        this(PromptSection.DEFAULT_LANGUAGE);
    }

    /**
     * Create a prompt builder with the requested language.
     *
     * @param language target language code
     */
    public SystemPromptBuilder(String language) {
        this.language = language != null && !language.isBlank() ? language : PromptSection.DEFAULT_LANGUAGE;
    }

    /**
     * Add or replace a prompt section.
     *
     * @param section prompt section
     * @return this builder
     */
    public SystemPromptBuilder addSection(PromptSection section) {
        if (section != null && section.getName() != null && !section.getName().isBlank()) {
            sections.put(section.getName(), section);
        }
        return this;
    }

    /**
     * Remove a prompt section by name.
     *
     * @param name section name
     * @return this builder
     */
    public SystemPromptBuilder removeSection(String name) {
        if (name != null) {
            sections.remove(name);
        }
        return this;
    }

    /**
     * Return all registered sections.
     *
     * @return section snapshot
     */
    public Map<String, PromptSection> getAllSections() {
        return new LinkedHashMap<String, PromptSection>(sections);
    }

    /**
     * Check whether the builder contains a section.
     *
     * @param name section name
     * @return true when the section exists
     */
    public boolean hasSection(String name) {
        return name != null && sections.containsKey(name);
    }

    /**
     * Return a section by name.
     *
     * @param name section name
     * @return matched section or null-equivalent map lookup result
     */
    public PromptSection getSection(String name) {
        return sections.get(name);
    }

    /**
     * Build the final system prompt text from all sections.
     *
     * @return rendered system prompt
     */
    public String build() {
        List<PromptSection> ordered = new ArrayList<PromptSection>(sections.values());
        ordered.sort(Comparator.comparingInt(PromptSection::getPriority));

        List<String> parts = new ArrayList<String>();
        for (PromptSection section : ordered) {
            String rendered = section.render(language);
            if (rendered != null && !rendered.trim().isEmpty()) {
                parts.add(rendered);
            }
        }
        return String.join("\n\n", parts);
    }
}
