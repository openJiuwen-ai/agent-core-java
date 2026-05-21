/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One stored evolution record.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.types.EvolutionRecord}.
 */
public class EvolutionRecord {

    private String id;
    private String source;
    private String timestamp;
    private String context;
    private EvolutionPatch change;
    private boolean applied = false;
    private double score = 0.6;
    private UsageStats usageStats;
    private String skillVersion;

    public EvolutionRecord() {
    }

    public EvolutionRecord(String id, String source, String timestamp, String context,
                           EvolutionPatch change, boolean applied, double score,
                           UsageStats usageStats, String skillVersion) {
        this.id = id;
        this.source = source;
        this.timestamp = timestamp;
        this.context = context;
        this.change = change;
        this.applied = applied;
        this.score = score;
        this.usageStats = usageStats;
        this.skillVersion = skillVersion;
    }

    public static EvolutionRecord make(String source, String context, EvolutionPatch change,
                                       double score, String skillVersion) {
        return new EvolutionRecord(
                "ev_" + UUID.randomUUID().toString().substring(0, 8),
                source,
                Instant.now().toString(),
                context,
                change,
                false,
                score,
                new UsageStats(),
                skillVersion
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isPending() {
        return !applied;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("source", source);
        payload.put("timestamp", timestamp);
        payload.put("context", context);
        payload.put("change", change != null ? change.toDict() : new HashMap<>());
        payload.put("applied", applied);
        payload.put("score", score);

        if (usageStats != null) {
            payload.put("usage_stats", usageStats.toDict());
        }
        if (skillVersion != null) {
            payload.put("skill_version", skillVersion);
        }

        return payload;
    }

    public static EvolutionRecord fromDict(Map<String, Object> data) {
        Map<String, Object> usageStatsData = (Map<String, Object>) data.get("usage_stats");
        UsageStats usageStats = usageStatsData != null ? UsageStats.fromDict(usageStatsData) : new UsageStats();

        Map<String, Object> changeData = (Map<String, Object>) data.get("change");
        EvolutionPatch change = changeData != null ? EvolutionPatch.fromDict(changeData) : EvolutionPatch.builder().build();

        return new EvolutionRecord(
                getString(data, "id", "ev_" + UUID.randomUUID().toString().substring(0, 8)),
                getString(data, "source", "unknown"),
                getString(data, "timestamp", ""),
                getString(data, "context", ""),
                change,
                getBool(data, "applied", false),
                getDouble(data, "score", 0.6),
                usageStats,
                getString(data, "skill_version")
        );
    }

    private static String getString(Map<String, Object> data, String key, String defaultVal) {
        Object val = data.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private static boolean getBool(Map<String, Object> data, String key, boolean defaultVal) {
        Object val = data.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Boolean) return (Boolean) val;
        return defaultVal;
    }

    private static double getDouble(Map<String, Object> data, String key, double defaultVal) {
        Object val = data.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public EvolutionPatch getChange() { return change; }
    public void setChange(EvolutionPatch change) { this.change = change; }
    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public UsageStats getUsageStats() { return usageStats; }
    public void setUsageStats(UsageStats usageStats) { this.usageStats = usageStats; }
    public String getSkillVersion() { return skillVersion; }
    public void setSkillVersion(String skillVersion) { this.skillVersion = skillVersion; }

    public static class Builder {
        private String id;
        private String source;
        private String timestamp;
        private String context;
        private EvolutionPatch change;
        private boolean applied = false;
        private double score = 0.6;
        private UsageStats usageStats;
        private String skillVersion;

        public Builder id(String id) { this.id = id; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
        public Builder context(String context) { this.context = context; return this; }
        public Builder change(EvolutionPatch change) { this.change = change; return this; }
        public Builder applied(boolean applied) { this.applied = applied; return this; }
        public Builder score(double score) { this.score = score; return this; }
        public Builder usageStats(UsageStats usageStats) { this.usageStats = usageStats; return this; }
        public Builder skillVersion(String skillVersion) { this.skillVersion = skillVersion; return this; }

        public EvolutionRecord build() {
            return new EvolutionRecord(id, source, timestamp, context, change, applied, score, usageStats, skillVersion);
        }
    }
}