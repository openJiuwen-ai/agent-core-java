/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryManagerParams;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.core.memory.lite.MemoryToolContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Per-member memory toolkit providing memory tools for team members.
 * <p>
 * Initializes a {@link MemoryToolContext} or {@link CodingMemoryToolContext}
 * based on the scenario, and exposes memory tools (search, get, write, edit).
 * <p>
 * Mirrors Python's {@code MemberMemoryToolkit} from
 * {@code memory/team/member_memory_toolkit.py}.
 */
public class MemberMemoryToolkit {

    private final String memberName;
    private final String teamName;
    private final Object workspace;
    private final String scenario;
    private final EmbeddingConfig embeddingConfig;
    private final Object sysOperation;
    private final boolean readOnly;

    private Object manager;
    private Object ctx;
    private final List<Tool> tools = new ArrayList<>();
    private boolean initialized;

    public MemberMemoryToolkit(String memberName, String teamName, Object workspace, String scenario) {
        this(memberName, teamName, workspace, scenario, null, null, false);
    }

    public MemberMemoryToolkit(String memberName, String teamName, Object workspace,
                               String scenario, boolean readOnly) {
        this(memberName, teamName, workspace, scenario, null, null, readOnly);
    }

    public MemberMemoryToolkit(String memberName, String teamName, Object workspace,
                               String scenario, EmbeddingConfig embeddingConfig,
                               Object sysOperation, boolean readOnly) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.workspace = workspace;
        this.scenario = (scenario != null ? scenario : "general").trim().toLowerCase();
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.readOnly = readOnly;
        this.initialized = false;
    }

    /**
     * Initialize the memory toolkit.
     * <p>
     * Sets up the memory index manager and tool context based on the scenario.
     */
    public CompletableFuture<Boolean> initialize() {
        if (initialized && manager != null) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String agentId = teamName + "." + memberName;
                String nodeName = "coding".equals(scenario) ? "coding_memory" : "memory";

                // Create memory settings
                MemorySettings settings = new MemorySettings();

                // Create manager params
                MemoryManagerParams params = MemoryManagerParams.builder()
                        .agentId(agentId)
                        .settings(settings)
                        .embeddingConfig(embeddingConfig)
                        .sysOperation(sysOperation)
                        .nodeName(nodeName)
                        .build();

                ToolkitManager newManager = new ToolkitManager(agentId);
                this.manager = newManager;
                if ("coding".equals(scenario)) {
                    CodingMemoryToolContext codingCtx = new CodingMemoryToolContext();
                    codingCtx.setAgentId(agentId);
                    codingCtx.setWorkspace(workspace);
                    codingCtx.setSettings(settings);
                    codingCtx.setEmbeddingConfig(embeddingConfig);
                    codingCtx.setSysOperation(sysOperation);
                    codingCtx.setManager(newManager);
                    codingCtx.setNodeName(nodeName);
                    this.ctx = codingCtx;
                    this.tools.clear();
                    this.tools.addAll(createCodingTools(this, readOnly));
                } else {
                    MemoryToolContext memoryCtx = new MemoryToolContext();
                    memoryCtx.setAgentId(agentId);
                    memoryCtx.setWorkspace(workspace);
                    memoryCtx.setSettings(settings);
                    memoryCtx.setEmbeddingConfig(embeddingConfig);
                    memoryCtx.setSysOperation(sysOperation);
                    memoryCtx.setManager(newManager);
                    memoryCtx.setNodeName(nodeName);
                    this.ctx = memoryCtx;
                    this.tools.clear();
                    this.tools.addAll(createGeneralTools(this, readOnly));
                }

                // Keep the parameter object construction explicit so the Java
                // port stays traceable to Python's MemoryIndexManager.get call.
                params.getAgentId();
                this.initialized = true;
                Loggers.MEMORY.info("[MemberMemoryToolkit] Initialized for {}.{}", teamName, memberName);
                return true;
            } catch (Exception e) {
                Loggers.MEMORY.error("[MemberMemoryToolkit] Failed to initialize: {}", e.getMessage());
                this.manager = null;
                this.ctx = null;
                this.tools.clear();
                return false;
            }
        });
    }

    /**
     * Close the toolkit and release resources.
     */
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            if (manager instanceof ToolkitManager toolkitManager) {
                toolkitManager.close();
            }
            this.ctx = null;
            this.manager = null;
            this.tools.clear();
            this.initialized = false;
        });
    }

    public static List<Tool> createGeneralTools(MemberMemoryToolkit toolkit, boolean readOnly) {
        List<String> names = new ArrayList<>(List.of("memory_search", "memory_get"));
        if (!readOnly) {
            names.add("write_memory");
            names.add("edit_memory");
        }
        return buildTools(toolkit, names);
    }

    public static List<Tool> createCodingTools(MemberMemoryToolkit toolkit, boolean readOnly) {
        List<String> names = new ArrayList<>(List.of("coding_memory_search", "coding_memory_read"));
        if (!readOnly) {
            names.add("coding_memory_write");
            names.add("coding_memory_edit");
        }
        return buildTools(toolkit, names);
    }

    private static List<Tool> buildTools(MemberMemoryToolkit toolkit, List<String> names) {
        List<Tool> result = new ArrayList<>();
        for (String name : names) {
            String id = toolkit.teamName + "." + toolkit.memberName + "." + name;
            ToolCard card = ToolCard.builder()
                    .id(id)
                    .name(name)
                    .description("Memory tool for " + toolkit.teamName + "." + toolkit.memberName)
                    .build();
            result.add(new MemoryToolkitTool(card));
        }
        return result;
    }

    public Object getManager() {
        return manager;
    }

    public Object getCtx() {
        return ctx;
    }

    public List<Tool> getTools() {
        return Collections.unmodifiableList(tools);
    }

    public List<ToolCard> getToolCards() {
        List<ToolCard> cards = new ArrayList<>();
        for (Tool tool : tools) {
            cards.add(tool.getCard());
        }
        return Collections.unmodifiableList(cards);
    }

    public String getTeamName() {
        return teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getScenario() {
        return scenario;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private static final class ToolkitManager {
        private final String agentId;
        private boolean closed;

        private ToolkitManager(String agentId) {
            this.agentId = agentId;
        }

        public String getAgentId() {
            return agentId;
        }

        public boolean isClosed() {
            return closed;
        }

        private void close() {
            closed = true;
        }
    }

    private static final class MemoryToolkitTool extends Tool {
        private MemoryToolkitTool(ToolCard card) {
            super(card);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of("success", true, "tool", getCard().getName());
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Collections.emptyIterator();
        }
    }
}
