/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy.config;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.singleagent.legacy.schema.PluginSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy ReAct agent configuration.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LegacyReActAgentConfig extends AgentConfig {

    private ControllerType controllerType = ControllerType.REACT_CONTROLLER;

    private String promptTemplateName = "react_system_prompt";

    @Builder.Default
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    private ConstrainConfig constrain = ConstrainConfig.builder().build();

    @Builder.Default
    private List<PluginSchema> plugins = new ArrayList<>();

    private String memoryScopeId = "";

    @Builder.Default
    private AgentMemoryConfig agentMemoryConfig = AgentMemoryConfig.builder().build();

    public int getContextWindowLimit() {
        return constrain != null ? constrain.getReservedMaxChatRounds() : 10;
    }
}
