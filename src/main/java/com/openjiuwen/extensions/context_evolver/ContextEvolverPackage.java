/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.extensions.context_evolver.core.file_connector.FileConnectorPackage;
import com.openjiuwen.extensions.context_evolver.core.file_connector.JsonFileConnector;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemoryMetadata;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.schema.SummarizeResponse;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.service.TrajectoryGenerator;
import com.openjiuwen.extensions.context_evolver.tool.WikipediaTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Package facade for context-evolver top-level exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver} package facade in
 * {@code openjiuwen/extensions/context_evolver/__init__.py}.</p>
 */
public final class ContextEvolverPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
    );

    public static final Map<String, String> EXPORT_SOURCES = Map.ofEntries(
            Map.entry("TaskMemoryService", "openjiuwen.extensions.context_evolver.service.task_memory_service.TaskMemoryService"),
            Map.entry("AddMemoryRequest", "openjiuwen.extensions.context_evolver.service.task_memory_service.AddMemoryRequest"),
            Map.entry("ContextEvolvingReActAgent", "openjiuwen.extensions.context_evolver.context_evolving_react_agent.ContextEvolvingReActAgent"),
            Map.entry("create_memory_agent_config", "openjiuwen.extensions.context_evolver.context_evolving_react_agent.create_memory_agent_config"),
            Map.entry("MemoryAgentConfigInput", "openjiuwen.extensions.context_evolver.context_evolving_react_agent.MemoryAgentConfigInput"),
            Map.entry("SummarizeTrajectoriesInput", "openjiuwen.extensions.context_evolver.service.trajectory_generator.SummarizeTrajectoriesInput"),
            Map.entry("summarize_trajectories", "openjiuwen.extensions.context_evolver.service.trajectory_generator.summarize_trajectories"),
            Map.entry("wikipedia_tool", "openjiuwen.extensions.context_evolver.tool.wikipedia_tool.wikipedia_tool"),
            Map.entry("JSONFileConnector", "openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector"),
            Map.entry("safe_model_dump", "openjiuwen.extensions.context_evolver.core.file_connector.safe_model_dump"),
            Map.entry("MemoryVectorStore", "openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore"),
            Map.entry("ACEMemory", "openjiuwen.extensions.context_evolver.schema.io_schema.ACEMemory"),
            Map.entry("ACERetrievedMemory", "openjiuwen.extensions.context_evolver.schema.io_schema.ACERetrievedMemory"),
            Map.entry("ReasoningBankMemory", "openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankMemory"),
            Map.entry("ReasoningBankMemoryItem", "openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankMemoryItem"),
            Map.entry("ReasoningBankRetrievedMemory", "openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankRetrievedMemory"),
            Map.entry("ReMeMemory", "openjiuwen.extensions.context_evolver.schema.io_schema.ReMeMemory"),
            Map.entry("ReMeMemoryMetadata", "openjiuwen.extensions.context_evolver.schema.io_schema.ReMeMemoryMetadata"),
            Map.entry("ReMeRetrievedMemory", "openjiuwen.extensions.context_evolver.schema.io_schema.ReMeRetrievedMemory"),
            Map.entry("SummarizeResponse", "openjiuwen.extensions.context_evolver.schema.io_schema.SummarizeResponse"),
            Map.entry("RetrieveResponse", "openjiuwen.extensions.context_evolver.schema.io_schema.RetrieveResponse")
    );

    public static final Map<String, Class<?>> TYPE_EXPORTS = Map.ofEntries(
            Map.entry("TaskMemoryService", TaskMemoryService.class),
            Map.entry("AddMemoryRequest", AddMemoryRequest.class),
            Map.entry("ContextEvolvingReActAgent", ContextEvolvingReActAgent.class),
            Map.entry("MemoryAgentConfigInput", MemoryAgentConfigInput.class),
            Map.entry("SummarizeTrajectoriesInput", TrajectoryGenerator.SummarizeTrajectoriesInput.class),
            Map.entry("JSONFileConnector", JsonFileConnector.class),
            Map.entry("MemoryVectorStore", MemoryVectorStore.class),
            Map.entry("ACEMemory", ACEMemory.class),
            Map.entry("ACERetrievedMemory", ACERetrievedMemory.class),
            Map.entry("ReasoningBankMemory", ReasoningBankMemory.class),
            Map.entry("ReasoningBankMemoryItem", ReasoningBankMemoryItem.class),
            Map.entry("ReasoningBankRetrievedMemory", ReasoningBankRetrievedMemory.class),
            Map.entry("ReMeMemory", ReMeMemory.class),
            Map.entry("ReMeMemoryMetadata", ReMeMemoryMetadata.class),
            Map.entry("ReMeRetrievedMemory", ReMeRetrievedMemory.class),
            Map.entry("SummarizeResponse", SummarizeResponse.class),
            Map.entry("RetrieveResponse", RetrieveResponse.class)
    );

    public static final Map<String, Class<?>> METHOD_OWNERS = Map.of(
            "create_memory_agent_config", ContextEvolvingReActAgent.class,
            "summarize_trajectories", TrajectoryGenerator.class,
            "safe_model_dump", FileConnectorPackage.class
    );

    public static final Map<String, String> METHOD_NAMES = Map.of(
            "create_memory_agent_config", "create_memory_agent_config",
            "summarize_trajectories", "summarizeTrajectories",
            "safe_model_dump", "safeModelDump"
    );

    public static final Map<String, LocalFunction> LOCAL_FUNCTION_EXPORTS = Map.of(
            "wikipedia_tool", WikipediaTool.WIKIPEDIA_TOOL
    );

    private ContextEvolverPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is exported by Python {@code __all__}.
     *
     * @param symbolName symbol name
     * @return {@code true} when exported
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    public static Class<?> typeFor(String symbolName) {
        return TYPE_EXPORTS.get(symbolName);
    }

    public static Class<?> methodOwnerFor(String symbolName) {
        return METHOD_OWNERS.get(symbolName);
    }

    public static String methodNameFor(String symbolName) {
        return METHOD_NAMES.get(symbolName);
    }

    public static LocalFunction localFunctionFor(String symbolName) {
        return LOCAL_FUNCTION_EXPORTS.get(symbolName);
    }

    public static ReActAgentConfig createMemoryAgentConfig(MemoryAgentConfigInput params) {
        return ContextEvolvingReActAgent.createMemoryAgentConfig(params);
    }

    public static ReActAgentConfig create_memory_agent_config(MemoryAgentConfigInput params) {
        return ContextEvolvingReActAgent.create_memory_agent_config(params);
    }

    public static CompletableFuture<Map<String, Object>> summarizeTrajectories(
            TaskMemoryService memoryService,
            String userId,
            TrajectoryGenerator.SummarizeTrajectoriesInput input) {
        return TrajectoryGenerator.summarizeTrajectories(memoryService, userId, input);
    }

    public static Map<String, Object> safeModelDump(Object value) {
        return FileConnectorPackage.safeModelDump(value);
    }
}
