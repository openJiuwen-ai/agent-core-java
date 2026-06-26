/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.file_connector.FileConnectorPackage;
import com.openjiuwen.extensions.context_evolver.core.file_connector.JsonFileConnector;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.schema.SummarizeResponse;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.service.TrajectoryGenerator;
import com.openjiuwen.extensions.context_evolver.tool.WikipediaTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for context-evolver top-level package exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver} package facade in
 * {@code openjiuwen/extensions/context_evolver/__init__.py}.</p>
 */
class ContextEvolverPackageTest {
    @Test
    void exposesPythonAllInOrder() {
        assertThat(ContextEvolverPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/extensions/context_evolver/__init__.py");
        assertThat(ContextEvolverPackage.all()).containsExactlyElementsOf(List.of(
                "TaskMemoryService",
                "AddMemoryRequest",
                "ContextEvolvingReActAgent",
                "create_memory_agent_config",
                "MemoryAgentConfigInput",
                "SummarizeTrajectoriesInput",
                "summarize_trajectories",
                "wikipedia_tool",
                "JSONFileConnector",
                "safe_model_dump",
                "MemoryVectorStore",
                "ACEMemory",
                "ACERetrievedMemory",
                "ReasoningBankMemory",
                "ReasoningBankMemoryItem",
                "ReasoningBankRetrievedMemory",
                "ReMeMemory",
                "ReMeMemoryMetadata",
                "ReMeRetrievedMemory",
                "SummarizeResponse",
                "RetrieveResponse"
        ));
        assertThat(ContextEvolverPackage.all()).isSameAs(ContextEvolverPackage.EXPORTED_SYMBOLS);
    }

    @Test
    void resolvesClassExports() {
        assertThat(ContextEvolverPackage.typeFor("TaskMemoryService")).isEqualTo(TaskMemoryService.class);
        assertThat(ContextEvolverPackage.typeFor("AddMemoryRequest")).isEqualTo(AddMemoryRequest.class);
        assertThat(ContextEvolverPackage.typeFor("ContextEvolvingReActAgent"))
                .isEqualTo(ContextEvolvingReActAgent.class);
        assertThat(ContextEvolverPackage.typeFor("MemoryAgentConfigInput")).isEqualTo(MemoryAgentConfigInput.class);
        assertThat(ContextEvolverPackage.typeFor("SummarizeTrajectoriesInput"))
                .isEqualTo(TrajectoryGenerator.SummarizeTrajectoriesInput.class);
        assertThat(ContextEvolverPackage.typeFor("JSONFileConnector")).isEqualTo(JsonFileConnector.class);
        assertThat(ContextEvolverPackage.typeFor("MemoryVectorStore")).isEqualTo(MemoryVectorStore.class);
        assertThat(ContextEvolverPackage.typeFor("ACEMemory")).isEqualTo(ACEMemory.class);
        assertThat(ContextEvolverPackage.typeFor("SummarizeResponse")).isEqualTo(SummarizeResponse.class);
        assertThat(ContextEvolverPackage.typeFor("RetrieveResponse")).isEqualTo(RetrieveResponse.class);
    }

    @Test
    void resolvesFunctionExports() throws NoSuchMethodException {
        assertThat(ContextEvolverPackage.exports("create_memory_agent_config")).isTrue();
        assertThat(ContextEvolverPackage.methodOwnerFor("create_memory_agent_config"))
                .isEqualTo(ContextEvolvingReActAgent.class);
        assertThat(ContextEvolverPackage.methodNameFor("create_memory_agent_config"))
                .isEqualTo("create_memory_agent_config");
        assertThat(ContextEvolvingReActAgent.class.getMethod(
                "create_memory_agent_config", MemoryAgentConfigInput.class)).isNotNull();

        assertThat(ContextEvolverPackage.methodOwnerFor("summarize_trajectories"))
                .isEqualTo(TrajectoryGenerator.class);
        assertThat(ContextEvolverPackage.methodNameFor("summarize_trajectories"))
                .isEqualTo("summarizeTrajectories");
        assertThat(ContextEvolverPackage.methodOwnerFor("safe_model_dump"))
                .isEqualTo(FileConnectorPackage.class);
        assertThat(ContextEvolverPackage.methodNameFor("safe_model_dump")).isEqualTo("safeModelDump");
        assertThat(ContextEvolverPackage.localFunctionFor("wikipedia_tool"))
                .isSameAs(WikipediaTool.WIKIPEDIA_TOOL);
    }

    @Test
    void resolvesPythonSourcesAndMissingSymbols() {
        assertThat(ContextEvolverPackage.sourceFor("TaskMemoryService"))
                .isEqualTo("openjiuwen.extensions.context_evolver.service.task_memory_service.TaskMemoryService");
        assertThat(ContextEvolverPackage.sourceFor("safe_model_dump"))
                .isEqualTo("openjiuwen.extensions.context_evolver.core.file_connector.safe_model_dump");
        assertThat(ContextEvolverPackage.exports("missing")).isFalse();
        assertThat(ContextEvolverPackage.typeFor("missing")).isNull();
        assertThat(ContextEvolverPackage.methodOwnerFor("missing")).isNull();
        assertThat(ContextEvolverPackage.localFunctionFor("missing")).isNull();
    }
}
