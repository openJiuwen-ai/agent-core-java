/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative tool reference.
 * Mirrors Python BuiltinToolSpec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuiltinToolSpec {

    private String type;
    @Builder.Default
    private Map<String, Object> params = new LinkedHashMap<>();
}
