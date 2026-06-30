/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class EvolutionPatch used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class EvolutionPatch {
    @Builder.Default
    private String section = "Troubleshooting";
    @Builder.Default
    private String action = "append";
    @Builder.Default
    private String content = "";
    @Builder.Default
    private EvolutionTarget target = EvolutionTarget.BODY;
    private String skipReason;
    private String mergeTarget;
    private String scriptFilename;
    private String scriptLanguage;
    private String scriptPurpose;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("section", section);
        payload.put("action", action);
        payload.put("content", content);
        payload.put("target", target != null ? target.value() : EvolutionTarget.BODY.value());
        putIfPresent(payload, "skip_reason", skipReason);
        putIfPresent(payload, "merge_target", mergeTarget);
        putIfPresent(payload, "script_filename", scriptFilename);
        putIfPresent(payload, "script_language", scriptLanguage);
        putIfPresent(payload, "script_purpose", scriptPurpose);
        return payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static EvolutionPatch fromMap(Map<String, Object> data) {
        Map<String, Object> safe = data != null ? data : Map.of();
        return EvolutionPatch.builder()
                .section(string(safe.getOrDefault("section", "Troubleshooting")))
                .action(string(safe.getOrDefault("action", "append")))
                .content(string(safe.getOrDefault("content", "")))
                .target(EvolutionTarget.fromValue(string(safe.get("target"))))
                .skipReason(blankToNull(string(safe.get("skip_reason"))))
                .mergeTarget(blankToNull(string(safe.get("merge_target"))))
                .scriptFilename(blankToNull(string(safe.get("script_filename"))))
                .scriptLanguage(blankToNull(string(safe.get("script_language"))))
                .scriptPurpose(blankToNull(string(safe.get("script_purpose"))))
                .build();
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
