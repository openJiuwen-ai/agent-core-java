/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

/**
 * Backward-compatible application-schema alias for legacy agent constraints.
 *
 * <p>Mirrors Python's {@code ConstrainConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
public class ConstrainConfig extends com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig {

    public ConstrainConfig() {
        super();
    }

    public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration) {
        super();
        if (reservedMaxChatRounds != null) {
            setReservedMaxChatRounds(reservedMaxChatRounds);
        }
        if (maxIteration != null) {
            setMaxIteration(maxIteration);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder compatible with the 0.1.12 Lombok builder surface.
     */
    public static final class Builder {
        private Integer reservedMaxChatRounds;
        private Integer maxIteration;

        private Builder() {
        }

        public Builder reservedMaxChatRounds(Integer reservedMaxChatRounds) {
            this.reservedMaxChatRounds = reservedMaxChatRounds;
            return this;
        }

        public Builder maxIteration(Integer maxIteration) {
            this.maxIteration = maxIteration;
            return this;
        }

        public ConstrainConfig build() {
            return new ConstrainConfig(reservedMaxChatRounds, maxIteration);
        }
    }
}
