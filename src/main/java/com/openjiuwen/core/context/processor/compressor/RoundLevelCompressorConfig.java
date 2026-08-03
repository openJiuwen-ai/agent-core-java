/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Backward-compatible config DTO for the pre-0.1.14 compressor package.
 *
 * <p>Mirrors Python's {@code RoundLevelCompressorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/round_level_compressor.py}.</p>
 */
public class RoundLevelCompressorConfig
        extends com.openjiuwen.core.context_engine.processor.compressor.RoundLevelCompressorConfig {
    private static final int DEFAULT_TRIGGER_TOTAL_TOKENS = 230000;
    private static final int DEFAULT_TARGET_TOTAL_TOKENS = 160000;
    private static final int DEFAULT_KEEP_RECENT_MESSAGES = 0;
    private static final int DEFAULT_COMPRESSION_CALL_MAX_TOKENS = 250000;
    private static final int DEFAULT_FIRST_PASS_TARGET_TOKENS = 30000;
    private static final int DEFAULT_SECOND_PASS_TARGET_TOKENS = 20000;
    private static final int DEFAULT_THIRD_PASS_TARGET_TOKENS = 10000;
    private static final double DEFAULT_TRUNCATE_HEAD_RATIO = 0.2d;
    private static final String DEFAULT_TRUNCATED_MARKER = "...[TRUNCATED]...";

    public RoundLevelCompressorConfig() {
    }

    public RoundLevelCompressorConfig(int triggerTotalTokens, int targetTotalTokens, int keepRecentMessages,
                                      int compressionCallMaxTokens, int firstPassTargetTokens,
                                      int secondPassTargetTokens, int thirdPassTargetTokens,
                                      double truncateHeadRatio, String truncatedMarker,
                                      String compressionMarker, ModelRequestConfig model,
                                      ModelClientConfig modelClient) {
        setTriggerTotalTokens(triggerTotalTokens);
        setTargetTotalTokens(targetTotalTokens);
        setKeepRecentMessages(keepRecentMessages);
        setCompressionCallMaxTokens(compressionCallMaxTokens);
        setFirstPassTargetTokens(firstPassTargetTokens);
        setSecondPassTargetTokens(secondPassTargetTokens);
        setThirdPassTargetTokens(thirdPassTargetTokens);
        setTruncateHeadRatio(truncateHeadRatio);
        setTruncatedMarker(truncatedMarker);
        setCompressionMarker(compressionMarker);
        setModel(model);
        setModelClient(modelClient);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validate() {
        setTriggerTotalTokens(getTriggerTotalTokens());
        setTargetTotalTokens(getTargetTotalTokens());
        setKeepRecentMessages(getKeepRecentMessages());
        setCompressionCallMaxTokens(getCompressionCallMaxTokens());
        setFirstPassTargetTokens(getFirstPassTargetTokens());
        setSecondPassTargetTokens(getSecondPassTargetTokens());
        setThirdPassTargetTokens(getThirdPassTargetTokens());
        setTruncateHeadRatio(getTruncateHeadRatio());
    }

    public static final class Builder {
        private int triggerTotalTokens = DEFAULT_TRIGGER_TOTAL_TOKENS;
        private int targetTotalTokens = DEFAULT_TARGET_TOTAL_TOKENS;
        private int keepRecentMessages = DEFAULT_KEEP_RECENT_MESSAGES;
        private int compressionCallMaxTokens = DEFAULT_COMPRESSION_CALL_MAX_TOKENS;
        private int firstPassTargetTokens = DEFAULT_FIRST_PASS_TARGET_TOKENS;
        private int secondPassTargetTokens = DEFAULT_SECOND_PASS_TARGET_TOKENS;
        private int thirdPassTargetTokens = DEFAULT_THIRD_PASS_TARGET_TOKENS;
        private double truncateHeadRatio = DEFAULT_TRUNCATE_HEAD_RATIO;
        private String truncatedMarker = DEFAULT_TRUNCATED_MARKER;
        private String compressionMarker = RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER;
        private ModelRequestConfig model;
        private ModelClientConfig modelClient;

        private Builder() {
        }

        public Builder triggerTotalTokens(int triggerTotalTokens) {
            this.triggerTotalTokens = triggerTotalTokens;
            return this;
        }

        public Builder targetTotalTokens(int targetTotalTokens) {
            this.targetTotalTokens = targetTotalTokens;
            return this;
        }

        public Builder keepRecentMessages(int keepRecentMessages) {
            this.keepRecentMessages = keepRecentMessages;
            return this;
        }

        public Builder compressionCallMaxTokens(int compressionCallMaxTokens) {
            this.compressionCallMaxTokens = compressionCallMaxTokens;
            return this;
        }

        public Builder firstPassTargetTokens(int firstPassTargetTokens) {
            this.firstPassTargetTokens = firstPassTargetTokens;
            return this;
        }

        public Builder secondPassTargetTokens(int secondPassTargetTokens) {
            this.secondPassTargetTokens = secondPassTargetTokens;
            return this;
        }

        public Builder thirdPassTargetTokens(int thirdPassTargetTokens) {
            this.thirdPassTargetTokens = thirdPassTargetTokens;
            return this;
        }

        public Builder truncateHeadRatio(double truncateHeadRatio) {
            this.truncateHeadRatio = truncateHeadRatio;
            return this;
        }

        public Builder truncatedMarker(String truncatedMarker) {
            this.truncatedMarker = truncatedMarker;
            return this;
        }

        public Builder compressionMarker(String compressionMarker) {
            this.compressionMarker = compressionMarker;
            return this;
        }

        public Builder model(ModelRequestConfig model) {
            this.model = model;
            return this;
        }

        public Builder modelClient(ModelClientConfig modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder roundsThreshold(int roundsThreshold) {
            this.keepRecentMessages = Math.max(0, roundsThreshold);
            return this;
        }

        public Builder tokensThreshold(int tokensThreshold) {
            this.triggerTotalTokens = tokensThreshold;
            return this;
        }

        public Builder keepLastRound(boolean keepLastRound) {
            if (keepLastRound && keepRecentMessages == 0) {
                this.keepRecentMessages = 2;
            }
            return this;
        }

        public RoundLevelCompressorConfig build() {
            return new RoundLevelCompressorConfig(triggerTotalTokens, targetTotalTokens, keepRecentMessages,
                    compressionCallMaxTokens, firstPassTargetTokens, secondPassTargetTokens,
                    thirdPassTargetTokens, truncateHeadRatio, truncatedMarker, compressionMarker, model,
                    modelClient);
        }
    }
}
