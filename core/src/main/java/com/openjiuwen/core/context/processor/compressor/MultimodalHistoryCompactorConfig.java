/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for {@link MultimodalHistoryCompactor}.
 *
 * <p>Controls how many recent multimodal user messages keep their full-resolution
 * media payloads before older images and videos are replaced by text markers.</p>
 *
 * @since 0.1.17
 */
public class MultimodalHistoryCompactorConfig {
    @JsonProperty("keep_recent_turns")
    private int keepRecentTurns = 2;

    @JsonProperty("trigger_threshold")
    private int triggerThreshold = 1;

    @JsonProperty("summary_marker")
    private String summaryMarker = "[Old image or video content cleared]";

    /**
     * Gets the number of recent media-bearing turns kept at full resolution.
     *
     * @return number of recent media-bearing turns
     */
    public int getKeepRecentTurns() {
        return keepRecentTurns;
    }

    /**
     * Sets the number of recent media-bearing turns kept at full resolution.
     *
     * @param keepRecentTurns number of recent media-bearing turns, non-negative
     */
    public void setKeepRecentTurns(int keepRecentTurns) {
        if (keepRecentTurns < 0) {
            throw new IllegalArgumentException("keep_recent_turns must be >= 0");
        }
        this.keepRecentTurns = keepRecentTurns;
    }

    /**
     * Gets the media-turn count above the retained tail that triggers compaction.
     *
     * @return media-turn threshold
     */
    public int getTriggerThreshold() {
        return triggerThreshold;
    }

    /**
     * Sets the media-turn count above the retained tail that triggers compaction.
     *
     * @param triggerThreshold media-turn threshold, positive
     */
    public void setTriggerThreshold(int triggerThreshold) {
        if (triggerThreshold <= 0) {
            throw new IllegalArgumentException("trigger_threshold must be > 0");
        }
        this.triggerThreshold = triggerThreshold;
    }

    /**
     * Gets the text marker replacing an old image or video payload.
     *
     * @return replacement text marker
     */
    public String getSummaryMarker() {
        return summaryMarker;
    }

    /**
     * Sets the text marker replacing an old media payload.
     *
     * @param summaryMarker replacement text marker, non-null
     */
    public void setSummaryMarker(String summaryMarker) {
        if (summaryMarker == null) {
            throw new IllegalArgumentException("summary_marker must not be null");
        }
        this.summaryMarker = summaryMarker;
    }
}
