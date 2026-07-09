/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.memory.lite.MemoryIndexManager;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.CodingMemoryPromptSections;
import com.openjiuwen.harness.prompts.sections.MemoryPromptSections;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Team member memory manager.
 * 
 * @since 0.1.7
 */
public class TeamMemoryManager {
    /**
     * SECTION_NAME.
     * 
     * @since 0.1.7
     */
    public static final String SECTION_NAME = "team_memory";

    private static final int MAX_PERSONAL_MEMORY_BYTES = 10 * 1024;

    private final TeamMemoryManagerParams params;
    private Workspace workspace;
    private MemberMemoryToolkit toolkit;
    private SharedMemoryManager sharedManager;
    private String cachedPromptBlock;
    private PromptSection cachedBaseSection;

    /**
     * LinkedHashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> ownedToolNames = new LinkedHashSet<>();

    /**
     * LinkedHashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private DeepAgent deepAgentForCleanup;

    /**
     * TeamMemoryManager.
     * 
     * @param params params
     * @since 0.1.7
     */
    public TeamMemoryManager(TeamMemoryManagerParams params) {
        this.params = params;
        if (params.getReadOnlySourceWorkspace() != null && !params.getReadOnlySourceWorkspace().isBlank()) {
            this.workspace = Workspace.builder().rootPath(params.getReadOnlySourceWorkspace()).build();
        } else {
            this.workspace = params.getWorkspace();
        }
    }

    /**
     * initToolkit.
     * 
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public boolean initToolkit() throws IOException {
        if (toolkit != null && toolkit.isInitialized()) {
            return true;
        }
        if (workspace == null) {
            return false;
        }
        toolkit = new MemberMemoryToolkit(params.getMemberName(), params.getTeamName(), workspace,
                params.getScenario() != null ? params.getScenario().getValue() : "general", params.getEmbeddingConfig(),
                params.getSysOperation() instanceof SysOperation sysOperation ? sysOperation : null,
                params.getReadOnlySourceWorkspace() != null && !params.getReadOnlySourceWorkspace().isBlank());
        boolean isInitialized = toolkit.initialize();
        if (isInitialized && params.getTeamMemoryDir() != null && !params.getTeamMemoryDir().isBlank()) {
            sharedManager = new SharedMemoryManager(params.getTeamMemoryDir(), params.getSysOperation());
            sharedManager.ensureDir();
        }
        return isInitialized;
    }

    /**
     * registerTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<LocalFunction> registerTools() {
        return toolkit != null ? toolkit.getTools() : List.of();
    }

    /**
     * registerTools.
     * 
     * @param deepAgent deepAgent
     * @since 0.1.7
     */
    public void registerTools(DeepAgent deepAgent) {
        if (!ownedToolNames.isEmpty() || toolkit == null || deepAgent == null) {
            return;
        }
        int railsRemoved = stripMemoryRailsFromDeepAgent(deepAgent);
        if (railsRemoved > 0) {
            Loggers.MEMORY.info("[TeamMemoryManager] Stripped {} memory rail(s) for {}.{}", railsRemoved,
                    params.getTeamName(), params.getMemberName());
        }
        deepAgentForCleanup = deepAgent;
        for (LocalFunction tool : toolkit.getTools()) {
            if (tool.getCard() == null || tool.getCard().getId() == null || tool.getCard().getId().isBlank()) {
                continue;
            }
            String toolId = tool.getCard().getId();
            try {
                Object existing = Runner.resourceMgr().getTool(toolId);
                if (!(existing instanceof Tool)) {
                    Result<?> added = Runner.resourceMgr().addTool(tool, tool.getCard().getId());
                    if (added.isOk()) {
                        ownedToolIds.add(toolId);
                    }
                }
                deepAgent.getAgent().getAbilityManager().add(tool.getCard());
                ownedToolNames.add(tool.getCard().getName());
            } catch (IllegalStateException | IllegalArgumentException e) {
                Loggers.MEMORY.warning("[TeamMemoryManager] Failed to register tool {}: {}", toolId, e.getMessage());
            }
        }
    }

