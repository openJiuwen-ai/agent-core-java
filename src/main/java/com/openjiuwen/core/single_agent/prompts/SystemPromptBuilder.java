/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.prompts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mirrors Python's {@code SystemPromptBuilder} in
 * {@code openjiuwen/core/single_agent/prompts/builder.py}.
 */
public class SystemPromptBuilder {
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("cn", "en");
    public static final String DEFAULT_LANGUAGE = "cn";

    private final Map<String, PromptSection> sections = new LinkedHashMap<>();
    private String language;

    public SystemPromptBuilder() {
        this(DEFAULT_LANGUAGE);
    }

    public SystemPromptBuilder(String language) {
        this.language = Objects.requireNonNullElse(language, DEFAULT_LANGUAGE);
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = Objects.requireNonNullElse(language, DEFAULT_LANGUAGE);
    }

    public SystemPromptBuilder addSection(PromptSection section) {
        sections.put(section.getName(), section);
        return this;
    }

    public SystemPromptBuilder removeSection(String name) {
        sections.remove(name);
        return this;
    }

    public Map<String, PromptSection> getAllSections() {
        return new LinkedHashMap<>(sections);
    }

    public boolean hasSection(String name) {
        return sections.containsKey(name);
    }

    public Optional<PromptSection> getSection(String name) {
        return Optional.ofNullable(sections.get(name));
    }

    public String build() {
        List<PromptSection> sortedSections = new ArrayList<>(getSectionsForBuild());
        sortedSections.sort(Comparator.comparingInt(PromptSection::getPriority));
        return sortedSections.stream()
                .map(section -> section.render(language))
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    protected List<PromptSection> getSectionsForBuild() {
        return new ArrayList<>(sections.values());
    }
}
