/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;

/**
 * Configuration for {@link FullCompactProcessor}.
 *
 * <p>Mirrors Python's {@code FullCompactProcessorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/full_compact_processor.py}.</p>
 */
public class FullCompactProcessorConfig {
    @JsonProperty("trigger_total_tokens")
    private int triggerTotalTokens = 180000;

    @JsonProperty("compression_call_max_tokens")
    private int compressionCallMaxTokens = 200000;

    @JsonProperty("messages_to_keep")
    private int messagesToKeep = 10;

    @JsonProperty("session_memory_enabled")
    private boolean sessionMemoryEnabled = true;

    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    @JsonProperty("keep_tool_message_pairs")
    private boolean keepToolMessagePairs = true;

    @JsonProperty("state_snapshot_max_chars")
    private int stateSnapshotMaxChars = 4000;

    @JsonProperty("reinject_recent_skills")
    private int reinjectRecentSkills = 3;

    @JsonProperty("reinject_file_tool_names")
    private List<String> reinjectFileToolNames = List.of("read_file", "write_file", "edit_file", "glob", "grep");

    @JsonProperty("reinject_tool_result_hint_names")
    private List<String> reinjectToolResultHintNames =
            List.of("read_file", "write_file", "edit_file", "glob", "grep");

    private String marker = FullCompactProcessor.FULL_COMPACT_BOUNDARY_MARKER;

    @JsonProperty("state_marker")
    private String stateMarker = FullCompactProcessor.FULL_COMPACT_STATE_MARKER;

    @JsonProperty("synthetic_user_marker")
    private String syntheticUserMarker = FullCompactProcessor.FULL_COMPACT_SYNTHETIC_USER_MARKER;

    @JsonProperty("summary_intro")
    private String summaryIntro = FullCompactProcessor.FULL_COMPACT_SUMMARY_INTRO;

    @JsonProperty("recent_messages_notice")
    private String recentMessagesNotice = FullCompactProcessor.FULL_COMPACT_RECENT_MESSAGES_NOTICE;

    @JsonProperty("session_memory_marker")
    private String sessionMemoryMarker = FullCompactProcessor.SESSION_MEMORY_BOUNDARY_MARKER;

    @JsonProperty("session_memory_intro")
    private String sessionMemoryIntro = FullCompactProcessor.SESSION_MEMORY_SUMMARY_INTRO;

    public int getTriggerTotalTokens() {
        return triggerTotalTokens;
    }

    public void setTriggerTotalTokens(int triggerTotalTokens) {
        validateGt(triggerTotalTokens, "trigger_total_tokens");
        this.triggerTotalTokens = triggerTotalTokens;
    }

    public int getCompressionCallMaxTokens() {
        return compressionCallMaxTokens;
    }

    public void setCompressionCallMaxTokens(int compressionCallMaxTokens) {
        validateGt(compressionCallMaxTokens, "compression_call_max_tokens");
        this.compressionCallMaxTokens = compressionCallMaxTokens;
    }

    public int getMessagesToKeep() {
        return messagesToKeep;
    }

    public void setMessagesToKeep(int messagesToKeep) {
        validateGe(messagesToKeep, "messages_to_keep");
        this.messagesToKeep = messagesToKeep;
    }

    public boolean isSessionMemoryEnabled() {
        return sessionMemoryEnabled;
    }

    public void setSessionMemoryEnabled(boolean sessionMemoryEnabled) {
        this.sessionMemoryEnabled = sessionMemoryEnabled;
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

    public boolean isKeepToolMessagePairs() {
        return keepToolMessagePairs;
    }

    public void setKeepToolMessagePairs(boolean keepToolMessagePairs) {
        this.keepToolMessagePairs = keepToolMessagePairs;
    }

    public int getStateSnapshotMaxChars() {
        return stateSnapshotMaxChars;
    }

    public void setStateSnapshotMaxChars(int stateSnapshotMaxChars) {
        validateGt(stateSnapshotMaxChars, "state_snapshot_max_chars");
        this.stateSnapshotMaxChars = stateSnapshotMaxChars;
    }

    public int getReinjectRecentSkills() {
        return reinjectRecentSkills;
    }

    public void setReinjectRecentSkills(int reinjectRecentSkills) {
        validateGe(reinjectRecentSkills, "reinject_recent_skills");
        this.reinjectRecentSkills = reinjectRecentSkills;
    }

    public List<String> getReinjectFileToolNames() {
        return reinjectFileToolNames;
    }

    public void setReinjectFileToolNames(List<String> reinjectFileToolNames) {
        this.reinjectFileToolNames = reinjectFileToolNames == null ? List.of() : List.copyOf(reinjectFileToolNames);
    }

    public List<String> getReinjectToolResultHintNames() {
        return reinjectToolResultHintNames;
    }

    public void setReinjectToolResultHintNames(List<String> reinjectToolResultHintNames) {
        this.reinjectToolResultHintNames = reinjectToolResultHintNames == null
                ? List.of()
                : List.copyOf(reinjectToolResultHintNames);
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker == null ? "" : marker;
    }

    public String getStateMarker() {
        return stateMarker;
    }

    public void setStateMarker(String stateMarker) {
        this.stateMarker = stateMarker == null ? "" : stateMarker;
    }

    public String getSyntheticUserMarker() {
        return syntheticUserMarker;
    }

    public void setSyntheticUserMarker(String syntheticUserMarker) {
        this.syntheticUserMarker = syntheticUserMarker == null ? "" : syntheticUserMarker;
    }

    public String getSummaryIntro() {
        return summaryIntro;
    }

    public void setSummaryIntro(String summaryIntro) {
        this.summaryIntro = summaryIntro == null ? "" : summaryIntro;
    }

    public String getRecentMessagesNotice() {
        return recentMessagesNotice;
    }

    public void setRecentMessagesNotice(String recentMessagesNotice) {
        this.recentMessagesNotice = recentMessagesNotice == null ? "" : recentMessagesNotice;
    }

    public String getSessionMemoryMarker() {
        return sessionMemoryMarker;
    }

    public void setSessionMemoryMarker(String sessionMemoryMarker) {
        this.sessionMemoryMarker = sessionMemoryMarker == null ? "" : sessionMemoryMarker;
    }

    public String getSessionMemoryIntro() {
        return sessionMemoryIntro;
    }

    public void setSessionMemoryIntro(String sessionMemoryIntro) {
        this.sessionMemoryIntro = sessionMemoryIntro == null ? "" : sessionMemoryIntro;
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
