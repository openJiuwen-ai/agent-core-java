/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.agent_builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.dev_tools.agent_builder.AgentBuilder;

import java.util.Map;

/**
 * Example for the Java AgentBuilder entry point.
 */
public final class AgentBuilderExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentBuilderExample() {
    }

    public static void main(String[] args) throws Exception {
        AgentBuilder builder = new AgentBuilder(Map.of(
                "model_provider", "openai",
                "model_name", "gpt-4"
        ));

        Map<String, Object> result = builder.buildLlmAgent(
                "Build an agent assistant that classifies support tickets and summarizes customer intent.",
                "agent_builder_demo"
        );

        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
