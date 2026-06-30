/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class EvolutionRecord used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class EvolutionRecord {
    private String id;
    @Builder.Default
    private String source = "unknown";
    private String timestamp;
    @Builder.Default
    private String context = "";
    private EvolutionPatch change;
    private boolean isApplied;
    @Builder.Default
    private double score = 0.6d;
    @Builder.Default
    private UsageStats usageStats = UsageStats.builder().build();
    private String skillVersion;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static EvolutionRecord make(String source, String context, EvolutionPatch change) {
        return EvolutionRecord.builder()
                .id("ev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .source(source != null ? source : "unknown")
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .context(context != null ? context : "")
                .change(change != null ? change : EvolutionPatch.builder().build())
                .usageStats(UsageStats.builder().build())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isPending() {
        return !isApplied;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("source", source);
        payload.put("timestamp", timestamp);
        payload.put("context", context);
        payload.put("change", change != null ? change.toMap() : EvolutionPatch.builder().build().toMap());
        payload.put("isApplied", isApplied);
        payload.put("score", score);
        if (usageStats != null) {
            payload.put("usage_stats", usageStats.toMap());
        }
        if (skillVersion != null && !skillVersion.isBlank()) {
            payload.put("skill_version", skillVersion);
        }
        return payload;
    }
}
