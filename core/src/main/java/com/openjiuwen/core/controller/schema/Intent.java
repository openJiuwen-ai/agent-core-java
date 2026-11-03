/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intent data model.
 * <p>
 * Represents a user's intent, containing intent type, associated event,
 * target task, and other information. The intent recognizer converts user
 * input events into Intent objects, then routes them to appropriate
 * processing logic based on intent type.
 * <p>
 * Mirrors Python's {@code Intent} in
 * {@code openjiuwen/core/controller/schema/intent.py}.
 */
public class Intent {

    @JsonProperty("intent_type")
    private IntentType intentType;

    private Event event;

    @JsonProperty("target_task_id")
    private String targetTaskId;

    @JsonProperty("target_task_description")
    private String targetTaskDescription;

    @JsonProperty("depend_task_id")
    private List<String> dependTaskId;

    @JsonProperty("supplementary_info")
    private String supplementaryInfo;

    @JsonProperty("modification_details")
    private String modificationDetails;

    private double confidence;

    private Map<String, Object> metadata;

    @JsonProperty("clarification_prompt")
    private String clarificationPrompt;

    public Intent(IntentType intentType, Event event, String targetTaskId) {
        this(intentType, event, targetTaskId, null, null, null, null, null, null, null);
    }

    public Intent(IntentType intentType, Event event, String targetTaskId,
                  String targetTaskDescription, List<String> dependTaskId,
                  String supplementaryInfo, String modificationDetails,
                  double confidence, String clarificationPrompt) {
        this(intentType, event, targetTaskId, targetTaskDescription, dependTaskId,
                supplementaryInfo, modificationDetails, confidence, null, clarificationPrompt);
    }

    @JsonCreator
    public Intent(@JsonProperty("intent_type") IntentType intentType,
                  @JsonProperty("event") Event event,
                  @JsonProperty("target_task_id") String targetTaskId,
                  @JsonProperty("target_task_description") String targetTaskDescription,
                  @JsonProperty("depend_task_id") List<String> dependTaskId,
                  @JsonProperty("supplementary_info") String supplementaryInfo,
                  @JsonProperty("modification_details") String modificationDetails,
                  @JsonProperty("confidence") Double confidence,
                  @JsonProperty("metadata") Map<String, Object> metadata,
                  @JsonProperty("clarification_prompt") String clarificationPrompt) {
        this.intentType = intentType;
        this.event = event;
        this.targetTaskId = targetTaskId;
        this.targetTaskDescription = targetTaskDescription;
        this.dependTaskId = dependTaskId;
        this.supplementaryInfo = supplementaryInfo;
        this.modificationDetails = modificationDetails;
        this.confidence = confidence != null ? confidence : 1.0;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        this.clarificationPrompt = clarificationPrompt;
        validate();
    }

    /**
     * Validate intent data.
     */
    private void validate() {
        if (confidence < 0.0 || confidence > 1.0) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Confidence must be between 0.0 and 1.0, got " + confidence);
        }

        switch (intentType) {
            case CREATE_TASK -> {
                if (isMissingPythonString(targetTaskDescription)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "CREATE_TASK intent requires target_task_description");
                }
            }
            case CONTINUE_TASK -> {
                if (dependTaskId == null || dependTaskId.isEmpty()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "CONTINUE_TASK intent requires depend_task_id");
                }
            }
            case SUPPLEMENT_TASK -> {
                if (isMissingPythonString(targetTaskId)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "SUPPLEMENT_TASK intent requires target_task_id");
                }
                if (isMissingPythonString(supplementaryInfo)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "SUPPLEMENT_TASK intent requires supplementary_info");
                }
            }
            case MODIFY_TASK -> {
                if (isMissingPythonString(targetTaskId)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "MODIFY_TASK intent requires target_task_id");
                }
                if (isMissingPythonString(modificationDetails)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "MODIFY_TASK intent requires modification_details");
                }
            }
            case PAUSE_TASK, RESUME_TASK, CANCEL_TASK -> {
                if (isMissingPythonString(targetTaskId)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", intentType.getValue() + " intent requires target_task_id");
                }
            }
            case SWITCH_TASK -> {
                if (isMissingPythonString(targetTaskDescription)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "SWITCH_TASK intent requires target_task_description");
                }
            }
            case UNKNOWN_TASK -> {
                if (isMissingPythonString(clarificationPrompt)) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "UNKNOWN_TASK intent requires clarification_prompt");
                }
            }
        }
    }

    private static boolean isMissingPythonString(String value) {
        return value == null || value.isEmpty();
    }

    // Getters and setters

    public IntentType getIntentType() {
        return intentType;
    }

    public void setIntentType(IntentType intentType) {
        this.intentType = intentType;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getTargetTaskId() {
        return targetTaskId;
    }

    public void setTargetTaskId(String targetTaskId) {
        this.targetTaskId = targetTaskId;
    }

    public String getTargetTaskDescription() {
        return targetTaskDescription;
    }

    public void setTargetTaskDescription(String targetTaskDescription) {
        this.targetTaskDescription = targetTaskDescription;
    }

    public List<String> getDependTaskId() {
        return dependTaskId;
    }

    public void setDependTaskId(List<String> dependTaskId) {
        this.dependTaskId = dependTaskId;
    }

    public String getSupplementaryInfo() {
        return supplementaryInfo;
    }

    public void setSupplementaryInfo(String supplementaryInfo) {
        this.supplementaryInfo = supplementaryInfo;
    }

    public String getModificationDetails() {
        return modificationDetails;
    }

    public void setModificationDetails(String modificationDetails) {
        this.modificationDetails = modificationDetails;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public String getClarificationPrompt() {
        return clarificationPrompt;
    }

    public void setClarificationPrompt(String clarificationPrompt) {
        this.clarificationPrompt = clarificationPrompt;
    }
}
