/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.signal.EvolutionTarget;

import java.util.HashMap;
import java.util.Map;

/**
 * One generated evolution change.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.types.EvolutionPatch}.
 */
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

    public EvolutionPatch() {
    }

    public EvolutionPatch(String section, String action, String content) {
        this.section = section;
        this.action = action;
        this.content = content;
        this.target = EvolutionTarget.BODY;
    }

    public EvolutionPatch(String section, String action, String content, EvolutionTarget target,
                          String skipReason, String mergeTarget, String scriptFilename,
                          String scriptLanguage, String scriptPurpose) {
        this.section = section;
        this.action = action;
        this.content = content;
        this.target = target;
        this.skipReason = skipReason;
        this.mergeTarget = mergeTarget;
        this.scriptFilename = scriptFilename;
        this.scriptLanguage = scriptLanguage;
        this.scriptPurpose = scriptPurpose;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("section", section);
        payload.put("action", action);
        payload.put("content", content);
        payload.put("target", target != null ? target.getValue() : "body");

        if (skipReason != null) payload.put("skip_reason", skipReason);
        if (mergeTarget != null) payload.put("merge_target", mergeTarget);
        if (scriptFilename != null) payload.put("script_filename", scriptFilename);
        if (scriptLanguage != null) payload.put("script_language", scriptLanguage);
        if (scriptPurpose != null) payload.put("script_purpose", scriptPurpose);

        return payload;
    }

    public static EvolutionPatch fromDict(Map<String, Object> data) {
        String rawTarget = getString(data, "target", "body");
        EvolutionTarget target;
        try {
            target = EvolutionTarget.fromValue(rawTarget);
        } catch (Exception e) {
            target = EvolutionTarget.BODY;
        }

        return new EvolutionPatch(
                getString(data, "section", "Troubleshooting"),
                getString(data, "action", "append"),
                getString(data, "content", ""),
                target,
                getString(data, "skip_reason"),
                getString(data, "merge_target"),
                getString(data, "script_filename"),
                getString(data, "script_language"),
                getString(data, "script_purpose")
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

    // Getters and setters
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public EvolutionTarget getTarget() { return target; }
    public void setTarget(EvolutionTarget target) { this.target = target; }
    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    public String getMergeTarget() { return mergeTarget; }
    public void setMergeTarget(String mergeTarget) { this.mergeTarget = mergeTarget; }
    public String getScriptFilename() { return scriptFilename; }
    public void setScriptFilename(String scriptFilename) { this.scriptFilename = scriptFilename; }
    public String getScriptLanguage() { return scriptLanguage; }
    public void setScriptLanguage(String scriptLanguage) { this.scriptLanguage = scriptLanguage; }
    public String getScriptPurpose() { return scriptPurpose; }
    public void setScriptPurpose(String scriptPurpose) { this.scriptPurpose = scriptPurpose; }

    public static class Builder {
        private String section;
        private String action;
        private String content;
        private EvolutionTarget target = EvolutionTarget.BODY;
        private String skipReason;
        private String mergeTarget;
        private String scriptFilename;
        private String scriptLanguage;
        private String scriptPurpose;

        public Builder section(String section) { this.section = section; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder target(EvolutionTarget target) { this.target = target; return this; }
        public Builder skipReason(String skipReason) { this.skipReason = skipReason; return this; }
        public Builder mergeTarget(String mergeTarget) { this.mergeTarget = mergeTarget; return this; }
        public Builder scriptFilename(String scriptFilename) { this.scriptFilename = scriptFilename; return this; }
        public Builder scriptLanguage(String scriptLanguage) { this.scriptLanguage = scriptLanguage; return this; }
        public Builder scriptPurpose(String scriptPurpose) { this.scriptPurpose = scriptPurpose; return this; }

        public EvolutionPatch build() {
            return new EvolutionPatch(section, action, content, target, skipReason, mergeTarget,
                                      scriptFilename, scriptLanguage, scriptPurpose);
        }
    }
}