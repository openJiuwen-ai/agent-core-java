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
 * 
 * @since 0.1.7
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

    /**
     * ArrayList<>.
     * 
     * @param "grep" "grep"
     * @since 0.1.7
     */
    private List<String> reinjectFileToolNames =
        new ArrayList<>(List.of("read_file", "write_file", "edit_file", "glob", "grep"));

    /**
     * ArrayList<>.
     * 
     * @param "grep" "grep"
     * @since 0.1.7
     */
    private List<String> reinjectToolResultHintNames =
        new ArrayList<>(List.of("read_file", "write_file", "edit_file", "glob", "grep"));
    private String marker = FullCompactProcessor.FULL_COMPACT_BOUNDARY_MARKER;
    private String stateMarker = FullCompactProcessor.FULL_COMPACT_STATE_MARKER;
    private String syntheticUserMarker = FullCompactProcessor.FULL_COMPACT_SYNTHETIC_USER_MARKER;
    private String summaryIntro = FullCompactProcessor.FULL_COMPACT_SUMMARY_INTRO;
    private String recentMessagesNotice = FullCompactProcessor.FULL_COMPACT_RECENT_MESSAGES_NOTICE;
    private String sessionMemoryMarker = FullCompactProcessor.SESSION_MEMORY_BOUNDARY_MARKER;
    private String sessionMemoryIntro = FullCompactProcessor.SESSION_MEMORY_SUMMARY_INTRO;

    /**
     * getTriggerTotalTokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getTriggerTotalTokens() {
        return triggerTotalTokens;
    }

    /**
     * getCompressionCallMaxTokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCompressionCallMaxTokens() {
        return compressionCallMaxTokens;
    }

    /**
     * getMessagesToKeep.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMessagesToKeep() {
        return messagesToKeep;
    }

    /**
     * isSessionMemoryEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSessionMemoryEnabled() {
        return sessionMemoryEnabled;
    }

    /**
     * getModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelRequestConfig getModel() {
        return model;
    }

    /**
     * getModelClient.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelClientConfig getModelClient() {
        return modelClient;
    }

    /**
     * isKeepToolMessagePairs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isKeepToolMessagePairs() {
        return isKeepToolMessagePairs;
    }

    /**
     * getStateSnapshotMaxChars.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getStateSnapshotMaxChars() {
        return stateSnapshotMaxChars;
    }

    /**
     * getReinjectRecentSkills.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getReinjectRecentSkills() {
        return reinjectRecentSkills;
    }

    /**
     * getReinjectFileToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getReinjectFileToolNames() {
        return reinjectFileToolNames;
    }

    /**
     * getReinjectToolResultHintNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getReinjectToolResultHintNames() {
        return reinjectToolResultHintNames;
    }

    /**
     * getMarker.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMarker() {
        return marker;
    }

    /**
     * getStateMarker.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getStateMarker() {
        return stateMarker;
    }

    /**
     * getSyntheticUserMarker.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSyntheticUserMarker() {
        return syntheticUserMarker;
    }

    /**
     * getSummaryIntro.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSummaryIntro() {
        return summaryIntro;
    }

    /**
     * getRecentMessagesNotice.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRecentMessagesNotice() {
        return recentMessagesNotice;
    }

    /**
     * getSessionMemoryMarker.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionMemoryMarker() {
        return sessionMemoryMarker;
    }

    /**
     * getSessionMemoryIntro.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionMemoryIntro() {
        return sessionMemoryIntro;
    }

    /**
     * validate.
     * 
     * @since 0.1.7
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
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder {
        private final FullCompactProcessorConfig config = new FullCompactProcessorConfig();

        /**
         * triggerTotalTokens.
         * 
         * @param triggerTotalTokens triggerTotalTokens
         * @return the result
         * @since 0.1.7
         */
        public Builder triggerTotalTokens(int triggerTotalTokens) {
            config.triggerTotalTokens = triggerTotalTokens;
            return this;
        }

        /**
         * compressionCallMaxTokens.
         * 
         * @param compressionCallMaxTokens compressionCallMaxTokens
         * @return the result
         * @since 0.1.7
         */
        public Builder compressionCallMaxTokens(int compressionCallMaxTokens) {
            config.compressionCallMaxTokens = compressionCallMaxTokens;
            return this;
        }

        /**
         * messagesToKeep.
         * 
         * @param messagesToKeep messagesToKeep
         * @return the result
         * @since 0.1.7
         */
        public Builder messagesToKeep(int messagesToKeep) {
            config.messagesToKeep = messagesToKeep;
            return this;
        }

        /**
         * sessionMemoryEnabled.
         * 
         * @param sessionMemoryEnabled sessionMemoryEnabled
         * @return the result
         * @since 0.1.7
         */
        public Builder sessionMemoryEnabled(boolean sessionMemoryEnabled) {
            config.sessionMemoryEnabled = sessionMemoryEnabled;
            return this;
        }

        /**
         * model.
         * 
         * @param model model
         * @return the result
         * @since 0.1.7
         */
        public Builder model(ModelRequestConfig model) {
            config.model = model;
            return this;
        }

        /**
         * modelClient.
         * 
         * @param modelClient modelClient
         * @return the result
         * @since 0.1.7
         */
        public Builder modelClient(ModelClientConfig modelClient) {
            config.modelClient = modelClient;
            return this;
        }

        /**
         * isKeepToolMessagePairs.
         * 
         * @param isKeepToolMessagePairs isKeepToolMessagePairs
         * @return the result
         * @since 0.1.7
         */
        public Builder isKeepToolMessagePairs(boolean isKeepToolMessagePairs) {
            config.isKeepToolMessagePairs = isKeepToolMessagePairs;
            return this;
        }

        /**
         * stateSnapshotMaxChars.
         * 
         * @param stateSnapshotMaxChars stateSnapshotMaxChars
         * @return the result
         * @since 0.1.7
         */
        public Builder stateSnapshotMaxChars(int stateSnapshotMaxChars) {
            config.stateSnapshotMaxChars = stateSnapshotMaxChars;
            return this;
        }

        /**
         * reinjectRecentSkills.
         * 
         * @param reinjectRecentSkills reinjectRecentSkills
         * @return the result
         * @since 0.1.7
         */
        public Builder reinjectRecentSkills(int reinjectRecentSkills) {
            config.reinjectRecentSkills = reinjectRecentSkills;
            return this;
        }

        /**
         * reinjectFileToolNames.
         * 
         * @param reinjectFileToolNames reinjectFileToolNames
         * @return the result
         * @since 0.1.7
         */
        public Builder reinjectFileToolNames(List<String> reinjectFileToolNames) {
            config.reinjectFileToolNames =
                reinjectFileToolNames != null ? new ArrayList<>(reinjectFileToolNames) : new ArrayList<>();
            return this;
        }

        /**
         * reinjectToolResultHintNames.
         * 
         * @param reinjectToolResultHintNames reinjectToolResultHintNames
         * @return the result
         * @since 0.1.7
         */
        public Builder reinjectToolResultHintNames(List<String> reinjectToolResultHintNames) {
            config.reinjectToolResultHintNames =
                reinjectToolResultHintNames != null ? new ArrayList<>(reinjectToolResultHintNames) : new ArrayList<>();
            return this;
        }

        /**
         * marker.
         * 
         * @param marker marker
         * @return the result
         * @since 0.1.7
         */
        public Builder marker(String marker) {
            config.marker = marker;
            return this;
        }

        /**
         * stateMarker.
         * 
         * @param stateMarker stateMarker
         * @return the result
         * @since 0.1.7
         */
        public Builder stateMarker(String stateMarker) {
            config.stateMarker = stateMarker;
            return this;
        }

        /**
         * syntheticUserMarker.
         * 
         * @param syntheticUserMarker syntheticUserMarker
         * @return the result
         * @since 0.1.7
         */
        public Builder syntheticUserMarker(String syntheticUserMarker) {
            config.syntheticUserMarker = syntheticUserMarker;
            return this;
        }

        /**
         * summaryIntro.
         * 
         * @param summaryIntro summaryIntro
         * @return the result
         * @since 0.1.7
         */
        public Builder summaryIntro(String summaryIntro) {
            config.summaryIntro = summaryIntro;
            return this;
        }

        /**
         * recentMessagesNotice.
         * 
         * @param recentMessagesNotice recentMessagesNotice
         * @return the result
         * @since 0.1.7
         */
        public Builder recentMessagesNotice(String recentMessagesNotice) {
            config.recentMessagesNotice = recentMessagesNotice;
            return this;
        }

        /**
         * sessionMemoryMarker.
         * 
         * @param sessionMemoryMarker sessionMemoryMarker
         * @return the result
         * @since 0.1.7
         */
        public Builder sessionMemoryMarker(String sessionMemoryMarker) {
            config.sessionMemoryMarker = sessionMemoryMarker;
            return this;
        }

        /**
         * sessionMemoryIntro.
         * 
         * @param sessionMemoryIntro sessionMemoryIntro
         * @return the result
         * @since 0.1.7
         */
        public Builder sessionMemoryIntro(String sessionMemoryIntro) {
            config.sessionMemoryIntro = sessionMemoryIntro;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public FullCompactProcessorConfig build() {
            config.validate();
            return config;
        }
    }
}