    /**
     * loadAndInject.
     * 
     * @param query query
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public String loadAndInject(String query) throws IOException {
        PromptSection base = getOrBuildBaseSection();
        String language = languageKey();
        StringBuilder builder = new StringBuilder(base.render(language));
        String personalMemory = fetchPersonalMemoryForPrompt(query);
        if (personalMemory != null && !personalMemory.isBlank()) {
            builder.append(isCn() ? "\n\n## 你的相关记忆\n\n" : "\n\n## Your relevant memories\n\n").append(personalMemory);
        }
        if (sharedManager != null) {
            String teamSummary = sharedManager.readTeamSummary();
            if (!teamSummary.isBlank()) {
                builder.append(isCn() ? "\n\n## 团队共享记忆\n\n" : "\n\n## Team shared memory\n\n").append(teamSummary);
            }
        }
        cachedPromptBlock = builder.toString().trim();
        return cachedPromptBlock;
    }

    /**
     * loadAndInject.
     * 
     * @param deepAgent deepAgent
     * @param query query
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void loadAndInject(DeepAgent deepAgent, String query) throws IOException {
        String promptBlock = loadAndInject(query);
        if (deepAgent == null || promptBlock.isBlank()) {
            return;
        }
        var builder = deepAgent.getAgent().getSystemPromptBuilder();
        builder.removeSection(SECTION_NAME);
        builder.addSection(new PromptSection(SECTION_NAME, Map.of(languageKey(), promptBlock),
                getOrBuildBaseSection().getPriority()));
    }

    /**
     * extractAfterRound.
     * 
     * @param entry entry
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void extractAfterRound(String entry) throws IOException {
        if (sharedManager == null || !params.isAutoExtractEnabled()) {
            return;
        }
        if (entry != null && !entry.isBlank()) {
            sharedManager.appendEntry(entry);
        }
    }

    /**
     * extractAfterRound.
     * 
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void extractAfterRound() throws IOException {
        if (!params.isAutoExtractEnabled() || params.getLifecycle() != TeamLifecycle.PERSISTENT
                || params.getRole() != TeamRole.LEADER || params.getTeamMemoryDir() == null
                || params.getTeamMemoryDir().isBlank() || params.getDb() == null) {
            return;
        }
        TeamMemoryExtractor.extractTeamMemories(params.getTeamName(), params.getDb(), params.getTaskManager(),
                params.getTeamMemoryDir(),
                params.getSysOperation() instanceof SysOperation sysOperation ? sysOperation : null,
                params.getExtractionModel(), params.getTimezoneOffsetHours());
    }

    /**
     * close.
     * 
     * @since 0.1.7
     */
    public void close() {
        if (deepAgentForCleanup != null) {
            deepAgentForCleanup.getAgent().getSystemPromptBuilder().removeSection(SECTION_NAME);
            for (String toolName : ownedToolNames) {
                deepAgentForCleanup.getAgent().getAbilityManager().remove(toolName);
            }
        }
        for (String toolId : ownedToolIds) {
            try {
                Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
            } catch (IllegalStateException | IllegalArgumentException e) {
                Loggers.MEMORY.warning("[TeamMemoryManager] remove_tool({}) failed: {}", toolId, e.getMessage());
            }
        }
        deepAgentForCleanup = null;
        ownedToolNames.clear();
        ownedToolIds.clear();
        if (toolkit != null) {
            toolkit.close();
            toolkit = null;
        }
        cachedBaseSection = null;
        cachedPromptBlock = null;
    }

