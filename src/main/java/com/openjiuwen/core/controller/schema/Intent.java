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
 * 
 * @since 0.1.7
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
     * Intent.
     * 
     * @param intentType intentType
     * @param event event
     * @param targetTaskId targetTaskId
     * @since 0.1.7
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
     * Intent.
     * 
     * @param intentType intentType
     * @param event event
     * @param targetTaskId targetTaskId
     * @param targetTaskDescription targetTaskDescription
     * @param dependTaskId dependTaskId
     * @param supplementaryInfo supplementaryInfo
     * @param modificationDetails modificationDetails
     * @param confidence confidence
     * @param clarificationPrompt clarificationPrompt
     * @since 0.1.7
     */
    public Intent(IntentType intentType, Event event, String targetTaskId, String targetTaskDescription,
            List<String> dependTaskId, String supplementaryInfo, String modificationDetails, double confidence,
            String clarificationPrompt) {
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
     * 
     * @since 0.1.7
     */
    private void validate() {
        if (confidence < 0.0 || confidence > 1.0) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                    "Confidence must be between 0.0 and 1.0, got " + confidence);
        }

        switch (intentType) {
            case CREATE_TASK -> {
                if (targetTaskDescription == null || targetTaskDescription.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "CREATE_TASK intent requires target_task_description");
                }
            }
            case CONTINUE_TASK -> {
                if (dependTaskId == null || dependTaskId.isEmpty()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "CONTINUE_TASK intent requires depend_task_id");
                }
            }
            case SUPPLEMENT_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "SUPPLEMENT_TASK intent requires target_task_id");
                }
                if (supplementaryInfo == null || supplementaryInfo.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "SUPPLEMENT_TASK intent requires supplementary_info");
                }
            }
            case MODIFY_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "MODIFY_TASK intent requires target_task_id");
                }
                if (modificationDetails == null || modificationDetails.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "MODIFY_TASK intent requires modification_details");
                }
            }
            case PAUSE_TASK, RESUME_TASK, CANCEL_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            intentType.getValue() + " intent requires target_task_id");
                }
            }
            case SWITCH_TASK -> {
                if (targetTaskDescription == null || targetTaskDescription.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "SWITCH_TASK intent requires target_task_description");
                }
            }
            case UNKNOWN_TASK -> {
                if (clarificationPrompt == null || clarificationPrompt.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                            "UNKNOWN_TASK intent requires clarification_prompt");
                }
            }
            default -> throw new IllegalStateException("Unexpected intent type: " + intentType);
        }
    }

    // Getters and setters

    /**
     * getIntentType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public IntentType getIntentType() {
        return intentType;
    }

    /**
     * setIntentType.
     * 
     * @param intentType intentType
     * @since 0.1.7
     */
    public void setIntentType(IntentType intentType) {
        this.intentType = intentType;
    }

    /**
     * getEvent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Event getEvent() {
        return event;
    }

    /**
     * setEvent.
     * 
     * @param event event
     * @since 0.1.7
     */
    public void setEvent(Event event) {
        this.event = event;
    }

    /**
     * getTargetTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTargetTaskId() {
        return targetTaskId;
    }

    /**
     * setTargetTaskId.
     * 
     * @param targetTaskId targetTaskId
     * @since 0.1.7
     */
    public void setTargetTaskId(String targetTaskId) {
        this.targetTaskId = targetTaskId;
    }

    /**
     * getTargetTaskDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTargetTaskDescription() {
        return targetTaskDescription;
    }

    /**
     * setTargetTaskDescription.
     * 
     * @param targetTaskDescription targetTaskDescription
     * @since 0.1.7
     */
    public void setTargetTaskDescription(String targetTaskDescription) {
        this.targetTaskDescription = targetTaskDescription;
    }

    /**
     * getDependTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getDependTaskId() {
        return dependTaskId;
    }

    /**
     * setDependTaskId.
     * 
     * @param dependTaskId dependTaskId
     * @since 0.1.7
     */
    public void setDependTaskId(List<String> dependTaskId) {
        this.dependTaskId = dependTaskId;
    }

    /**
     * getSupplementaryInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSupplementaryInfo() {
        return supplementaryInfo;
    }

    /**
     * setSupplementaryInfo.
     * 
     * @param supplementaryInfo supplementaryInfo
     * @since 0.1.7
     */
    public void setSupplementaryInfo(String supplementaryInfo) {
        this.supplementaryInfo = supplementaryInfo;
    }

    /**
     * getModificationDetails.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModificationDetails() {
        return modificationDetails;
    }

    /**
     * setModificationDetails.
     * 
     * @param modificationDetails modificationDetails
     * @since 0.1.7
     */
    public void setModificationDetails(String modificationDetails) {
        this.modificationDetails = modificationDetails;
    }

    /**
     * getConfidence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * setConfidence.
     * 
     * @param confidence confidence
     * @since 0.1.7
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * getClarificationPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getClarificationPrompt() {
        return clarificationPrompt;
    }

    /**
     * setClarificationPrompt.
     * 
     * @param clarificationPrompt clarificationPrompt
     * @since 0.1.7
     */
    public void setClarificationPrompt(String clarificationPrompt) {
        this.clarificationPrompt = clarificationPrompt;
    }
}
