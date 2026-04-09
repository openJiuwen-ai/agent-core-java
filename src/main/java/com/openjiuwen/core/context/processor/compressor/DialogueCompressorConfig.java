/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.context.processor.compressor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Configuration for the {@link DialogueCompressor} ContextProcessor.
 * <p>
 * Mirrors Python's {@code DialogueCompressorConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueCompressorConfig {

    /**
     * Maximum number of messages allowed before compression is triggered.
     */
    private Integer messagesThreshold;

    /**
     * Maximum accumulated token count before compression is triggered.
     */
    @Builder.Default
    private int tokensThreshold = 10000;

    /**
     * Number of most-recent messages to retain regardless of thresholds.
     */
    private Integer messagesToKeep;

    /**
     * If true, the most recent user-assistant round is always preserved.
     */
    @Builder.Default
    private boolean keepLastRound = true;

    /**
     * User-supplied prompt for the compression step.
     */
    private String customizedCompressionPrompt;

    /**
     * Max tokens allowed in the compressed summary.
     */
    @Builder.Default
    private int compressionTokenLimit = 2000;

    /**
     * Model request configuration.
     */
    private ModelRequestConfig model;

    /**
     * Optional client-level configuration for the model.
     */
    private ModelClientConfig modelClient;

    /**
     * Validate configuration constraints matching Python Pydantic rules.
     */
    public void validate() {
        if (messagesThreshold != null && messagesThreshold <= 0) {
            throw new IllegalArgumentException("messagesThreshold must be > 0, got " + messagesThreshold);
        }
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0, got " + tokensThreshold);
        }
        if (messagesToKeep != null && messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0, got " + messagesToKeep);
        }
        if (compressionTokenLimit <= 0) {
            throw new IllegalArgumentException("compressionTokenLimit must be > 0, got " + compressionTokenLimit);
        }
    }
}
