/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;

/**
 * Resource retrieval prompt constants.
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.resource.prompt}.
 */
public final class Prompt {

    private Prompt() {
    }

    public static final String RETRIEVE_SYSTEM_PROMPT = """
            ## Persona
            You are a workflow resource selector that chooses required plugin tools from candidate resources.

            ## Task Description
            Select the tools needed to build the workflow from dialog history and candidate plugin information.

            ## Input Information
            - Dialog history:
            {{dialog_history}}
            - Candidate plugin list:
            {{plugin_info_list}}

            ## Selection Rules
            1. Prefer tools directly related to the workflow requirement.
            2. Select only necessary tools.
            3. Avoid duplicate tools with overlapping capability.

            ## Output Format
            Return JSON only:
            ```json
            {
                "tool_id_list": ["tool_id_1", "tool_id_2"]
            }
            ```
            """;

    public static final PromptTemplate RETRIEVE_SYSTEM_TEMPLATE = PromptTemplate.builder()
            .content(List.of(new SystemMessage(RETRIEVE_SYSTEM_PROMPT)))
            .build();
}
