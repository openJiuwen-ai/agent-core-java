/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.prompts;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Section-based system prompt builder base class.
 * <p>
 * Provides generic section registration, ordering by priority, and rendering.
 * Agent-family-specific policies (mode switching, diagnostics) live in subclasses.
 * <p>
 * Mirrors Python's {@code SystemPromptBuilder} in
 * {@code openjiuwen.core.single_agent.prompts.builder}.
 */
public class SystemPromptBuilder {

    protected String language;
    protected final Map<String, PromptSection> sections = new LinkedHashMap<>();

    public SystemPromptBuilder() {
        this(PromptSection.DEFAULT_LANGUAGE);
    }

    public SystemPromptBuilder(String language) {
        this.language = language;
    }

    /** Add or replace a section (same name overwrites). */
    public SystemPromptBuilder addSection(PromptSection section) {
        sections.put(section.getName(), section);
        return this;
    }

    /** Remove a section by name. */
    public SystemPromptBuilder removeSection(String name) {
        sections.remove(name);
        return this;
    }

    /** Return a copy of all registered sections. */
    public Map<String, PromptSection> getAllSections() {
        return new LinkedHashMap<>(sections);
    }

    /** Check if a section exists. */
    public boolean hasSection(String name) {
        return sections.containsKey(name);
    }

    /** Get a section by name. */
    public Optional<PromptSection> getSection(String name) {
        return Optional.ofNullable(sections.get(name));
    }

    /** Get the current language. */
    public String getLanguage() {
        return language;
    }

    /** Set the language. */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Return sections sorted by priority (ascending) for build.
     * Override in subclasses to filter sections.
     */
    protected List<PromptSection> getSectionsForBuild() {
        return sections.values().stream()
                .sorted(Comparator.comparingInt(PromptSection::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Build the full system prompt by sorting sections by priority
     * and joining them with double newlines.
     */
    public String build() {
        return getSectionsForBuild().stream()
                .map(s -> s.render(language))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}