    /**
     * getToolkit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MemberMemoryToolkit getToolkit() {
        return toolkit;
    }

    /**
     * getSharedManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SharedMemoryManager getSharedManager() {
        return sharedManager;
    }

    /**
     * getCachedPromptBlock.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCachedPromptBlock() {
        return cachedPromptBlock;
    }

    /**
     * getOwnedToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getOwnedToolNames() {
        return Set.copyOf(ownedToolNames);
    }

    /**
     * getOwnedToolIds.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getOwnedToolIds() {
        return Set.copyOf(ownedToolIds);
    }

    /**
     * stripMemoryRailsFromDeepAgent.
     * 
     * @param deepAgent deepAgent
     * @return the result
     * @since 0.1.7
     */
    private int stripMemoryRailsFromDeepAgent(DeepAgent deepAgent) {
        int removed = 0;
        for (Object rail : new ArrayList<>(deepAgent.getRegisteredRails())) {
            if (rail instanceof MemoryRail || rail instanceof CodingMemoryRail) {
                try {
                    if (rail instanceof com.openjiuwen.core.singleagent.rail.AgentRail agentRail) {
                        deepAgent.getAgent().unregisterRail(agentRail);
                    }
                    if (rail instanceof com.openjiuwen.harness.rails.DeepAgentRail deepAgentRail) {
                        deepAgentRail.uninit(deepAgent);
                    }
                    deepAgent.getRegisteredRails().remove(rail);
                    removed++;
                } catch (IllegalStateException | IllegalArgumentException e) {
                    Loggers.MEMORY.warning("[TeamMemoryManager] Failed to strip memory rail: {}", e.getMessage());
                }
            }
        }
        return removed;
    }

    /**
     * fetchPersonalMemoryForPrompt.
     * 
     * @param query query
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private String fetchPersonalMemoryForPrompt(String query) throws IOException {
        if (toolkit == null || toolkit.getMemoryDir() == null) {
            return null;
        }
        MemoryIndexManager indexManager = toolkit.getManager();
        if (indexManager != null && query != null && !query.isBlank()) {
            List<String> parts = new ArrayList<>();
            int totalBytes = 0;
            for (var result : indexManager.search(query, Map.of("max_results", 5))) {
                String path = String.valueOf(result.get("path"));
                if (path.endsWith("MEMORY.md")) {
                    continue;
                }
                String content = String.valueOf(indexManager.readFile(path, null, null).getOrDefault("text", ""));
                if (content.isBlank()) {
                    continue;
                }
                int contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
                if (totalBytes + contentBytes > MAX_PERSONAL_MEMORY_BYTES) {
                    int remaining = MAX_PERSONAL_MEMORY_BYTES - totalBytes;
                    if (remaining > 200) {
                        parts.add("### " + path + "\n\n" + content.substring(0, Math.min(content.length(), remaining))
                                + "\n... (truncated)");
                    }
                    break;
                }
                parts.add("### " + path + "\n\n" + content);
                totalBytes += contentBytes;
            }
            if (!parts.isEmpty()) {
                return String.join("\n\n---\n\n", parts);
            }
        }
        Path indexPath = toolkit.getMemoryDir().resolve("MEMORY.md");
        if (!Files.exists(indexPath)) {
            return null;
        }
        String content = Files.readString(indexPath, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return null;
        }
        return content.strip();
    }

    /**
     * getOrBuildBaseSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    private PromptSection getOrBuildBaseSection() {
        if (cachedBaseSection != null) {
            return cachedBaseSection;
        }
        String language = languageKey();
        boolean isReadOnly =
            params.getReadOnlySourceWorkspace() != null && !params.getReadOnlySourceWorkspace().isBlank();
        String scenario = params.getScenario() != null ? params.getScenario().getValue() : "general";
        if ("coding".equals(scenario)) {
            String memoryDir = workspace != null ? workspace.getNodePath("coding_memory").toString() : "coding_memory/";
            cachedBaseSection = CodingMemoryPromptSections.buildCodingMemorySection(language, isReadOnly, memoryDir);
        } else {
            cachedBaseSection = MemoryPromptSections.buildMemorySection(language, isReadOnly,
                    params.getPromptMode() == PromptMode.PROACTIVE);
        }
        return cachedBaseSection;
    }

    /**
     * languageKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String languageKey() {
        return params.getLanguage() != null ? params.getLanguage().getValue() : PromptSection.DEFAULT_LANGUAGE;
    }

    /**
     * isCn.
     * 
     * @return the result
     * @since 0.1.7
     */
    private boolean isCn() {
        return "cn".equalsIgnoreCase(languageKey());
    }

    /**
     * setExtractionModel.
     * 
     * @param extractionModel extractionModel
     * @since 0.1.7
     */
    public void setExtractionModel(Model extractionModel) {
        params.setExtractionModel(extractionModel);
    }

    /**
     * getExtractionModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Model getExtractionModel() {
        return params.getExtractionModel();
    }
}
