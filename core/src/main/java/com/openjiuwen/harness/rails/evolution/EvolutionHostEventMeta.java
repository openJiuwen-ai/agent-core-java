/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code EvolutionHostEventMeta} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EvolutionHostEventMeta {

    private final EvolutionEventKind eventKind;
    private final String railKind;
    private final String stage;
    private final String skillName;
    private final String requestId;
    private final String signalType;
    private final String source;
    private final String status;

    private EvolutionHostEventMeta(Builder builder) {
        this.eventKind = Objects.requireNonNull(builder.eventKind, "eventKind is required");
        this.railKind = builder.railKind;
        this.stage = builder.stage;
        this.skillName = builder.skillName;
        this.requestId = builder.requestId;
        this.signalType = builder.signalType;
        this.source = builder.source;
        this.status = builder.status;
    }

    public static Builder builder(EvolutionEventKind eventKind) {
        return new Builder(eventKind);
    }

    public Map<String, String> toPayload() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("event_kind", eventKind.value());
        putIfPresent(payload, "rail_kind", railKind);
        putIfPresent(payload, "stage", stage);
        putIfPresent(payload, "skill_name", skillName);
        putIfPresent(payload, "request_id", requestId);
        putIfPresent(payload, "signal_type", signalType);
        putIfPresent(payload, "source", source);
        putIfPresent(payload, "status", status);
        return payload;
    }

    private static void putIfPresent(Map<String, String> payload, String key, String value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    public EvolutionEventKind getEventKind() {
        return eventKind;
    }

    public String getRailKind() {
        return railKind;
    }

    public String getStage() {
        return stage;
    }

    public String getSkillName() {
        return skillName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSignalType() {
        return signalType;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public static final class Builder {
        private final EvolutionEventKind eventKind;
        private String railKind;
        private String stage;
        private String skillName;
        private String requestId;
        private String signalType;
        private String source;
        private String status;

        private Builder(EvolutionEventKind eventKind) {
            this.eventKind = eventKind;
        }

        public Builder railKind(String railKind) {
            this.railKind = railKind;
            return this;
        }

        public Builder stage(String stage) {
            this.stage = stage;
            return this;
        }

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder signalType(String signalType) {
            this.signalType = signalType;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public EvolutionHostEventMeta build() {
            return new EvolutionHostEventMeta(this);
        }
    }
}
