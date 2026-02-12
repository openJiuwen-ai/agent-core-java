// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.HashMap;
import java.util.Map;

/**
 * Intent Data Model.
 *
 * <p>Represents a user's intent, containing intent type, associated event, target task,
 * and other information. The intent recognizer (IntentRecognizer) will convert user input
 * events into Intent objects, then route them to appropriate processing logic based on
 * intent type.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class Intent {

    private final IntentType intentType;
    private final Event event;
    private final String targetTaskId;
    private final String targetTaskDescription;
    private final String dependTaskId;
    private final Map<String, Object> supplementaryInfo;
    private final Map<String, Object> modificationDetails;
    private final double confidence;
    private final Map<String, Object> metadata;
    private final String clarificationPrompt;

    private Intent(IntentBuilder builder) {
        this.intentType = builder.intentType;
        this.event = builder.event;
        this.targetTaskId = builder.targetTaskId;
        this.targetTaskDescription = builder.targetTaskDescription;
        this.dependTaskId = builder.dependTaskId;
        this.supplementaryInfo = builder.supplementaryInfo;
        this.modificationDetails = builder.modificationDetails;
        this.confidence = builder.confidence;
        this.metadata = builder.metadata != null ? builder.metadata : new HashMap<>();
        this.clarificationPrompt = builder.clarificationPrompt;

        validate();
    }

    /**
     * Creates a new builder.
     *
     * @param intentType the intent type
     * @param event      the associated event
     * @return a new IntentBuilder
     */
    public static IntentBuilder builder(IntentType intentType, Event event) {
        return new IntentBuilder(intentType, event);
    }

    /**
     * Validates intent data.
     *
     * @throws BaseError if validation fails
     */
    private void validate() {
        // Validate confidence range
        if (confidence < 0.0 || confidence > 1.0) {
            throw buildIntentError("Confidence must be between 0.0 and 1.0, got " + confidence);
        }

        // Validate intent-specific required fields
        switch (intentType) {
            case CREATE_TASK -> {
                if (targetTaskDescription == null || targetTaskDescription.isBlank()) {
                    throw buildIntentError("CREATE_TASK intent requires target_task_description");
                }
            }
            case CONTINUE_TASK -> {
                if (dependTaskId == null || dependTaskId.isBlank()) {
                    throw buildIntentError("CONTINUE_TASK intent requires depend_task_id");
                }
            }
            case SUPPLEMENT_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw buildIntentError("SUPPLEMENT_TASK intent requires target_task_id");
                }
                if (supplementaryInfo == null || supplementaryInfo.isEmpty()) {
                    throw buildIntentError("SUPPLEMENT_TASK intent requires supplementary_info");
                }
            }
            case MODIFY_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw buildIntentError("MODIFY_TASK intent requires target_task_id");
                }
                if (modificationDetails == null || modificationDetails.isEmpty()) {
                    throw buildIntentError("MODIFY_TASK intent requires modification_details");
                }
            }
            case PAUSE_TASK, RESUME_TASK, CANCEL_TASK -> {
                if (targetTaskId == null || targetTaskId.isBlank()) {
                    throw buildIntentError(intentType.getValue() + " intent requires target_task_id");
                }
            }
            case SWITCH_TASK -> {
                if (targetTaskDescription == null || targetTaskDescription.isBlank()) {
                    throw buildIntentError("SWITCH_TASK intent requires target_task_description");
                }
            }
            case UNKNOWN_TASK -> {
                if (clarificationPrompt == null || clarificationPrompt.isBlank()) {
                    throw buildIntentError("UNKNOWN_TASK intent requires clarification_prompt");
                }
            }
        }
    }

    /**
     * Builds a BaseError for intent validation failures.
     */
    private static BaseError buildIntentError(String errorMsg) {
        return ErrorBuilder.build(
            StatusCode.AGENT_CONTROLLER_INTENT_PARAM_ERROR,
            null,
            null,
            null,
            Map.of("error_msg", errorMsg)
        );
    }

    // Getters

    public IntentType getIntentType() {
        return intentType;
    }

    public Event getEvent() {
        return event;
    }

    public String getTargetTaskId() {
        return targetTaskId;
    }

    public String getTargetTaskDescription() {
        return targetTaskDescription;
    }

    public String getDependTaskId() {
        return dependTaskId;
    }

    public Map<String, Object> getSupplementaryInfo() {
        return supplementaryInfo;
    }

    public Map<String, Object> getModificationDetails() {
        return modificationDetails;
    }

    public double getConfidence() {
        return confidence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getClarificationPrompt() {
        return clarificationPrompt;
    }

    /**
     * Builder for Intent.
     */
    public static class IntentBuilder {
        private final IntentType intentType;
        private final Event event;
        private String targetTaskId;
        private String targetTaskDescription;
        private String dependTaskId;
        private Map<String, Object> supplementaryInfo;
        private Map<String, Object> modificationDetails;
        private double confidence = 1.0;
        private Map<String, Object> metadata;
        private String clarificationPrompt;

        public IntentBuilder(IntentType intentType, Event event) {
            this.intentType = intentType;
            this.event = event;
        }

        public IntentBuilder targetTaskId(String targetTaskId) {
            this.targetTaskId = targetTaskId;
            return this;
        }

        public IntentBuilder targetTaskDescription(String targetTaskDescription) {
            this.targetTaskDescription = targetTaskDescription;
            return this;
        }

        public IntentBuilder dependTaskId(String dependTaskId) {
            this.dependTaskId = dependTaskId;
            return this;
        }

        public IntentBuilder supplementaryInfo(Map<String, Object> supplementaryInfo) {
            this.supplementaryInfo = supplementaryInfo;
            return this;
        }

        public IntentBuilder modificationDetails(Map<String, Object> modificationDetails) {
            this.modificationDetails = modificationDetails;
            return this;
        }

        public IntentBuilder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public IntentBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public IntentBuilder clarificationPrompt(String clarificationPrompt) {
            this.clarificationPrompt = clarificationPrompt;
            return this;
        }

        /**
         * Builds and validates the Intent.
         *
         * @return the validated Intent
         * @throws BaseError if validation fails
         */
        public Intent build() {
            return new Intent(this);
        }
    }
}

