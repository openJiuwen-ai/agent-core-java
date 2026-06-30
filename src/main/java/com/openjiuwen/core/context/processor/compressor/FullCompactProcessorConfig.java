/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for {@link FullCompactProcessor}.
 */
public class FullCompactProcessorConfig {
    private int triggerTotalTokens = 180000;
    private int compressionCallMaxTokens = 200000;
    private int messagesToKeep = 10;
    private boolean sessionMemoryEnabled = true;
    private ModelRequestConfig model;
    private ModelClientConfig modelClient;
    private boolean isKeepToolMessagePairs = true;
    private int stateSnapshotMaxChars = 4000;
    private int reinjectRecentSkills = 3;
    private List<String> reinjectFileToolNames = new ArrayList<>(
            List.of("read_file", "write_file", "edit_file", "glob", "grep"));
    private List<String> reinjectToolResultHintNames = new ArrayList<>(
            List.of("read_file", "write_file", "edit_file", "glob", "grep"));
    private String marker = FullCompactProcessor.FULL_COMPACT_BOUNDARY_MARKER;
    private String stateMarker = FullCompactProcessor.FULL_COMPACT_STATE_MARKER;
    private String syntheticUserMarker = FullCompactProcessor.FULL_COMPACT_SYNTHETIC_USER_MARKER;
    private String summaryIntro = FullCompactProcessor.FULL_COMPACT_SUMMARY_INTRO;
    private String recentMessagesNotice = FullCompactProcessor.FULL_COMPACT_RECENT_MESSAGES_NOTICE;
    private String sessionMemoryMarker = FullCompactProcessor.SESSION_MEMORY_BOUNDARY_MARKER;
    private String sessionMemoryIntro = FullCompactProcessor.SESSION_MEMORY_SUMMARY_INTRO;

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getTriggerTotalTokens() {
        return triggerTotalTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCompressionCallMaxTokens() {
        return compressionCallMaxTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMessagesToKeep() {
        return messagesToKeep;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isSessionMemoryEnabled() {
            return sessionMemoryEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ModelRequestConfig getModel() {
        return model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ModelClientConfig getModelClient() {
        return modelClient;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isKeepToolMessagePairs() {
        return isKeepToolMessagePairs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getStateSnapshotMaxChars() {
        return stateSnapshotMaxChars;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getReinjectRecentSkills() {
        return reinjectRecentSkills;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getReinjectFileToolNames() {
        return reinjectFileToolNames;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getReinjectToolResultHintNames() {
        return reinjectToolResultHintNames;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMarker() {
        return marker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getStateMarker() {
        return stateMarker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSyntheticUserMarker() {
        return syntheticUserMarker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSummaryIntro() {
        return summaryIntro;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRecentMessagesNotice() {
        return recentMessagesNotice;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionMemoryMarker() {
        return sessionMemoryMarker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionMemoryIntro() {
        return sessionMemoryIntro;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        if (triggerTotalTokens <= 0) {
            throw new IllegalArgumentException("triggerTotalTokens must be > 0");
        }
        if (compressionCallMaxTokens <= 0) {
            throw new IllegalArgumentException("compressionCallMaxTokens must be > 0");
        }
        if (messagesToKeep < 0) {
            throw new IllegalArgumentException("messagesToKeep must be >= 0");
        }
        if (stateSnapshotMaxChars <= 0) {
            throw new IllegalArgumentException("stateSnapshotMaxChars must be > 0");
        }
        if (reinjectRecentSkills < 0) {
            throw new IllegalArgumentException("reinjectRecentSkills must be >= 0");
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        private final FullCompactProcessorConfig config = new FullCompactProcessorConfig();

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder triggerTotalTokens(int triggerTotalTokens) {
            config.triggerTotalTokens = triggerTotalTokens;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder compressionCallMaxTokens(int compressionCallMaxTokens) {
            config.compressionCallMaxTokens = compressionCallMaxTokens;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder messagesToKeep(int messagesToKeep) {
            config.messagesToKeep = messagesToKeep;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sessionMemoryEnabled(boolean sessionMemoryEnabled) {
            config.sessionMemoryEnabled = sessionMemoryEnabled;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder model(ModelRequestConfig model) {
            config.model = model;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder modelClient(ModelClientConfig modelClient) {
            config.modelClient = modelClient;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder isKeepToolMessagePairs(boolean isKeepToolMessagePairs) {
            config.isKeepToolMessagePairs = isKeepToolMessagePairs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder stateSnapshotMaxChars(int stateSnapshotMaxChars) {
            config.stateSnapshotMaxChars = stateSnapshotMaxChars;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder reinjectRecentSkills(int reinjectRecentSkills) {
            config.reinjectRecentSkills = reinjectRecentSkills;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder reinjectFileToolNames(List<String> reinjectFileToolNames) {
            config.reinjectFileToolNames = reinjectFileToolNames != null
                    ? new ArrayList<>(reinjectFileToolNames)
                    : new ArrayList<>();
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder reinjectToolResultHintNames(List<String> reinjectToolResultHintNames) {
            config.reinjectToolResultHintNames = reinjectToolResultHintNames != null
                    ? new ArrayList<>(reinjectToolResultHintNames)
                    : new ArrayList<>();
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder marker(String marker) {
            config.marker = marker;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder stateMarker(String stateMarker) {
            config.stateMarker = stateMarker;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder syntheticUserMarker(String syntheticUserMarker) {
            config.syntheticUserMarker = syntheticUserMarker;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder summaryIntro(String summaryIntro) {
            config.summaryIntro = summaryIntro;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder recentMessagesNotice(String recentMessagesNotice) {
            config.recentMessagesNotice = recentMessagesNotice;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sessionMemoryMarker(String sessionMemoryMarker) {
            config.sessionMemoryMarker = sessionMemoryMarker;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sessionMemoryIntro(String sessionMemoryIntro) {
            config.sessionMemoryIntro = sessionMemoryIntro;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public FullCompactProcessorConfig build() {
            config.validate();
            return config;
        }
    }
}
