/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for ReAct agent workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react.react_config.ReActAgentCompConfig}.
 * <p>
 * Extends {@link ReActAgentConfig} to provide workflow-specific configuration.
 * May add workflow-specific configurations in the future.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReActAgentCompConfig extends ReActAgentConfig {

    // Currently inherits all fields from ReActAgentConfig.
    // Workflow-specific configurations may be added here in the future.
}