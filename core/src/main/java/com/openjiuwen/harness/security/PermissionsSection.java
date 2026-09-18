/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code PermissionsSection} in
 * {@code openjiuwen/harness/security/models.py}.
 */
public class PermissionsSection {

    private Boolean enabled;
    private String schema;
    private Map<String, Object> defaults;
    private Map<String, Object> tools;
    private List<Map<String, Object>> rules;

    @JsonProperty("approval_overrides")
    private List<ApprovalOverrideEntry> approvalOverrides;

    @JsonProperty("external_directory")
    private Map<String, String> externalDirectory;

    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public void setDefaults(Map<String, Object> defaults) {
        this.defaults = defaults == null ? null : new LinkedHashMap<>(defaults);
    }

    public Map<String, Object> getTools() {
        return tools;
    }

    public void setTools(Map<String, Object> tools) {
        this.tools = tools == null ? null : new LinkedHashMap<>(tools);
    }

    public List<Map<String, Object>> getRules() {
        return rules;
    }

    public void setRules(List<Map<String, Object>> rules) {
        if (rules == null) {
            this.rules = null;
            return;
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            copy.add(rule == null ? null : new LinkedHashMap<>(rule));
        }
        this.rules = copy;
    }

    public List<ApprovalOverrideEntry> getApprovalOverrides() {
        return approvalOverrides;
    }

    public void setApprovalOverrides(List<ApprovalOverrideEntry> approvalOverrides) {
        this.approvalOverrides = approvalOverrides == null ? null : new ArrayList<>(approvalOverrides);
    }

    public Map<String, String> getExternalDirectory() {
        return externalDirectory;
    }

    public void setExternalDirectory(Map<String, String> externalDirectory) {
        this.externalDirectory = externalDirectory == null ? null : new LinkedHashMap<>(externalDirectory);
    }

    @JsonAnySetter
    public void putExtension(String key, Object value) {
        if (isKnownKey(key)) {
            return;
        }
        extensions.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    private boolean isKnownKey(String key) {
        return "enabled".equals(key)
                || "schema".equals(key)
                || "defaults".equals(key)
                || "tools".equals(key)
                || "rules".equals(key)
                || "approval_overrides".equals(key)
                || "external_directory".equals(key);
    }
}
