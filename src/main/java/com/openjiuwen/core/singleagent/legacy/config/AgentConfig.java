  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.singleagent.legacy.config;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.WorkflowCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy agent configuration.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    private String id = "";

    private String version = "";

    private String description = "";

    private ControllerType controllerType = ControllerType.UNDEFINED;

    /**
     * Workflow list – accepts both {@link WorkflowSchema} and {@link WorkflowCard}
     * to mirror Python's {@code List[Union[WorkflowSchema, WorkflowCard]]}.
     */
    @Builder.Default
    private List<Object> workflows = new ArrayList<>();

    private ModelConfig model;

    @Builder.Default
    private List<String> tools = new ArrayList<>();
}
