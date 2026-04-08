// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReMeMemoryMetadata}.
 */
public class ReMeMemoryMetadata {

    private List<String> tags = new ArrayList<>();
    private String stepType;
    private List<String> toolsUsed = new ArrayList<>();
    private Double confidence;
    private int freq;
    private Double utility;

    public ReMeMemoryMetadata() {
        // Default constructor
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tags", new ArrayList<>(tags));
        result.put("step_type", stepType);
        result.put("tools_used", new ArrayList<>(toolsUsed));
        result.put("confidence", confidence);
        result.put("freq", freq);
        result.put("utility", utility);
        return result;
    }

    public static ReMeMemoryMetadata fromMap(Map<String, Object> data) {
        ReMeMemoryMetadata result = new ReMeMemoryMetadata();
        result.tags = SchemaUtils.stringListValue(data.get("tags"));
        result.stepType = SchemaUtils.stringValue(data.get("step_type"), null);
        result.toolsUsed = SchemaUtils.stringListValue(data.get("tools_used"));
        result.confidence = data.containsKey("confidence")
            ? SchemaUtils.doubleValue(data.get("confidence"), 0.0d)
            : null;
        result.freq = SchemaUtils.intValue(data.get("freq"), 0);
        result.utility = data.containsKey("utility")
            ? SchemaUtils.doubleValue(data.get("utility"), 0.0d)
            : null;
        return result;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public List<String> getToolsUsed() {
        return new ArrayList<>(toolsUsed);
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed != null ? new ArrayList<>(toolsUsed) : new ArrayList<>();
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public Double getUtility() {
        return utility;
    }

    public void setUtility(Double utility) {
        this.utility = utility;
    }
}
