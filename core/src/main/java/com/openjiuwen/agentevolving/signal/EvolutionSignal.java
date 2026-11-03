/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen/agent_evolving/signal/base.py}.
 */
public class EvolutionSignal {

    private String signalType;
    private String section;
    private String excerpt;
    private String skillName;
    private Map<String, Object> context;

    public EvolutionSignal() {
    }

    public EvolutionSignal(
            String signalType,
            String section,
            String excerpt,
            String skillName,
            Map<String, Object> context
    ) {
        this.signalType = signalType;
        this.section = section;
        this.excerpt = excerpt;
        this.skillName = skillName;
        this.context = context;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", signalType);
        data.put("section", section);
        data.put("excerpt", excerpt);
        data.put("skill_name", skillName);
        if (context != null) {
            data.put("context", context);
        }
        return data;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public static final class Builder {
        private String signalType;
        private String section;
        private String excerpt;
        private String skillName;
        private Map<String, Object> context;

        private Builder() {
        }

        public Builder signalType(String signalType) {
            this.signalType = signalType;
            return this;
        }

        public Builder section(String section) {
            this.section = section;
            return this;
        }

        public Builder excerpt(String excerpt) {
            this.excerpt = excerpt;
            return this;
        }

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public EvolutionSignal build() {
            return new EvolutionSignal(signalType, section, excerpt, skillName, context);
        }
    }
}
