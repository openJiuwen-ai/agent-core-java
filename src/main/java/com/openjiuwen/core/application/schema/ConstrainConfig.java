/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Constraint configuration for application-layer agents.
 *
 * <p>Mirrors Python's {@code ConstrainConfig} for legacy compatibility.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstrainConfig {

    @Builder.Default
    @JsonProperty("reserved_max_chat_rounds")
    @JsonAlias("reservedMaxChatRounds")
    private int reservedMaxChatRounds = 10;

    @Builder.Default
    @JsonProperty("max_iteration")
    @JsonAlias("maxIteration")
    private int maxIteration = 5;
}
