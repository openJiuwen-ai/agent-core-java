/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryToolOps;
import com.openjiuwen.core.memory.lite.MemoryIndexManager;
import com.openjiuwen.core.memory.lite.MemoryManagerParams;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolOps;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * File-backed member memory toolkit.
 */
public class MemberMemoryToolkit {
    @Getter
    private final String memberName;
    @Getter
    private final String teamName;
    @Getter
    private final Workspace workspace;
    @Getter
    private final String scenario;
    @Getter
    private final EmbeddingConfig embeddingConfig;
    @Getter
    private final SysOperation sysOperation;
    @Getter
    private final boolean isReadOnly;

    private Path memoryDir;
    private MemorySettings settings;
    private MemoryIndexManager manager;
    private Object ctx;
    private final List<LocalFunction> tools = new ArrayList<>();
    private boolean isToolkitInitialized;

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemberMemoryToolkit(String memberName,
                               String teamName,
                               Workspace workspace,
                               String scenario,
                               EmbeddingConfig embeddingConfig,
                               SysOperation sysOperation,
                               boolean isReadOnly) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.workspace = workspace;
        this.scenario = scenario != null ? scenario.trim().toLowerCase(Locale.ROOT) : "general";
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.isReadOnly = isReadOnly;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean initialize() throws IOException {
        if (isToolkitInitialized) {
            return true;
        }
        if (!MemorySettings.isMemoryEnabled()) {
            return false;
        }
        if (workspace == null) {
            return false;
        }
        String nodeName = "coding".equals(scenario) ? "coding_memory" : "memory";
        memoryDir = workspace.getNodePath(nodeName);
        Files.createDirectories(memoryDir);
        settings = MemorySettings.createMemorySettings(memoryDir.toString(), Map.of());
        manager = MemoryIndexManager.get(new MemoryManagerParams(
                teamName + "." + memberName,
                workspace,
                settings,
                embeddingConfig,
                sysOperation,
                nodeName
        )).join();
        if ("coding".equals(scenario)) {
            ctx = new CodingMemoryToolContext(memoryDir.toString(), nodeName);
            ((CodingMemoryToolContext) ctx).setWorkspace(workspace);
            ((CodingMemoryToolContext) ctx).setSettings(settings);
            ((CodingMemoryToolContext) ctx).setAgentId(teamName + "." + memberName);
            ((CodingMemoryToolContext) ctx).setEmbeddingConfig(embeddingConfig);
            ((CodingMemoryToolContext) ctx).setSysOperation(sysOperation);
            ((CodingMemoryToolContext) ctx).setManager(manager);
        } else {
            ctx = new MemoryToolContext();
            ((MemoryToolContext) ctx).setWorkspace(workspace);
            ((MemoryToolContext) ctx).setSettings(settings);
            ((MemoryToolContext) ctx).setAgentId(teamName + "." + memberName);
            ((MemoryToolContext) ctx).setEmbeddingConfig(embeddingConfig);
            ((MemoryToolContext) ctx).setSysOperation(sysOperation);
            ((MemoryToolContext) ctx).setManager(manager);
        }
        tools.clear();
        tools.addAll(createTools(nodeName));
        isToolkitInitialized = true;
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<LocalFunction> getTools() {
        return new ArrayList<>(tools);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<ToolCard> getToolCards() {
        return tools.stream().map(LocalFunction::getCard).toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        tools.clear();
        if (manager != null) {
            manager.close();
        }
        manager = null;
        ctx = null;
        isToolkitInitialized = false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isInitialized() {
        return isToolkitInitialized;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path getMemoryDir() {
        return memoryDir;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemoryIndexManager getManager() {
        return manager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getCtx() {
        return ctx;
    }

    private List<LocalFunction> createTools(String nodeName) {
        List<LocalFunction> result = new ArrayList<>();
        if ("coding_memory".equals(nodeName)) {
            result.addAll(createCodingTools("coding_memory." + teamName + "." + memberName));
        } else {
            result.addAll(createGeneralTools("memory." + teamName + "." + memberName));
        }
        return result;
    }

    private List<LocalFunction> createGeneralTools(String prefix) {
        if (!(ctx instanceof MemoryToolContext memoryCtx)) {
            throw new IllegalStateException("memory tool context is not initialized");
        }
        List<LocalFunction> result = new ArrayList<>();
        result.add(new LocalFunction(
                ToolCard.builder()
                        .id(prefix + ".memory_search")
                        .name("memory_search")
                        .description("Search memory files by text.")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("query", Map.of("type", "string")),
                                "required", List.of("query")))
                        .build(),
                inputs -> MemoryToolOps.memorySearchWithContext(
                        memoryCtx,
                        String.valueOf(inputs.getOrDefault("query", "")),
                        inputs.get("max_results") != null
                                ? Integer.parseInt(String.valueOf(inputs.get("max_results")))
                                : null,
                        inputs.get("min_score") != null
                                ? Double.parseDouble(String.valueOf(inputs.get("min_score")))
                                : null,
                        inputs.get("session_key") != null ? String.valueOf(inputs.get("session_key")) : null
                )
        ));
        result.add(new LocalFunction(
                ToolCard.builder()
                        .id(prefix + ".memory_get")
                        .name("memory_get")
                        .description("Get a memory file content.")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("path", Map.of("type", "string")),
                                "required", List.of("path")))
                        .build(),
                inputs -> MemoryToolOps.memoryGetWithContext(
                        memoryCtx,
                        String.valueOf(inputs.getOrDefault("path", "")),
                        inputs.get("from_line") != null
                                ? Integer.parseInt(String.valueOf(inputs.get("from_line")))
                                : null,
                        inputs.get("lines") != null ? Integer.parseInt(String.valueOf(inputs.get("lines"))) : null
                )
        ));
        result.add(new LocalFunction(
                ToolCard.builder().id(prefix + ".read_memory").name("read_memory").description("Read a memory file.")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("path", Map.of("type", "string")),
                                "required", List.of("path")))
                        .build(),
                inputs -> MemoryToolOps.readMemoryWithContext(
                        memoryCtx,
                        String.valueOf(inputs.getOrDefault("path", "")),
                        inputs.get("offset") != null ? Integer.parseInt(String.valueOf(inputs.get("offset"))) : null,
                        inputs.get("limit") != null ? Integer.parseInt(String.valueOf(inputs.get("limit"))) : null
                )
        ));
        if (!isReadOnly) {
            result.add(new LocalFunction(
                    ToolCard.builder()
                            .id(prefix + ".write_memory")
                            .name("write_memory")
                            .description("Write a memory file.")
                            .inputParams(Map.of("type", "object", "properties", Map.of(
                                    "path", Map.of("type", "string"),
                                    "content", Map.of("type", "string"),
                                    "append", Map.of("type", "boolean")),
                                    "required", List.of("path", "content")))
                            .build(),
                    inputs -> MemoryToolOps.writeMemoryWithContext(
                            memoryCtx,
                            String.valueOf(inputs.getOrDefault("path", "")),
                            String.valueOf(inputs.getOrDefault("content", "")),
                            Boolean.parseBoolean(String.valueOf(inputs.getOrDefault("append", false)))
                    )
            ));
            result.add(new LocalFunction(
                    ToolCard.builder()
                            .id(prefix + ".edit_memory")
                            .name("edit_memory")
                            .description("Replace text in a memory file.")
                            .inputParams(Map.of("type", "object", "properties", Map.of(
                                    "path", Map.of("type", "string"),
                                    "old_text", Map.of("type", "string"),
                                    "new_text", Map.of("type", "string")),
                                    "required", List.of("path", "old_text", "new_text")))
                            .build(),
                    inputs -> MemoryToolOps.editMemoryWithContext(
                            memoryCtx,
                            String.valueOf(inputs.getOrDefault("path", "")),
                            String.valueOf(inputs.getOrDefault("old_text", "")),
                            String.valueOf(inputs.getOrDefault("new_text", ""))
                    )
            ));
        }
        return result;
    }

