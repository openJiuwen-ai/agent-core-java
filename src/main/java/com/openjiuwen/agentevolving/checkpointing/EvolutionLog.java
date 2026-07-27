/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted container of evolution entries for one skill.
 *
 * <p>Mirrors Python's {@code EvolutionLog} in
 * {@code openjiuwen/agent_evolving/checkpointing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvolutionLog {

    private String skillId;
    private String version = "1.0.0";
    private String updatedAt = Instant.now().toString();
    private List<EvolutionRecord> entries = new ArrayList<>();

    public EvolutionLog() {
    }

    public EvolutionLog(String skillId, String version, String updatedAt, List<EvolutionRecord> entries) {
        this.skillId = skillId;
        this.version = version == null ? "1.0.0" : version;
        this.updatedAt = updatedAt == null ? Instant.now().toString() : updatedAt;
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EvolutionLog empty(String skillId) {
        return new EvolutionLog(skillId, "1.0.0", Instant.now().toString(), List.of());
    }

    public List<EvolutionRecord> getPendingEntries() {
        List<EvolutionRecord> pending = new ArrayList<>();
        for (EvolutionRecord entry : entries) {
            if (entry != null && entry.isPending()) {
                pending.add(entry);
            }
        }
        return pending;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_id", skillId);
        payload.put("version", version);
        payload.put("updated_at", updatedAt);
        List<Map<String, Object>> serializedEntries = new ArrayList<>();
        for (EvolutionRecord entry : entries) {
            serializedEntries.add(entry.toDict());
        }
        payload.put("entries", serializedEntries);
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static EvolutionLog fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        List<EvolutionRecord> loadedEntries = new ArrayList<>();
        Object entriesData = resolved.get("entries");
        if (entriesData instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    loadedEntries.add(EvolutionRecord.fromDict((Map<String, Object>) map));
                }
            }
        }
        return new EvolutionLog(
                stringValue(resolved.getOrDefault("skill_id", "")),
                stringValue(resolved.getOrDefault("version", "1.0.0")),
                stringValue(resolved.getOrDefault("updated_at", "")),
                loadedEntries
        );
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<EvolutionRecord> getEntries() {
        return entries;
    }

    public void setEntries(List<EvolutionRecord> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static final class Builder {
        private String skillId;
        private String version = "1.0.0";
        private String updatedAt;
        private List<EvolutionRecord> entries = new ArrayList<>();

        private Builder() {
        }

        public Builder skillId(String skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder updatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder entries(List<EvolutionRecord> entries) {
            this.entries = entries;
            return this;
        }

        public EvolutionLog build() {
            return new EvolutionLog(skillId, version, updatedAt, entries);
        }
    }
}
