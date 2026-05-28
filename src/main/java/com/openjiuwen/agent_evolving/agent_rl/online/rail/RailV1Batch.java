// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Rail-v1 batch for uploading to training service.
 * <p>
 * Mirrors Python's {@code RailV1Batch} dataclass in converter.py.
 */
public class RailV1Batch {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private final String protocolVersion;
    private final String sessionId;
    private final String tenantId;
    private final String trajectoryId;
    private final String modelId;
    private final List<PerTurnSample> samples;
    private final TrajectoryMeta trajectoryMeta;
    private final Map<String, Object> prevFeedback;
    private final boolean sessionDone;
    
    public RailV1Batch(String protocolVersion, String sessionId, String tenantId,
                       String trajectoryId, String modelId, List<PerTurnSample> samples,
                       TrajectoryMeta trajectoryMeta, Map<String, Object> prevFeedback,
                       boolean sessionDone) {
        this.protocolVersion = protocolVersion;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.trajectoryId = trajectoryId;
        this.modelId = modelId;
        this.samples = samples;
        this.trajectoryMeta = trajectoryMeta;
        this.prevFeedback = prevFeedback;
        this.sessionDone = sessionDone;
    }
    
    // Getters
    public String getProtocolVersion() { return protocolVersion; }
    public String getSessionId() { return sessionId; }
    public String getTenantId() { return tenantId; }
    public String getTrajectoryId() { return trajectoryId; }
    public String getModelId() { return modelId; }
    public List<PerTurnSample> getSamples() { return samples; }
    public TrajectoryMeta getTrajectoryMeta() { return trajectoryMeta; }
    public Map<String, Object> getPrevFeedback() { return prevFeedback; }
    public boolean isSessionDone() { return sessionDone; }
    
    /**
     * Convert to dictionary for serialization.
     */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol_version", protocolVersion);
        result.put("session_id", sessionId);
        result.put("tenant_id", tenantId);
        result.put("trajectory_id", trajectoryId);
        result.put("model_id", modelId);
        result.put("samples", samplesToDictList(samples));
        result.put("trajectory_meta", trajectoryMetaToDict(trajectoryMeta));
        result.put("prev_feedback", prevFeedback);
        result.put("session_done", sessionDone);
        return result;
    }
    
    private List<Map<String, Object>> samplesToDictList(List<PerTurnSample> samples) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PerTurnSample sample : samples) {
            Map<String, Object> dict = new LinkedHashMap<>();
            dict.put("trajectory_id", sample.getTrajectoryId());
            dict.put("step_index", sample.getStepIndex());
            dict.put("session_id", sample.getSessionId());
            dict.put("model_id", sample.getModelId());
            dict.put("messages", sample.getMessages());
            dict.put("response", sample.getResponse());
            dict.put("response_text", sample.getResponseText());
            dict.put("response_tokens", sample.getResponseTokens());
            dict.put("logprobs", sample.getLogprobs());
            dict.put("prompt_ids", sample.getPromptIds());
            dict.put("render_fingerprint", sample.getRenderFingerprint());
            dict.put("tools", TrajectoryConverterHelper.jsonValue(sample.getTools()));
            dict.put("meta", sample.getMeta());
            result.add(dict);
        }
        return result;
    }
    
    private Map<String, Object> trajectoryMetaToDict(TrajectoryMeta meta) {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("trajectory_id", meta.getTrajectoryId());
        dict.put("session_id", meta.getSessionId());
        dict.put("status", meta.getStatus());
        dict.put("total_turns", meta.getTotalTurns());
        dict.put("started_at", meta.getStartedAt());
        dict.put("ended_at", meta.getEndedAt());
        dict.put("extra", meta.getExtra());
        return dict;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String protocolVersion = "rail-v1";
        private String sessionId;
        private String tenantId;
        private String trajectoryId;
        private String modelId;
        private List<PerTurnSample> samples = new ArrayList<>();
        private TrajectoryMeta trajectoryMeta;
        private Map<String, Object> prevFeedback;
        private boolean sessionDone = false;
        
        public Builder protocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder trajectoryId(String trajectoryId) { this.trajectoryId = trajectoryId; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder samples(List<PerTurnSample> samples) { this.samples = samples; return this; }
        public Builder trajectoryMeta(TrajectoryMeta trajectoryMeta) { this.trajectoryMeta = trajectoryMeta; return this; }
        public Builder prevFeedback(Map<String, Object> prevFeedback) { this.prevFeedback = prevFeedback; return this; }
        public Builder sessionDone(boolean sessionDone) { this.sessionDone = sessionDone; return this; }
        
        public RailV1Batch build() {
            return new RailV1Batch(protocolVersion, sessionId, tenantId, trajectoryId, modelId,
                samples, trajectoryMeta, prevFeedback, sessionDone);
        }
    }
}