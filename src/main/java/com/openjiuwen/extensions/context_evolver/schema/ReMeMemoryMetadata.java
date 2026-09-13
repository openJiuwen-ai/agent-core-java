/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReMeMemoryMetadata}.
 * 
 * @since 0.1.7
 */
public class ReMeMemoryMetadata {
    private List<String> tags = new ArrayList<>();
    private String stepType;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> toolsUsed = new ArrayList<>();
    private Double confidence;
    private int freq;
    private Double utility;

    /**
     * ReMeMemoryMetadata.
     * 
     * @since 0.1.7
     */
    public ReMeMemoryMetadata() {
        // Default constructor
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static ReMeMemoryMetadata fromMap(Map<String, Object> data) {
        ReMeMemoryMetadata result = new ReMeMemoryMetadata();
        result.tags = SchemaUtils.stringListValue(data.get("tags"));
        result.stepType = SchemaUtils.stringValue(data.get("step_type"), null);
        result.toolsUsed = SchemaUtils.stringListValue(data.get("tools_used"));
        result.confidence =
            data.containsKey("confidence") ? SchemaUtils.doubleValue(data.get("confidence"), 0.0d) : null;
        result.freq = SchemaUtils.intValue(data.get("freq"), 0);
        result.utility = data.containsKey("utility") ? SchemaUtils.doubleValue(data.get("utility"), 0.0d) : null;
        return result;
    }

    /**
     * getTags.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    /**
     * setTags.
     * 
     * @param tags tags
     * @since 0.1.7
     */
    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    /**
     * getStepType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getStepType() {
        return stepType;
    }

    /**
     * setStepType.
     * 
     * @param stepType stepType
     * @since 0.1.7
     */
    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    /**
     * getToolsUsed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getToolsUsed() {
        return new ArrayList<>(toolsUsed);
    }

    /**
     * setToolsUsed.
     * 
     * @param toolsUsed toolsUsed
     * @since 0.1.7
     */
    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed != null ? new ArrayList<>(toolsUsed) : new ArrayList<>();
    }

    /**
     * getConfidence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getConfidence() {
        return confidence;
    }

    /**
     * setConfidence.
     * 
     * @param confidence confidence
     * @since 0.1.7
     */
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    /**
     * getFreq.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getFreq() {
        return freq;
    }

    /**
     * setFreq.
     * 
     * @param freq freq
     * @since 0.1.7
     */
    public void setFreq(int freq) {
        this.freq = freq;
    }

    /**
     * getUtility.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getUtility() {
        return utility;
    }

    /**
     * setUtility.
     * 
     * @param utility utility
     * @since 0.1.7
     */
    public void setUtility(Double utility) {
        this.utility = utility;
    }
}
