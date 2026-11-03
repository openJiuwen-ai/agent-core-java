/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured update contract shared by online and offline apply paths.
 *
 * <p>Mirrors Python's {@code UpdateValue} in
 * {@code openjiuwen/agent_evolving/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class UpdateValue {

    private final Object payload;
    private final String mode;
    private final String effect;
    private final String changeType;
    private final Map<String, Object> metadata;

    public UpdateValue(Object payload) {
        this(payload, Protocols.REPLACE_MODE, Protocols.STATE_EFFECT, null, Map.of());
    }

    public UpdateValue(
            Object payload,
            String mode,
            String effect,
            String changeType,
            Map<String, Object> metadata
    ) {
        this.payload = payload;
        this.mode = mode == null ? Protocols.REPLACE_MODE : mode;
        this.effect = effect == null ? Protocols.STATE_EFFECT : effect;
        this.changeType = changeType;
        this.metadata = copyMap(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Object getPayload() {
        return payload;
    }

    public String getMode() {
        return mode;
    }

    public String getEffect() {
        return effect;
    }

    public String getChangeType() {
        return changeType;
    }

    public Map<String, Object> getMetadata() {
        return copyMap(metadata);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UpdateValue that)) {
            return false;
        }
        return Objects.equals(payload, that.payload)
                && Objects.equals(mode, that.mode)
                && Objects.equals(effect, that.effect)
                && Objects.equals(changeType, that.changeType)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, mode, effect, changeType, metadata);
    }

    @Override
    public String toString() {
        return "UpdateValue{"
                + "payload=" + payload
                + ", mode='" + mode + '\''
                + ", effect='" + effect + '\''
                + ", changeType='" + changeType + '\''
                + ", metadata=" + metadata
                + '}';
    }

    private static Map<String, Object> copyMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public static final class Builder {
        private Object payload;
        private String mode = Protocols.REPLACE_MODE;
        private String effect = Protocols.STATE_EFFECT;
        private String changeType;
        private Map<String, Object> metadata = Map.of();

        private Builder() {
        }

        public Builder payload(Object payload) {
            this.payload = payload;
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

        public Builder changeType(String changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public UpdateValue build() {
            return new UpdateValue(payload, mode, effect, changeType, metadata);
        }
    }
}
