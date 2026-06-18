/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import java.util.List;

/**
 * Package-level compatibility exports for the LLM agent builder package.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/__init__.py}.</p>
 */
public final class LlmAgentPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/dev_tools/agent_builder/builders/llm_agent/__init__.py";
    public static final List<String> ALL = List.of(
            "LlmAgentBuilder",
            "Clarifier",
            "Generator",
            "Transformer"
    );
    public static final Class<LlmAgentBuilder> LLM_AGENT_BUILDER = LlmAgentBuilder.class;
    public static final Class<Clarifier> CLARIFIER = Clarifier.class;
    public static final Class<Generator> GENERATOR = Generator.class;
    public static final Class<Transformer> TRANSFORMER = Transformer.class;

    private LlmAgentPackage() {
    }
}
