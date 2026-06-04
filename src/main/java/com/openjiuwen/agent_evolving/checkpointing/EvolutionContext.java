/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.signal.EvolutionSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * All inputs required for LLM-based experience generation.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.types.EvolutionContext}.
 */
public class EvolutionContext {

    private String skillName;
    private List<EvolutionSignal> signals;
    private String skillContent;
    private List<Map<String, Object>> messages;
    private List<EvolutionRecord> existingDescRecords;
    private List<EvolutionRecord> existingBodyRecords;
    private String userQuery;

    public EvolutionContext() {
        this("", List.of(), "", List.of(), List.of(), List.of(), "");
    }

    public EvolutionContext(String skillName, List<EvolutionSignal> signals, String skillContent,
                            List<Map<String, Object>> messages,
                            List<EvolutionRecord> existingDescRecords,
                            List<EvolutionRecord> existingBodyRecords) {
        this(skillName, signals, skillContent, messages, existingDescRecords, existingBodyRecords, "");
    }

    public EvolutionContext(String skillName, List<EvolutionSignal> signals, String skillContent,
                            List<Map<String, Object>> messages,
                            List<EvolutionRecord> existingDescRecords,
                            List<EvolutionRecord> existingBodyRecords,
                            String userQuery) {
        this.skillName = skillName != null ? skillName : "";
        this.signals = signals != null ? new ArrayList<>(signals) : new ArrayList<>();
        this.skillContent = skillContent != null ? skillContent : "";
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.existingDescRecords = existingDescRecords != null
                ? new ArrayList<>(existingDescRecords) : new ArrayList<>();
        this.existingBodyRecords = existingBodyRecords != null
                ? new ArrayList<>(existingBodyRecords) : new ArrayList<>();
        this.userQuery = userQuery != null ? userQuery : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName != null ? skillName : "";
    }

    public List<EvolutionSignal> getSignals() {
        return signals;
    }

    public void setSignals(List<EvolutionSignal> signals) {
        this.signals = signals != null ? new ArrayList<>(signals) : new ArrayList<>();
    }

    public String getSkillContent() {
        return skillContent;
    }

    public void setSkillContent(String skillContent) {
        this.skillContent = skillContent != null ? skillContent : "";
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    public List<EvolutionRecord> getExistingDescRecords() {
        return existingDescRecords;
    }

    public void setExistingDescRecords(List<EvolutionRecord> existingDescRecords) {
        this.existingDescRecords = existingDescRecords != null
                ? new ArrayList<>(existingDescRecords) : new ArrayList<>();
    }

    public List<EvolutionRecord> getExistingBodyRecords() {
        return existingBodyRecords;
    }

    public void setExistingBodyRecords(List<EvolutionRecord> existingBodyRecords) {
        this.existingBodyRecords = existingBodyRecords != null
                ? new ArrayList<>(existingBodyRecords) : new ArrayList<>();
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery != null ? userQuery : "";
    }

    public static class Builder {
        private String skillName = "";
        private List<EvolutionSignal> signals = new ArrayList<>();
        private String skillContent = "";
        private List<Map<String, Object>> messages = new ArrayList<>();
        private List<EvolutionRecord> existingDescRecords = new ArrayList<>();
        private List<EvolutionRecord> existingBodyRecords = new ArrayList<>();
        private String userQuery = "";

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public Builder signals(List<EvolutionSignal> signals) {
            this.signals = signals;
            return this;
        }

        public Builder skillContent(String skillContent) {
            this.skillContent = skillContent;
            return this;
        }

        public Builder messages(List<Map<String, Object>> messages) {
            this.messages = messages;
            return this;
        }

        public Builder existingDescRecords(List<EvolutionRecord> existingDescRecords) {
            this.existingDescRecords = existingDescRecords;
            return this;
        }

        public Builder existingBodyRecords(List<EvolutionRecord> existingBodyRecords) {
            this.existingBodyRecords = existingBodyRecords;
            return this;
        }

        public Builder userQuery(String userQuery) {
            this.userQuery = userQuery;
            return this;
        }

        public EvolutionContext build() {
            return new EvolutionContext(skillName, signals, skillContent, messages,
                    existingDescRecords, existingBodyRecords, userQuery);
        }
    }
}
