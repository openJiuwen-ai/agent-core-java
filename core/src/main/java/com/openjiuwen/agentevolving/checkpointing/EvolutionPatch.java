/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One generated evolution change.
 *
 * <p>Mirrors Python's {@code EvolutionPatch} in
 * {@code openjiuwen/agent_evolving/checkpointing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvolutionPatch {

    private String section;
    private String action;
    private String content;
    private EvolutionTarget target = EvolutionTarget.BODY;
    private String skipReason;
    private String mergeTarget;
    private String scriptFilename;
    private String scriptLanguage;
    private String scriptPurpose;
    private List<String> keywords;
    private String summary;

    public EvolutionPatch() {
    }

    public EvolutionPatch(
            String section,
            String action,
            String content,
            EvolutionTarget target,
            String skipReason,
            String mergeTarget,
            String scriptFilename,
            String scriptLanguage,
            String scriptPurpose,
            List<String> keywords,
            String summary
    ) {
        this.section = section;
        this.action = action;
        this.content = content;
        this.target = target == null ? EvolutionTarget.BODY : target;
        this.skipReason = skipReason;
        this.mergeTarget = mergeTarget;
        this.scriptFilename = scriptFilename;
        this.scriptLanguage = scriptLanguage;
        this.scriptPurpose = scriptPurpose;
        this.keywords = keywords == null ? null : new ArrayList<>(keywords);
        this.summary = summary;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        if (!Protocols.VALID_PATCH_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("invalid evolution patch action: " + action);
        }
        if ("skip".equals(action)) {
            return;
        }
        if (!Protocols.VALID_SECTIONS.contains(section)) {
            throw new IllegalArgumentException("invalid evolution patch section: " + section);
        }
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("section", section);
        payload.put("action", action);
        payload.put("content", content);
        payload.put("target", target.getValue());
        putIfNonEmpty(payload, "skip_reason", skipReason);
        putIfNonEmpty(payload, "merge_target", mergeTarget);
        putIfNonEmpty(payload, "script_filename", scriptFilename);
        putIfNonEmpty(payload, "script_language", scriptLanguage);
        putIfNonEmpty(payload, "script_purpose", scriptPurpose);
        return payload;
    }

    public static EvolutionPatch fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        Object rawTarget = resolved.getOrDefault("target", "body");
        return builder()
                .section(stringValue(resolved.getOrDefault("section", "Troubleshooting")))
                .action(stringValue(resolved.getOrDefault("action", "append")))
                .content(stringValue(resolved.getOrDefault("content", "")))
                .target(parseTarget(rawTarget))
                .skipReason(stringValue(resolved.get("skip_reason")))
                .mergeTarget(stringValue(resolved.get("merge_target")))
                .scriptFilename(stringValue(resolved.get("script_filename")))
                .scriptLanguage(stringValue(resolved.get("script_language")))
                .scriptPurpose(stringValue(resolved.get("script_purpose")))
                .build();
    }

    private static EvolutionTarget parseTarget(Object rawTarget) {
        if (rawTarget instanceof EvolutionTarget target) {
            return target;
        }
        String value = String.valueOf(rawTarget);
        for (EvolutionTarget target : EvolutionTarget.values()) {
            if (Objects.equals(target.getValue(), value)) {
                return target;
            }
        }
        throw new IllegalArgumentException("invalid evolution patch target: " + rawTarget);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void putIfNonEmpty(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isEmpty()) {
            payload.put(key, value);
        }
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public EvolutionTarget getTarget() {
        return target;
    }

    public void setTarget(EvolutionTarget target) {
        this.target = target == null ? EvolutionTarget.BODY : target;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public String getMergeTarget() {
        return mergeTarget;
    }

    public void setMergeTarget(String mergeTarget) {
        this.mergeTarget = mergeTarget;
    }

    public String getScriptFilename() {
        return scriptFilename;
    }

    public void setScriptFilename(String scriptFilename) {
        this.scriptFilename = scriptFilename;
    }

    public String getScriptLanguage() {
        return scriptLanguage;
    }

    public void setScriptLanguage(String scriptLanguage) {
        this.scriptLanguage = scriptLanguage;
    }

    public String getScriptPurpose() {
        return scriptPurpose;
    }

    public void setScriptPurpose(String scriptPurpose) {
        this.scriptPurpose = scriptPurpose;
    }

    public List<String> getKeywords() {
        return keywords == null ? null : new ArrayList<>(keywords);
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? null : new ArrayList<>(keywords);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public static final class Builder {
        private String section;
        private String action;
        private String content;
        private EvolutionTarget target = EvolutionTarget.BODY;
        private String skipReason;
        private String mergeTarget;
        private String scriptFilename;
        private String scriptLanguage;
        private String scriptPurpose;
        private List<String> keywords;
        private String summary;

        private Builder() {
        }

        public Builder section(String section) {
            this.section = section;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder target(EvolutionTarget target) {
            this.target = target;
            return this;
        }

        public Builder skipReason(String skipReason) {
            this.skipReason = skipReason;
            return this;
        }

        public Builder mergeTarget(String mergeTarget) {
            this.mergeTarget = mergeTarget;
            return this;
        }

        public Builder scriptFilename(String scriptFilename) {
            this.scriptFilename = scriptFilename;
            return this;
        }

        public Builder scriptLanguage(String scriptLanguage) {
            this.scriptLanguage = scriptLanguage;
            return this;
        }

        public Builder scriptPurpose(String scriptPurpose) {
            this.scriptPurpose = scriptPurpose;
            return this;
        }

        public Builder keywords(List<String> keywords) {
            this.keywords = keywords;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public EvolutionPatch build() {
            return new EvolutionPatch(
                    section,
                    action,
                    content,
                    target,
                    skipReason,
                    mergeTarget,
                    scriptFilename,
                    scriptLanguage,
                    scriptPurpose,
                    keywords,
                    summary
            );
        }
    }
}
