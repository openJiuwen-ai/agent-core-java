/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of applying one normalized update to one evolution target.
 *
 * <p>Mirrors Python's {@code ApplyResult} in
 * {@code openjiuwen/agent_evolving/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ApplyResult {

    private final String operatorId;
    private final String target;
    private final boolean applied;
    private final String mode;
    private final String effect;
    private final Object value;
    private final List<Object> records;
    private final String changeType;
    private final String lifecycleStage;
    private final String pendingChangeId;
    private final List<String> errors;
    private final Map<String, Object> metadata;

    public ApplyResult(String operatorId, String target, boolean applied) {
        this(
                operatorId,
                target,
                applied,
                Protocols.REPLACE_MODE,
                Protocols.STATE_EFFECT,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                Map.of()
        );
    }

    public ApplyResult(
            String operatorId,
            String target,
            boolean applied,
            String mode,
            String effect,
            Object value,
            List<Object> records,
            String changeType,
            String lifecycleStage,
            String pendingChangeId,
            List<String> errors,
            Map<String, Object> metadata
    ) {
        this.operatorId = operatorId;
        this.target = target;
        this.applied = applied;
        this.mode = mode == null ? Protocols.REPLACE_MODE : mode;
        this.effect = effect == null ? Protocols.STATE_EFFECT : effect;
        this.value = value;
        this.records = copyList(records);
        this.changeType = changeType;
        this.lifecycleStage = lifecycleStage;
        this.pendingChangeId = pendingChangeId;
        this.errors = copyList(errors);
        this.metadata = copyMap(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getTarget() {
        return target;
    }

    public boolean isApplied() {
        return applied;
    }

    public String getMode() {
        return mode;
    }

    public String getEffect() {
        return effect;
    }

    public Object getValue() {
        return value;
    }

    public List<Object> getRecords() {
        return copyList(records);
    }

    public String getChangeType() {
        return changeType;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public String getPendingChangeId() {
        return pendingChangeId;
    }

    public List<String> getErrors() {
        return copyList(errors);
    }

    public Map<String, Object> getMetadata() {
        return copyMap(metadata);
    }

    public boolean isOk() {
        return applied && errors.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApplyResult that)) {
            return false;
        }
        return applied == that.applied
                && Objects.equals(operatorId, that.operatorId)
                && Objects.equals(target, that.target)
                && Objects.equals(mode, that.mode)
                && Objects.equals(effect, that.effect)
                && Objects.equals(value, that.value)
                && Objects.equals(records, that.records)
                && Objects.equals(changeType, that.changeType)
                && Objects.equals(lifecycleStage, that.lifecycleStage)
                && Objects.equals(pendingChangeId, that.pendingChangeId)
                && Objects.equals(errors, that.errors)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                operatorId,
                target,
                applied,
                mode,
                effect,
                value,
                records,
                changeType,
                lifecycleStage,
                pendingChangeId,
                errors,
                metadata
        );
    }

    @Override
    public String toString() {
        return "ApplyResult{"
                + "operatorId='" + operatorId + '\''
                + ", target='" + target + '\''
                + ", applied=" + applied
                + ", mode='" + mode + '\''
                + ", effect='" + effect + '\''
                + ", value=" + value
                + ", records=" + records
                + ", changeType='" + changeType + '\''
                + ", lifecycleStage='" + lifecycleStage + '\''
                + ", pendingChangeId='" + pendingChangeId + '\''
                + ", errors=" + errors
                + ", metadata=" + metadata
                + '}';
    }

    private static <T> List<T> copyList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(values));
    }

    private static Map<String, Object> copyMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public static final class Builder {
        private String operatorId;
        private String target;
        private boolean applied;
        private String mode = Protocols.REPLACE_MODE;
        private String effect = Protocols.STATE_EFFECT;
        private Object value;
        private List<Object> records = List.of();
        private String changeType;
        private String lifecycleStage;
        private String pendingChangeId;
        private List<String> errors = List.of();
        private Map<String, Object> metadata = Map.of();

        private Builder() {
        }

        public Builder operatorId(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder applied(boolean applied) {
            this.applied = applied;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder effect(String effect) {
            this.effect = effect;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder records(List<Object> records) {
            this.records = records;
            return this;
        }

        public Builder changeType(String changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder lifecycleStage(String lifecycleStage) {
            this.lifecycleStage = lifecycleStage;
            return this;
        }

        public Builder pendingChangeId(String pendingChangeId) {
            this.pendingChangeId = pendingChangeId;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ApplyResult build() {
            return new ApplyResult(
                    operatorId,
                    target,
                    applied,
                    mode,
                    effect,
                    value,
                    records,
                    changeType,
                    lifecycleStage,
                    pendingChangeId,
                    errors,
                    metadata
            );
        }
    }
}
