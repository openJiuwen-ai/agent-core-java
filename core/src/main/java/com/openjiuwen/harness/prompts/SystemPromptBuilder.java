/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mirrors Python's {@code SystemPromptBuilder} in
 * {@code openjiuwen/harness/prompts/builder.py}.
 */
public class SystemPromptBuilder extends com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder {

    private static final Set<String> MINIMAL_SECTIONS = Set.of(
            SectionName.IDENTITY,
            SectionName.SAFETY,
            SectionName.SKILLS,
            SectionName.TOOLS,
            SectionName.RUNTIME,
            SectionName.MEMORY
    );

    private PromptMode mode = PromptMode.FULL;

    public SystemPromptBuilder() {
        this(DEFAULT_LANGUAGE, PromptMode.FULL);
    }

    public SystemPromptBuilder(String language) {
        this(language, PromptMode.FULL);
    }

    public SystemPromptBuilder(String language, PromptMode mode) {
        super(language);
        this.mode = mode == null ? PromptMode.FULL : mode;
    }

    public PromptMode getMode() {
        return mode;
    }

    public void setMode(PromptMode mode) {
        this.mode = mode == null ? PromptMode.FULL : mode;
    }

    @Override
    public String build() {
        if (mode == PromptMode.NONE) {
            return getSection(SectionName.IDENTITY)
                    .map(section -> section.render(getLanguage()))
                    .orElse("");
        }
        return super.build();
    }

    public PromptReport buildReport() {
        return PromptReport.fromBuilder(this);
    }

    @Override
    protected List<PromptSection> getSectionsForBuild() {
        List<PromptSection> sections = new ArrayList<>(super.getSectionsForBuild());
        if (mode == PromptMode.FULL) {
            return sections;
        }

        List<PromptSection> filtered = new ArrayList<>();
        for (PromptSection section : sections) {
            if (MINIMAL_SECTIONS.contains(section.getName())) {
                filtered.add(section);
            }
        }
        return filtered;
    }
}
