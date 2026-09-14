/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code SimplifyRequestResult} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class SimplifyRequestResult {

    private final String skillName;
    private final String requestId;
    private final OutputSchema approvalEvent;
    private final List<Map<String, Object>> actions;

    private SimplifyRequestResult(Builder builder) {
        this.skillName = Objects.requireNonNull(builder.skillName, "skillName is required");
        this.requestId = builder.requestId;
        this.approvalEvent = builder.approvalEvent;
        this.actions = copyActions(builder.actions);
    }

    public static Builder builder(String skillName) {
        return new Builder(skillName);
    }

    public boolean hasChanges() {
        return !actions.isEmpty() || approvalEvent != null;
    }

    private static List<Map<String, Object>> copyActions(List<Map<String, Object>> source) {
        if (source == null) {
            return List.of();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> action : source) {
            copied.add(action == null ? new LinkedHashMap<>() : new LinkedHashMap<>(action));
        }
        return List.copyOf(copied);
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

    public List<Map<String, Object>> getActions() {
        return copyActions(actions);
    }

    public static final class Builder {
        private final String skillName;
        private String requestId;
        private OutputSchema approvalEvent;
        private List<Map<String, Object>> actions = List.of();

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

        public Builder actions(List<Map<String, Object>> actions) {
            this.actions = actions == null ? List.of() : copyActions(actions);
            return this;
        }

        public SimplifyRequestResult build() {
            return new SimplifyRequestResult(this);
        }
    }
}
