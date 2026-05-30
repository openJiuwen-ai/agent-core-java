/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured artifact from select_pipeline stage.
 *
 * <p>Mirrors Python's {@code PipelineSelectionArtifact} in {@code openjiuwen.auto_harness.schema}.</p>
 */
public class PipelineSelectionArtifact {

    private static final String META_EVOLVE_PIPELINE = "meta_evolve_pipeline";

    private String pipelineName = META_EVOLVE_PIPELINE;
    private String reason = "";
    private List<String> alternatives = new ArrayList<>();
    private double confidence = 0.0;
    private String riskLevel = "";
    private List<String> requiredInputs = new ArrayList<>();
    private String fallbackPipeline = "";

    public PipelineSelectionArtifact() {
    }

    public PipelineSelectionArtifact(String pipelineName, String reason, double confidence, String fallbackPipeline) {
        this.pipelineName = pipelineName;
        this.reason = reason;
        this.confidence = confidence;
        this.fallbackPipeline = fallbackPipeline;
    }

    public PipelineSelectionArtifact(String pipelineName, String reason, double confidence, String fallbackPipeline, List<String> alternatives) {
        this.pipelineName = pipelineName;
        this.reason = reason;
        this.confidence = confidence;
        this.fallbackPipeline = fallbackPipeline;
        this.alternatives = alternatives != null ? alternatives : new ArrayList<>();
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<String> alternatives) {
        this.alternatives = alternatives != null ? alternatives : new ArrayList<>();
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getRequiredInputs() {
        return requiredInputs;
    }

    public void setRequiredInputs(List<String> requiredInputs) {
        this.requiredInputs = requiredInputs != null ? requiredInputs : new ArrayList<>();
    }

    public String getFallbackPipeline() {
        return fallbackPipeline;
    }

    public void setFallbackPipeline(String fallbackPipeline) {
        this.fallbackPipeline = fallbackPipeline;
    }
}