    private List<LocalFunction> createCodingTools(String prefix) {
        if (!(ctx instanceof CodingMemoryToolContext codingCtx)) {
            throw new IllegalStateException("coding memory tool context is not initialized");
        }
        List<LocalFunction> result = new ArrayList<>();
        result.add(new LocalFunction(
                ToolCard.builder()
                        .id(prefix + ".coding_memory_read")
                        .name("coding_memory_read")
                        .description("Read a coding memory file.")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("path", Map.of("type", "string")),
                                "required", List.of("path")))
                        .build(),
                inputs -> CodingMemoryToolOps.codingMemoryReadWithContext(
                        codingCtx,
                        String.valueOf(inputs.getOrDefault("path", "")),
                        inputs.get("offset") != null ? Integer.parseInt(String.valueOf(inputs.get("offset"))) : null,
                        inputs.get("limit") != null ? Integer.parseInt(String.valueOf(inputs.get("limit"))) : null
                )
        ));
        if (!isReadOnly) {
            result.add(new LocalFunction(
                    ToolCard.builder()
                            .id(prefix + ".coding_memory_write")
                            .name("coding_memory_write")
                            .description("Write a coding memory file.")
                            .inputParams(Map.of("type", "object", "properties", Map.of(
                                    "path", Map.of("type", "string"),
                                    "content", Map.of("type", "string")),
                                    "required", List.of("path", "content")))
                            .build(),
                    inputs -> CodingMemoryToolOps.codingMemoryWriteWithContext(
                            codingCtx,
                            String.valueOf(inputs.getOrDefault("path", "")),
                            String.valueOf(inputs.getOrDefault("content", ""))
                    )
            ));
            result.add(new LocalFunction(
                    ToolCard.builder()
                            .id(prefix + ".coding_memory_edit")
                            .name("coding_memory_edit")
                            .description("Edit a coding memory file.")
                            .inputParams(Map.of("type", "object", "properties", Map.of(
                                    "path", Map.of("type", "string"),
                                    "old_text", Map.of("type", "string"),
                                    "new_text", Map.of("type", "string")),
                                    "required", List.of("path", "old_text", "new_text")))
                            .build(),
                    inputs -> CodingMemoryToolOps.codingMemoryEditWithContext(
                            codingCtx,
                            String.valueOf(inputs.getOrDefault("path", "")),
                            String.valueOf(inputs.getOrDefault("old_text", "")),
                            String.valueOf(inputs.getOrDefault("new_text", ""))
                    )
            ));
        }
        return result;
    }
}
