/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default response configuration for agents.
 * <p>
 * Used when intent detection returns no matching workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultResponse {

    @Builder.Default
    private String text = "";
}
