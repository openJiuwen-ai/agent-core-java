/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("type")
    @JsonAlias("type")
    private String type = "text";

    private String text;
}
