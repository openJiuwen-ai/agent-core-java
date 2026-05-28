/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import java.util.HashMap;
import java.util.Map;

/**
 * Detected evolution signal from dialogue/tool trace.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.signal.base.EvolutionSignal}.
 *
 * <p>Attributes:
 * <ul>
 *   <li>signalType: Type of signal (e.g., 'execution_failure', 'user_correction', 'low_score').</li>
 *   <li>evolutionType: Category of evolution (SKILL_EXPERIENCE or NEW_SKILL).</li>
 *   <li>section: Target section in SKILL.md (e.g., 'Troubleshooting', 'Examples').</li>
 *   <li>excerpt: Relevant excerpt from the conversation/trace.</li>
 *   <li>toolName: Tool name if signal originates from tool execution.</li>
 *   <li>skillName: Skill name for skill resolution.</li>
 *   <li>context: Additional context dict (offline: question/label/answer/reason/score).</li>
 * </ul>
 */
public class EvolutionSignal {

    private String signalType;
    private EvolutionCategory evolutionType;
    private String section;
    private String excerpt;
    private String toolName;
    private String skillName;
    private Map<String, Object> context;

    public EvolutionSignal() {
    }

    public EvolutionSignal(
            String signalType,
            EvolutionCategory evolutionType,
            String section,
            String excerpt,
            String toolName,
            String skillName,
            Map<String, Object> context
    ) {
        this.signalType = signalType;
        this.evolutionType = evolutionType;
        this.section = section;
        this.excerpt = excerpt;
        this.toolName = toolName;
        this.skillName = skillName;
        this.context = context;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> d = new HashMap<>();
        d.put("type", signalType);
        d.put("evolution_type", evolutionType != null ? evolutionType.getValue() : null);
        d.put("section", section);
        d.put("excerpt", excerpt);
        d.put("tool_name", toolName);
        d.put("skill_name", skillName);
        if (context != null) {
            d.put("context", context);
        }
        return d;
    }

    // Getters and setters
    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }
    public EvolutionCategory getEvolutionType() { return evolutionType; }
    public void setEvolutionType(EvolutionCategory evolutionType) { this.evolutionType = evolutionType; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public static class Builder {
        private String signalType;
        private EvolutionCategory evolutionType;
        private String section;
        private String excerpt;
        private String toolName;
        private String skillName;
        private Map<String, Object> context;

        public Builder signalType(String signalType) { this.signalType = signalType; return this; }
        public Builder evolutionType(EvolutionCategory evolutionType) { this.evolutionType = evolutionType; return this; }
        public Builder section(String section) { this.section = section; return this; }
        public Builder excerpt(String excerpt) { this.excerpt = excerpt; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder skillName(String skillName) { this.skillName = skillName; return this; }
        public Builder context(Map<String, Object> context) { this.context = context; return this; }

        public EvolutionSignal build() {
            return new EvolutionSignal(signalType, evolutionType, section, excerpt, toolName, skillName, context);
        }
    }
}