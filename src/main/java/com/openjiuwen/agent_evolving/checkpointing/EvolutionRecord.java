/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One stored evolution record.
 *
 * <p>Mirrors Python's {@code EvolutionRecord} in
 * {@code openjiuwen/agent_evolving/checkpointing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvolutionRecord {

    private String id;
    private String source;
    private String timestamp;
    private String context;
    private EvolutionPatch change;
    private boolean applied;
    private double score = 0.6;
    private UsageStats usageStats;
    private String skillVersion;
    private String summary;

    public EvolutionRecord() {
    }

    public EvolutionRecord(
            String id,
            String source,
            String timestamp,
            String context,
            EvolutionPatch change,
            boolean applied,
            double score,
            UsageStats usageStats,
            String skillVersion,
            String summary
    ) {
        this.id = id;
        this.source = source;
        this.timestamp = timestamp;
        this.context = context;
        this.change = change;
        this.applied = applied;
        this.score = score;
        this.usageStats = usageStats;
        this.skillVersion = skillVersion;
        this.summary = summary;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EvolutionRecord make(String source, String context, EvolutionPatch change) {
        return make(source, context, change, 0.6, null, null);
    }

    public static EvolutionRecord make(
            String source,
            String context,
            EvolutionPatch change,
            double score,
            String skillVersion,
            String summary
    ) {
        return new EvolutionRecord(
                "ev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                source,
                Instant.now().toString(),
                context,
                change,
                false,
                score,
                new UsageStats(),
                skillVersion,
                summary
        );
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("source", source);
        payload.put("timestamp", timestamp);
        payload.put("context", context);
        payload.put("change", change == null ? Map.of() : change.toDict());
        payload.put("applied", applied);
        payload.put("score", score);
        if (usageStats != null) {
            payload.put("usage_stats", usageStats.toDict());
        }
        if (skillVersion != null) {
            payload.put("skill_version", skillVersion);
        }
        if (summary != null && !summary.isEmpty()) {
            payload.put("summary", summary);
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static EvolutionRecord fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        Object usageStatsData = resolved.get("usage_stats");
        Object changeData = resolved.get("change");
        return new EvolutionRecord(
                stringValue(resolved.getOrDefault("id", "ev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))),
                stringValue(resolved.getOrDefault("source", "unknown")),
                stringValue(resolved.getOrDefault("timestamp", "")),
                stringValue(resolved.getOrDefault("context", "")),
                EvolutionPatch.fromDict(changeData instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of()),
                boolValue(resolved.get("applied"), false),
                doubleValue(resolved.get("score"), 0.6),
                usageStatsData instanceof Map<?, ?> map ? UsageStats.fromDict((Map<String, Object>) map) : new UsageStats(),
                stringValue(resolved.get("skill_version")),
                stringValue(resolved.get("summary"))
        );
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    public boolean isPending() {
        return !applied;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public EvolutionPatch getChange() {
        return change;
    }

    public void setChange(EvolutionPatch change) {
        this.change = change;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public UsageStats getUsageStats() {
        return usageStats;
    }

    public void setUsageStats(UsageStats usageStats) {
        this.usageStats = usageStats;
    }

    public String getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(String skillVersion) {
        this.skillVersion = skillVersion;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public static final class Builder {
        private String id;
        private String source;
        private String timestamp;
        private String context;
        private EvolutionPatch change;
        private boolean applied;
        private double score = 0.6;
        private UsageStats usageStats;
        private String skillVersion;
        private String summary;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder change(EvolutionPatch change) {
            this.change = change;
            return this;
        }

        public Builder applied(boolean applied) {
            this.applied = applied;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder usageStats(UsageStats usageStats) {
            this.usageStats = usageStats;
            return this;
        }

        public Builder skillVersion(String skillVersion) {
            this.skillVersion = skillVersion;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public EvolutionRecord build() {
            return new EvolutionRecord(
                    id,
                    source,
                    timestamp,
                    context,
                    change,
                    applied,
                    score,
                    usageStats,
                    skillVersion,
                    summary
            );
        }
    }
}
