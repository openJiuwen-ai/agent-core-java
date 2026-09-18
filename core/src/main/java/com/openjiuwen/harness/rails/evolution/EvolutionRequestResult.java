/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.List;
import java.util.Objects;

/**
 * Mirrors Python's {@code EvolutionRequestResult} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EvolutionRequestResult {

    private final String skillName;
    private final String requestId;
    private final OutputSchema approvalEvent;
    private final List<EvolutionRecord> records;
    private final boolean autoApproved;
    private final String status;
    private final String message;

    private EvolutionRequestResult(Builder builder) {
        this.skillName = Objects.requireNonNull(builder.skillName, "skillName is required");
        this.requestId = builder.requestId;
        this.approvalEvent = builder.approvalEvent;
        this.records = List.copyOf(builder.records);
        this.autoApproved = builder.autoApproved;
        this.status = builder.status;
        this.message = builder.message == null ? "" : builder.message;
    }

    public static Builder builder(String skillName) {
        return new Builder(skillName);
    }

    public boolean hasChanges() {
        return !records.isEmpty() || approvalEvent != null;
    }

    public String getSkillName() {
        return skillName;
    }

    public String getRequestId() {
        return requestId;
    }

    public OutputSchema getApprovalEvent() {
        return approvalEvent;
    }

    public List<EvolutionRecord> getRecords() {
        return records;
    }

    public boolean isAutoApproved() {
        return autoApproved;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static final class Builder {
        private final String skillName;
        private String requestId;
        private OutputSchema approvalEvent;
        private List<EvolutionRecord> records = List.of();
        private boolean autoApproved;
        private String status;
        private String message = "";

        private Builder(String skillName) {
            this.skillName = skillName;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder approvalEvent(OutputSchema approvalEvent) {
            this.approvalEvent = approvalEvent;
            return this;
        }

        public Builder records(List<EvolutionRecord> records) {
            this.records = records == null ? List.of() : List.copyOf(records);
            return this;
        }

        public Builder autoApproved(boolean autoApproved) {
            this.autoApproved = autoApproved;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public EvolutionRequestResult build() {
            return new EvolutionRequestResult(this);
        }
    }
}
