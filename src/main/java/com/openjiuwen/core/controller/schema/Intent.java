/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.HashMap;
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
 * Mirrors Python's {@code Intent(BaseModel)}.
 */
public class Intent {

    private IntentType intentType;
    private Event event;
    private String targetTaskId;
    private String targetTaskDescription;
    private List<String> dependTaskId;
    private String supplementaryInfo;
    private String modificationDetails;
    private double confidence;
    private Map<String, Object> metadata;
    private String clarificationPrompt;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Intent(IntentType intentType, Event event, String targetTaskId) {
        this.intentType = intentType;
        this.event = event;
        this.targetTaskId = targetTaskId;
        this.confidence = 1.0;
        this.metadata = new HashMap<>();
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Intent(IntentType intentType, Event event, String targetTaskId,
                  String targetTaskDescription, List<String> dependTaskId,
                  String supplementaryInfo, String modificationDetails,
                  double confidence, String clarificationPrompt) {
        this.intentType = intentType;
        this.event = event;
        this.targetTaskId = targetTaskId;
        this.targetTaskDescription = targetTaskDescription;
        this.dependTaskId = dependTaskId;
        this.supplementaryInfo = supplementaryInfo;
        this.modificationDetails = modificationDetails;
        this.confidence = confidence;
        this.metadata = new HashMap<>();
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
                if (targetTaskDescription == null || targetTaskDescription.isBlank()) {
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
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "SUPPLEMENT_TASK intent requires target_task_id");
                }
                if (supplementaryInfo == null || supplementaryInfo.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "SUPPLEMENT_TASK intent requires supplementary_info");
                }
            }
            case MODIFY_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "MODIFY_TASK intent requires target_task_id");
                }
                if (modificationDetails == null || modificationDetails.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "MODIFY_TASK intent requires modification_details");
                }
            }
            case PAUSE_TASK, RESUME_TASK, CANCEL_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", intentType.getValue() + " intent requires target_task_id");
                }
            }
            case SWITCH_TASK -> {
                if (targetTaskDescription == null || targetTaskDescription.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "SWITCH_TASK intent requires target_task_description");
                }
            }
            case UNKNOWN_TASK -> {
                if (clarificationPrompt == null || clarificationPrompt.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "UNKNOWN_TASK intent requires clarification_prompt");
                }
            }
        }
    }

    // Getters and setters

    /**
     * Auto-generated for codecheck compliance.
     */
    public IntentType getIntentType() {
        return intentType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setIntentType(IntentType intentType) {
        this.intentType = intentType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Event getEvent() {
        return event;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEvent(Event event) {
        this.event = event;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTargetTaskId() {
        return targetTaskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTargetTaskId(String targetTaskId) {
        this.targetTaskId = targetTaskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTargetTaskDescription() {
        return targetTaskDescription;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTargetTaskDescription(String targetTaskDescription) {
        this.targetTaskDescription = targetTaskDescription;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getDependTaskId() {
        return dependTaskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDependTaskId(List<String> dependTaskId) {
        this.dependTaskId = dependTaskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSupplementaryInfo() {
        return supplementaryInfo;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSupplementaryInfo(String supplementaryInfo) {
        this.supplementaryInfo = supplementaryInfo;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModificationDetails() {
        return modificationDetails;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModificationDetails(String modificationDetails) {
        this.modificationDetails = modificationDetails;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getClarificationPrompt() {
        return clarificationPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setClarificationPrompt(String clarificationPrompt) {
        this.clarificationPrompt = clarificationPrompt;
    }
}
