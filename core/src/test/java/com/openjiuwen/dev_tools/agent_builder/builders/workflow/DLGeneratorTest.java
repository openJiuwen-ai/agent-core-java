/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_generator.py}.
 */
class DLGeneratorTest {

    @Test
    void generateBuildsSystemPromptWithPluginResourceAndCallsModel() {
        AtomicReference<List<BaseMessage>> captured = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            captured.set(messages);
            return CompletableFuture.completedFuture(new AssistantMessage("[{\"id\":\"start\"}]"));
        });
        DLGenerator generator = new DLGenerator(model);
        generator.getReflectPrompts().add(new SystemMessage("reflect"));

        String result = generator.generate("make flow", Map.of(
                "plugins", List.of(Map.of("tool_id", "search", "enabled", true))
        ));

        assertThat(result).isEqualTo("[{\"id\":\"start\"}]");
        assertThat(captured.get()).hasSize(3);
        assertThat(captured.get().get(0).getContentAsString()).contains("'tool_id': 'search'");
        assertThat(captured.get().get(0).getContentAsString()).contains("'enabled': True");
        assertThat(captured.get().get(1).getContentAsString()).isEqualTo("make flow");
        assertThat(captured.get().get(2).getContentAsString()).isEqualTo("reflect");
    }

    @Test
    void refineUsesEmptyResourceTextAndExistingFlowInputs() {
        AtomicReference<List<BaseMessage>> captured = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            captured.set(messages);
            return CompletableFuture.completedFuture(new AssistantMessage("refined"));
        });
        DLGenerator generator = new DLGenerator(model);

        String result = generator.refine("change", Map.of(), "old-dl", "A --> B");

        assertThat(result).isEqualTo("refined");
        assertThat(captured.get().get(0).getContentAsString()).contains(WorkflowPrompts.EMPTY_RESOURCE_CONTENT);
        assertThat(captured.get().get(1).getContentAsString()).contains("change");
        assertThat(captured.get().get(1).getContentAsString()).contains("old-dl");
        assertThat(captured.get().get(1).getContentAsString()).contains("A --> B");
    }
}
