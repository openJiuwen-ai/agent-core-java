/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mirrors Python's {@code AddMemResult} in
 * {@code openjiuwen/core/memory/long_term_memory.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddMemResult {
    @JsonProperty("variables")
    private List<VariableUnit> variables = new ArrayList<>();

    @JsonProperty("user_profile")
    private List<FragmentMemoryUnit> userProfile = new ArrayList<>();

    @JsonProperty("semantic_memory")
    private List<FragmentMemoryUnit> semanticMemory = new ArrayList<>();

    @JsonProperty("episodic_memory")
    private List<FragmentMemoryUnit> episodicMemory = new ArrayList<>();

    @JsonProperty("summary")
    private List<SummaryUnit> summary = new ArrayList<>();

    public AddMemResult() {
    }

    public AddMemResult(List<VariableUnit> variables,
                        List<FragmentMemoryUnit> userProfile,
                        List<FragmentMemoryUnit> semanticMemory,
                        List<FragmentMemoryUnit> episodicMemory,
                        List<SummaryUnit> summary) {
        setVariables(variables);
        setUserProfile(userProfile);
        setSemanticMemory(semanticMemory);
        setEpisodicMemory(episodicMemory);
        setSummary(summary);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<VariableUnit> getVariables() {
        return List.copyOf(variables);
    }

    public void setVariables(List<VariableUnit> variables) {
        this.variables = variables == null ? new ArrayList<>() : new ArrayList<>(variables);
    }

    public List<FragmentMemoryUnit> getUserProfile() {
        return List.copyOf(userProfile);
    }

    public void setUserProfile(List<FragmentMemoryUnit> userProfile) {
        this.userProfile = userProfile == null ? new ArrayList<>() : new ArrayList<>(userProfile);
    }

    public List<FragmentMemoryUnit> getSemanticMemory() {
        return List.copyOf(semanticMemory);
    }

    public void setSemanticMemory(List<FragmentMemoryUnit> semanticMemory) {
        this.semanticMemory = semanticMemory == null ? new ArrayList<>() : new ArrayList<>(semanticMemory);
    }

    public List<FragmentMemoryUnit> getEpisodicMemory() {
        return List.copyOf(episodicMemory);
    }

    public void setEpisodicMemory(List<FragmentMemoryUnit> episodicMemory) {
        this.episodicMemory = episodicMemory == null ? new ArrayList<>() : new ArrayList<>(episodicMemory);
    }

    public List<SummaryUnit> getSummary() {
        return List.copyOf(summary);
    }

    public void setSummary(List<SummaryUnit> summary) {
        this.summary = summary == null ? new ArrayList<>() : new ArrayList<>(summary);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AddMemResult that)) {
            return false;
        }
        return Objects.equals(variables, that.variables)
                && Objects.equals(userProfile, that.userProfile)
                && Objects.equals(semanticMemory, that.semanticMemory)
                && Objects.equals(episodicMemory, that.episodicMemory)
                && Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables, userProfile, semanticMemory, episodicMemory, summary);
    }

    public static final class Builder {
        private List<VariableUnit> variables = new ArrayList<>();
        private List<FragmentMemoryUnit> userProfile = new ArrayList<>();
        private List<FragmentMemoryUnit> semanticMemory = new ArrayList<>();
        private List<FragmentMemoryUnit> episodicMemory = new ArrayList<>();
        private List<SummaryUnit> summary = new ArrayList<>();

        private Builder() {
        }

        public Builder variables(List<VariableUnit> variables) {
            this.variables = variables;
            return this;
        }

        public Builder userProfile(List<FragmentMemoryUnit> userProfile) {
            this.userProfile = userProfile;
            return this;
        }

        public Builder semanticMemory(List<FragmentMemoryUnit> semanticMemory) {
            this.semanticMemory = semanticMemory;
            return this;
        }

        public Builder episodicMemory(List<FragmentMemoryUnit> episodicMemory) {
            this.episodicMemory = episodicMemory;
            return this;
        }

        public Builder summary(List<SummaryUnit> summary) {
            this.summary = summary;
            return this;
        }

        public AddMemResult build() {
            return new AddMemResult(variables, userProfile, semanticMemory, episodicMemory, summary);
        }
    }
}
