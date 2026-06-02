/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.SectionName;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DeepAgent prompt builder with mode filtering and diagnostics.
 * <p>
 * Mirrors Python's {@code SystemPromptBuilder} in
 * {@code openjiuwen.harness.prompts.builder}.
 */
public class DeepAgentPromptBuilder extends SystemPromptBuilder {

    /** Prompt assembly mode for DeepAgent. */
    public enum PromptMode {
        FULL,
        MINIMAL,
        NONE;

        public static PromptMode fromString(String value) {
            if (value == null) {
                return FULL;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return FULL;
            }
        }
    }

    /** Sections included in MINIMAL mode. */
    private static final Set<String> MINIMAL_SECTIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    SectionName.IDENTITY,
                    SectionName.SAFETY,
                    SectionName.SKILLS,
                    SectionName.TOOLS,
                    SectionName.RUNTIME,
                    SectionName.MEMORY
            ))
    );

    private PromptMode mode;

    public DeepAgentPromptBuilder() {
        this(PromptSection.DEFAULT_LANGUAGE, PromptMode.FULL);
    }

    public DeepAgentPromptBuilder(String language, PromptMode mode) {
        super(language);
        this.mode = mode != null ? mode : PromptMode.FULL;
    }

    public PromptMode getMode() {
        return mode;
    }

    public void setMode(PromptMode mode) {
        this.mode = mode;
    }

    /**
     * Build a diagnostic report from the current builder state.
     */
    public PromptReport buildReport() {
        return PromptReport.fromBuilder(this);
    }

    @Override
    public String build() {
        if (mode == PromptMode.NONE) {
            Optional<PromptSection> identity = getSection(SectionName.IDENTITY);
            return identity.map(s -> s.render(language)).orElse("");
        }
        return super.build();
    }

    @Override
    protected List<PromptSection> getSectionsForBuild() {
        if (mode == PromptMode.FULL) {
            return super.getSectionsForBuild();
        }
        // MINIMAL mode: only include whitelisted sections
        return super.getSectionsForBuild().stream()
                .filter(s -> MINIMAL_SECTIONS.contains(s.getName()))
                .collect(Collectors.toList());
    }
}
