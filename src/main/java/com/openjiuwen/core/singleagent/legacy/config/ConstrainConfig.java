/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legacy constraint configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstrainConfig {

    @Builder.Default
    private int reservedMaxChatRounds = 10;

    @Builder.Default
    private int maxIteration = 5;
}
