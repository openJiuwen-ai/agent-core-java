/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code PerTurnSample} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/converter.py}.
 */
public class PerTurnSample {

    private final String trajectoryId;
    private final int stepIndex;
    private final String sessionId;
    private final String modelId;
    private final List<Map<String, Object>> messages;
    private final Map<String, Object> response;
    private final String responseText;
    private final List<Integer> responseTokens;
    private final List<Double> logprobs;
    private final List<Integer> promptIds;
    private final Map<String, Object> renderFingerprint;
    private final Object tools;
    private final Map<String, Object> meta;

    public PerTurnSample(String trajectoryId,
                         int stepIndex,
                         String sessionId,
                         String modelId,
                         List<Map<String, Object>> messages,
                         Map<String, Object> response,
                         String responseText,
                         List<Integer> responseTokens,
                         List<Double> logprobs,
                         List<Integer> promptIds,
                         Map<String, Object> renderFingerprint,
                         Object tools,
                         Map<String, Object> meta) {
        this.trajectoryId = trajectoryId;
        this.stepIndex = stepIndex;
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.messages = messages;
        this.response = response;
        this.responseText = responseText;
        this.responseTokens = responseTokens;
        this.logprobs = logprobs;
        this.promptIds = promptIds;
        this.renderFingerprint = renderFingerprint != null ? new LinkedHashMap<>(renderFingerprint) : new LinkedHashMap<>();
        this.tools = tools;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public String getTrajectoryId() {
        return trajectoryId;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getModelId() {
        return modelId;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public Map<String, Object> getResponse() {
        return response;
    }

    public String getResponseText() {
        return responseText;
    }

    public List<Integer> getResponseTokens() {
        return responseTokens;
    }

    public List<Double> getLogprobs() {
        return logprobs;
    }

    public List<Integer> getPromptIds() {
        return promptIds;
    }

    public Map<String, Object> getRenderFingerprint() {
        return renderFingerprint;
    }

    public Object getTools() {
        return tools;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String trajectoryId;
        private int stepIndex;
        private String sessionId;
        private String modelId;
        private List<Map<String, Object>> messages;
        private Map<String, Object> response;
        private String responseText;
        private List<Integer> responseTokens;
        private List<Double> logprobs;
        private List<Integer> promptIds;
        private Map<String, Object> renderFingerprint;
        private Object tools;
        private Map<String, Object> meta;

        private Builder() {
        }

        public Builder trajectoryId(String trajectoryId) {
            this.trajectoryId = trajectoryId;
            return this;
        }

        public Builder stepIndex(int stepIndex) {
            this.stepIndex = stepIndex;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder messages(List<Map<String, Object>> messages) {
            this.messages = messages;
            return this;
        }

        public Builder response(Map<String, Object> response) {
            this.response = response;
            return this;
        }

        public Builder responseText(String responseText) {
            this.responseText = responseText;
            return this;
        }

        public Builder responseTokens(List<Integer> responseTokens) {
            this.responseTokens = responseTokens;
            return this;
        }

        public Builder logprobs(List<Double> logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder promptIds(List<Integer> promptIds) {
            this.promptIds = promptIds;
            return this;
        }

        public Builder renderFingerprint(Map<String, Object> renderFingerprint) {
            this.renderFingerprint = renderFingerprint;
            return this;
        }

        public Builder tools(Object tools) {
            this.tools = tools;
            return this;
        }

        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        public PerTurnSample build() {
            return new PerTurnSample(trajectoryId, stepIndex, sessionId, modelId, messages, response,
                    responseText, responseTokens, logprobs, promptIds, renderFingerprint, tools, meta);
        }
    }
}
