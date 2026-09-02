/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LegacyReActAgentConfig extends AgentConfig {
    @Builder.Default
    private ControllerType controllerType = ControllerType.REACT_CONTROLLER;

    @Builder.Default
    private String promptTemplateName = "react_system_prompt";

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    /**
     * ConstrainConfig.builder.
     * 
     * @since 0.1.7
     */
    private ConstrainConfig constrain = ConstrainConfig.builder().build();

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<PluginSchema> plugins = new ArrayList<>();

    @Builder.Default
    private String memoryScopeId = "";

    @Builder.Default
    /**
     * AgentMemoryConfig.builder.
     * 
     * @since 0.1.7
     */
    private AgentMemoryConfig agentMemoryConfig = AgentMemoryConfig.builder().build();

    /**
     * getContextWindowLimit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getContextWindowLimit() {
        return constrain != null ? constrain.getReservedMaxChatRounds() : 10;
    }
}
