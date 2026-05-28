/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persisted container of evolution entries for one skill.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.types.EvolutionLog}.
 */
public class EvolutionLog {

    private String skillId;
    private String version = "1.0.0";
    private String updatedAt;
    private List<EvolutionRecord> entries = new ArrayList<>();

    public EvolutionLog() {
        this.updatedAt = Instant.now().toString();
    }

    public EvolutionLog(String skillId) {
        this.skillId = skillId;
        this.updatedAt = Instant.now().toString();
    }

    public EvolutionLog(String skillId, String version, String updatedAt, List<EvolutionRecord> entries) {
        this.skillId = skillId;
        this.version = version;
        this.updatedAt = updatedAt;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EvolutionLog empty(String skillId) {
        return new EvolutionLog(skillId);
    }

    public List<EvolutionRecord> getPendingEntries() {
        return entries.stream()
                .filter(EvolutionRecord::isPending)
                .collect(Collectors.toList());
    }

    public Map<String, Object> toDict() {
        Map<String, Object> d = new HashMap<>();
        d.put("skill_id", skillId);
        d.put("version", version);
        d.put("updated_at", updatedAt);
        d.put("entries", entries.stream().map(EvolutionRecord::toDict).collect(Collectors.toList()));
        return d;
    }

    public static EvolutionLog fromDict(Map<String, Object> data) {
        List<Map<String, Object>> entriesData = (List<Map<String, Object>>) data.get("entries");
        List<EvolutionRecord> entries = new ArrayList<>();
        if (entriesData != null) {
            for (Map<String, Object> item : entriesData) {
                entries.add(EvolutionRecord.fromDict(item));
            }
        }

        return new EvolutionLog(
                getString(data, "skill_id", ""),
                getString(data, "version", "1.0.0"),
                getString(data, "updated_at", ""),
                entries
        );
    }

    private static String getString(Map<String, Object> data, String key, String defaultVal) {
        Object val = data.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    // Getters and setters
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public List<EvolutionRecord> getEntries() { return entries; }
    public void setEntries(List<EvolutionRecord> entries) { this.entries = entries; }

    public static class Builder {
        private String skillId;
        private String version = "1.0.0";
        private String updatedAt;
        private List<EvolutionRecord> entries = new ArrayList<>();

        public Builder skillId(String skillId) { this.skillId = skillId; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder updatedAt(String updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder entries(List<EvolutionRecord> entries) { this.entries = entries; return this; }

        public EvolutionLog build() {
            return new EvolutionLog(skillId, version, updatedAt != null ? updatedAt : Instant.now().toString(), entries);
        }
    }
}