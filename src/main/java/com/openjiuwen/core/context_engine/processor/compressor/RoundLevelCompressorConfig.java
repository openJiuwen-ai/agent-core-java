/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Configuration for {@link RoundLevelCompressor}.
 *
 * <p>Mirrors Python's {@code RoundLevelCompressorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/round_level_compressor.py}.</p>
 */
public class RoundLevelCompressorConfig {
    private static final String DEFAULT_TRUNCATED_MARKER = "...[TRUNCATED]...";

    @JsonProperty("trigger_total_tokens")
    private int triggerTotalTokens = 230000;

    @JsonProperty("target_total_tokens")
    private int targetTotalTokens = 160000;

    @JsonProperty("keep_recent_messages")
    private int keepRecentMessages = 0;

    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    @JsonProperty("compression_call_max_tokens")
    private int compressionCallMaxTokens = 250000;

    @JsonProperty("first_pass_target_tokens")
    private int firstPassTargetTokens = 30000;

    @JsonProperty("second_pass_target_tokens")
    private int secondPassTargetTokens = 20000;

    @JsonProperty("third_pass_target_tokens")
    private int thirdPassTargetTokens = 10000;

    @JsonProperty("truncate_head_ratio")
    private double truncateHeadRatio = 0.2d;

    @JsonProperty("truncated_marker")
    private String truncatedMarker = DEFAULT_TRUNCATED_MARKER;

    @JsonProperty("compression_marker")
    private String compressionMarker = RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER;

    public int getTriggerTotalTokens() {
        return triggerTotalTokens;
    }

    public void setTriggerTotalTokens(int triggerTotalTokens) {
        validateGt(triggerTotalTokens, "trigger_total_tokens");
        this.triggerTotalTokens = triggerTotalTokens;
    }

    public int getTargetTotalTokens() {
        return targetTotalTokens;
    }

    public void setTargetTotalTokens(int targetTotalTokens) {
        validateGt(targetTotalTokens, "target_total_tokens");
        this.targetTotalTokens = targetTotalTokens;
    }

    public int getKeepRecentMessages() {
        return keepRecentMessages;
    }

    public void setKeepRecentMessages(int keepRecentMessages) {
        validateGe(keepRecentMessages, "keep_recent_messages");
        this.keepRecentMessages = keepRecentMessages;
    }

    public ModelRequestConfig getModel() {
        return model;
    }

    public void setModel(ModelRequestConfig model) {
        this.model = model;
    }

    public ModelClientConfig getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClientConfig modelClient) {
        this.modelClient = modelClient;
    }

    public int getCompressionCallMaxTokens() {
        return compressionCallMaxTokens;
    }

    public void setCompressionCallMaxTokens(int compressionCallMaxTokens) {
        validateGt(compressionCallMaxTokens, "compression_call_max_tokens");
        this.compressionCallMaxTokens = compressionCallMaxTokens;
    }

    public int getFirstPassTargetTokens() {
        return firstPassTargetTokens;
    }

    public void setFirstPassTargetTokens(int firstPassTargetTokens) {
        validateGt(firstPassTargetTokens, "first_pass_target_tokens");
        this.firstPassTargetTokens = firstPassTargetTokens;
    }

    public int getSecondPassTargetTokens() {
        return secondPassTargetTokens;
    }

    public void setSecondPassTargetTokens(int secondPassTargetTokens) {
        validateGt(secondPassTargetTokens, "second_pass_target_tokens");
        this.secondPassTargetTokens = secondPassTargetTokens;
    }

    public int getThirdPassTargetTokens() {
        return thirdPassTargetTokens;
    }

    public void setThirdPassTargetTokens(int thirdPassTargetTokens) {
        validateGt(thirdPassTargetTokens, "third_pass_target_tokens");
        this.thirdPassTargetTokens = thirdPassTargetTokens;
    }

    public double getTruncateHeadRatio() {
        return truncateHeadRatio;
    }

    public void setTruncateHeadRatio(double truncateHeadRatio) {
        if (truncateHeadRatio <= 0.0d || truncateHeadRatio >= 1.0d) {
            throw new IllegalArgumentException("truncate_head_ratio must be > 0 and < 1");
        }
        this.truncateHeadRatio = truncateHeadRatio;
    }

    public String getTruncatedMarker() {
        return truncatedMarker;
    }

    public void setTruncatedMarker(String truncatedMarker) {
        this.truncatedMarker = truncatedMarker == null ? DEFAULT_TRUNCATED_MARKER : truncatedMarker;
    }

    public String getCompressionMarker() {
        return compressionMarker;
    }

    public void setCompressionMarker(String compressionMarker) {
        this.compressionMarker = compressionMarker == null
                ? RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER
                : compressionMarker;
    }

    private static void validateGt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    private static void validateGe(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
