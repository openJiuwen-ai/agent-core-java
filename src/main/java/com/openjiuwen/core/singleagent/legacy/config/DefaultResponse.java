/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default response configuration for legacy workflow agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultResponse {

    @Builder.Default
    private String type = "text";

    private String text;
}
