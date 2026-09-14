/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.skill_call;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mirrors Python's {@code ParsedExperienceDraft} in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_draft_parser.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ParsedExperienceDraft {

    private final EvolutionPatch patch;
    private final String summary;
    private final List<String> keywords;

    public ParsedExperienceDraft(EvolutionPatch patch) {
        this(patch, null, null);
    }

    public ParsedExperienceDraft(EvolutionPatch patch, String summary, List<String> keywords) {
        this.patch = patch;
        this.summary = summary;
        this.keywords = immutableList(keywords);
    }

    public EvolutionPatch getPatch() {
        return patch;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
