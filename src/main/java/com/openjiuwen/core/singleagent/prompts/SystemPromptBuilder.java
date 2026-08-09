/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.prompts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Section-based system prompt builder.
 * 
 * @since 0.1.7
 */
public class SystemPromptBuilder {
    private static final String MODE_FULL = "full";
    private static final String MODE_MINIMAL = "minimal";
    private static final String MODE_NONE = "none";

    /**
     * HashSet<String>.
     * 
     * @param "memory" "memory"
     * @since 0.1.7
     */
    private static final Set<String> MINIMAL_SECTIONS =
        new HashSet<String>(List.of("identity", "safety", "skills", "tools", "runtime", "memory"));

    private final String language;
    private final String mode;

    /**
     * PromptSection>.
     * 
     * @return the result
     * @since 0.1.7
     */
    private final Map<String, PromptSection> sections = new LinkedHashMap<String, PromptSection>();

    /**
     * Create a prompt builder with the default language.
     * 
     * @since 0.1.7
     */
    public SystemPromptBuilder() {
        this(PromptSection.DEFAULT_LANGUAGE, MODE_FULL);
    }

    /**
     * Create a prompt builder with the requested language.
     * 
     * @param language target language code
     * @since 0.1.7
     */
    public SystemPromptBuilder(String language) {
        this(language, MODE_FULL);
    }

    /**
     * Create a prompt builder with the requested language and prompt mode.
     * <p>
     * Modes mirror the harness Python builder:
     * <ul>
     * <li>{@code full}: render every section</li>
     * <li>{@code minimal}: render only core sections</li>
     * <li>{@code none}: render identity only</li>
     * </ul>
     * 
     * @param language target language code
     * @param mode prompt assembly mode
     * @since 0.1.7
     */
    public SystemPromptBuilder(String language, String mode) {
        this.language = language != null && !language.isBlank() ? language : PromptSection.DEFAULT_LANGUAGE;
        this.mode = normalizeMode(mode);
    }

    /**
     * Add or isReplace a prompt section.
     * 
     * @param section prompt section
     * @return this builder
     * @since 0.1.7
     */
    public synchronized SystemPromptBuilder addSection(PromptSection section) {
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
     * @since 0.1.7
     */
    public synchronized SystemPromptBuilder removeSection(String name) {
        if (name != null) {
            sections.remove(name);
        }
        return this;
    }

    /**
     * Return all registered sections.
     * 
     * @return section snapshot
     * @since 0.1.7
     */
    public synchronized Map<String, PromptSection> getAllSections() {
        return new LinkedHashMap<String, PromptSection>(sections);
    }

    /**
     * Check whether the builder contains a section.
     * 
     * @param name section name
     * @return true when the section isExists
     * @since 0.1.7
     */
    public synchronized boolean hasSection(String name) {
        return name != null && sections.containsKey(name);
    }

    /**
     * Return a section by name.
     * 
     * @param name section name
     * @return matched section or null-equivalent map lookup result
     * @since 0.1.7
     */
    public synchronized PromptSection getSection(String name) {
        return sections.get(name);
    }

    /**
     * Build the final system prompt text from all sections.
     * 
     * @return rendered system prompt
     * @since 0.1.7
     */
    public synchronized String build() {
        List<PromptSection> ordered = getSectionsForBuild();
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

    /**
     * Return the normalized prompt assembly mode.
     * 
     * @return prompt mode
     * @since 0.1.7
     */
    public String getMode() {
        return mode;
    }

    /**
     * Return the language used to render sections.
     * 
     * @return target language code
     * @since 0.1.7
     */
    public String getLanguage() {
        return language;
    }

    /**
     * getSectionsForBuild.
     * 
     * @return the result
     * @since 0.1.7
     */
    private List<PromptSection> getSectionsForBuild() {
        if (MODE_FULL.equals(mode)) {
            return new ArrayList<PromptSection>(sections.values());
        }
        List<PromptSection> filtered = new ArrayList<PromptSection>();
        for (PromptSection section : sections.values()) {
            if (MODE_NONE.equals(mode)) {
                if ("identity".equals(section.getName())) {
                    filtered.add(section);
                }
                continue;
            }
            if (MINIMAL_SECTIONS.contains(section.getName())) {
                filtered.add(section);
            }
        }
        return filtered;
    }

    /**
     * normalizeMode.
     * 
     * @param mode mode
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_FULL;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        if (MODE_MINIMAL.equals(normalized) || MODE_NONE.equals(normalized)) {
            return normalized;
        }
        return MODE_FULL;
    }
}
