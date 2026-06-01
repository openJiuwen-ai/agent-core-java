/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.CodingMemorySection;
import com.openjiuwen.harness.prompts.sections.MemorySection;
import com.openjiuwen.harness.workspace.Workspace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Team-scoped orchestration for per-member memory tools and prompt injection.
 * <p>
 * Manages the lifecycle of team memory: initializes the member toolkit,
 * registers memory tools with the agent, injects context into system prompts,
 * and handles extraction after coordination rounds.
 * <p>
 * Resource contract:
 * <ul>
 *   <li>{@link #registerTools(Object)} registers tools with the agent.</li>
 *   <li>{@link #close()} removes tools and releases resources.</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code TeamMemoryManager} from
 * {@code memory/team/manager.py}.
 */
public class TeamMemoryManager {

    public static final String SECTION_NAME = "team_memory";
    private static final int MAX_PERSONAL_MEMORY_BYTES = 10 * 1024;

    private final String memberName;
    private final String teamName;
    private final String role;
    private final String lifecycle;
    private final String scenario;
    private final EmbeddingConfig embeddingConfig;
    private final String language;
    private final String promptMode;
    private final boolean enableAutoExtract;
    private final Object sysOperation;
    private final Object workspace;
    private final String teamMemoryDir;
    private final String readOnlySourceWorkspace;
    private final Object db;
    private final Object taskManager;
    private final Object extractionModel;

    private MemberMemoryToolkit toolkit;
    private SharedMemoryManager sharedManager;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private Object deepAgentForCleanup;
    private PromptSection cachedBaseSection;
    private int extractInvocationCount;

    /**
     * Create a new TeamMemoryManager from parameters.
     */
    public TeamMemoryManager(
            String memberName, String teamName, String role,
            String lifecycle, String scenario,
            EmbeddingConfig embeddingConfig, String language, String promptMode,
            boolean enableAutoExtract, Object sysOperation,
            Object workspace, String teamMemoryDir) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.role = role;
        this.lifecycle = lifecycle;
        this.scenario = scenario;
        this.embeddingConfig = embeddingConfig;
        this.language = language;
        this.promptMode = promptMode;
        this.enableAutoExtract = enableAutoExtract;
        this.sysOperation = sysOperation;
        this.workspace = workspace;
        this.teamMemoryDir = teamMemoryDir;
        this.readOnlySourceWorkspace = null;
        this.db = null;
        this.taskManager = null;
        this.extractionModel = null;
    }

    /**
     * Create a manager from the Python dataclass-style parameter object.
     */
    public TeamMemoryManager(TeamMemoryManagerParams params) {
        this.memberName = params.getMemberName();
        this.teamName = params.getTeamName();
        this.role = params.getRole();
        this.lifecycle = params.getLifecycle();
        this.scenario = params.getScenario();
        this.embeddingConfig = params.getEmbeddingConfig() instanceof EmbeddingConfig cfg ? cfg : null;
        this.language = params.getLanguage();
        this.promptMode = params.getPromptMode();
        this.enableAutoExtract = params.isEnableAutoExtract();
        this.sysOperation = params.getSysOperation();
        this.teamMemoryDir = params.getTeamMemoryDir();
        this.readOnlySourceWorkspace = params.getReadOnlySourceWorkspace();
        this.workspace = params.getWorkspace() != null
                ? params.getWorkspace()
                : createReadOnlyWorkspace(params.getReadOnlySourceWorkspace(), params.getLanguage());
        this.db = params.getDb();
        this.taskManager = params.getTaskManager();
        this.extractionModel = params.getExtractionModel();
    }

    /**
     * Initialize the member memory toolkit.
     */
    public CompletableFuture<Boolean> initToolkit() {
        if (toolkit != null) {
            return CompletableFuture.completedFuture(true);
        }

        Object effectiveWorkspace = effectiveWorkspace();
        if (effectiveWorkspace == null) {
            Loggers.MEMORY.warn("[TeamMemoryManager] No workspace available, skipping init");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                toolkit = new MemberMemoryToolkit(
                        memberName, teamName, effectiveWorkspace,
                        scenario, embeddingConfig, sysOperation,
                        readOnlySourceWorkspace != null);

                boolean success = toolkit.initialize().join();
                if (!success) {
                    Loggers.MEMORY.warn("[TeamMemoryManager] Toolkit init failed, memory tools unavailable");
                    return false;
                }

                if (teamMemoryDir != null && !teamMemoryDir.isEmpty()) {
                    sharedManager = new SharedMemoryManager(teamMemoryDir, sysOperation);
                    sharedManager.ensureDir().join();
                }

                Loggers.MEMORY.info("[TeamMemoryManager] Initialized for {}.{} lifecycle={} scenario={}",
                        teamName, memberName, lifecycle, scenario);
                return true;
            } catch (Exception e) {
                Loggers.MEMORY.error("[TeamMemoryManager] Init failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Register memory tools with the agent.
     * <p>
     * Idempotent: skips if already registered.
     */
    public void registerTools(Object deepAgent) {
        if (!ownedToolNames.isEmpty()) {
            return;
        }
        if (toolkit == null) {
            return;
        }
        this.deepAgentForCleanup = deepAgent;
        Object abilityManager = resolveAbilityManager(deepAgent);
        for (Tool tool : toolkit.getTools()) {
            ToolCard card = tool.getCard();
            ownedToolNames.add(card.getName());
            ownedToolIds.add(card.getId());
            invokeAbilityAdd(abilityManager, card);
        }
        Loggers.MEMORY.info("[TeamMemoryManager] Tool registration for {}.{} (pending full integration)",
                teamName, memberName);
    }

    /**
     * Load personal and team memory and inject into system prompt context.
     */
    public CompletableFuture<String> loadAndInject(String query) {
        return CompletableFuture.supplyAsync(() -> {
            if (toolkit == null) {
                return "";
            }
            ensurePromptSection(null);
            StringBuilder sb = new StringBuilder();
            // Personal memory injection
            if (toolkit != null && toolkit.getCtx() != null) {
                sb.append(cachedBaseSection == null ? "" : cachedBaseSection.render(normalizeLanguage()));
            }
            // Shared team memory injection
            if (sharedManager != null) {
                try {
                    String teamSummary = sharedManager.readTeamSummary().join();
                    if (teamSummary != null && !teamSummary.isEmpty()) {
                        sb.append("\n## Team Memory\n").append(teamSummary);
                    }
                } catch (Exception e) {
                    Loggers.MEMORY.debug("[TeamMemoryManager] Failed to read team summary: {}", e.getMessage());
                }
            }
            return sb.toString().trim();
        });
    }

    /**
     * Load personal/team memory and inject a memory section into a DeepAgent-like object.
     */
    public CompletableFuture<Void> loadAndInject(Object deepAgent, String query) {
        return CompletableFuture.runAsync(() -> {
            if (toolkit == null) {
                return;
            }
            ensurePromptSection(deepAgent);
        });
    }

    /**
     * Extract memory after a coordination round.
     */
    public CompletableFuture<Void> extractAfterRound(String roundSummary) {
        if (!enableAutoExtract || toolkit == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            extractInvocationCount++;
            Loggers.MEMORY.debug("[TeamMemoryManager] Extract after round for {}.{}",
                    teamName, memberName);
            // Full extraction logic deferred
        });
    }

    /**
     * Close and release all resources.
     */
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            Object abilityManager = resolveAbilityManager(deepAgentForCleanup);
            invokeAbilityRemove(abilityManager, new ArrayList<>(ownedToolNames));
            ownedToolNames.clear();
            ownedToolIds.clear();
            deepAgentForCleanup = null;

            if (toolkit != null) {
                toolkit.close().join();
            }
            toolkit = null;
            sharedManager = null;
            cachedBaseSection = null;
            Loggers.MEMORY.info("[TeamMemoryManager] Closed for {}.{}", teamName, memberName);
        });
    }

    // ==================== Accessors ====================

    public MemberMemoryToolkit getToolkit() {
        return toolkit;
    }

    public SharedMemoryManager getSharedManager() {
        return sharedManager;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getTeamName() {
        return teamName;
    }

    public Object getWorkspace() {
        return workspace;
    }

    public Set<String> getOwnedToolNames() {
        return Collections.unmodifiableSet(ownedToolNames);
    }

    public Set<String> getOwnedToolIds() {
        return Collections.unmodifiableSet(ownedToolIds);
    }

    public PromptSection getCachedBaseSection() {
        return cachedBaseSection;
    }

    public int getExtractInvocationCount() {
        return extractInvocationCount;
    }

    private Object effectiveWorkspace() {
        return workspace != null ? workspace : createReadOnlyWorkspace(readOnlySourceWorkspace, language);
    }

    private static Workspace createReadOnlyWorkspace(String root, String language) {
        if (root == null || root.isBlank()) {
            return null;
        }
        return new Workspace(root, language);
    }

    private void ensurePromptSection(Object deepAgent) {
        if (cachedBaseSection == null) {
            String normalizedLanguage = normalizeLanguage();
            boolean readOnly = readOnlySourceWorkspace != null;
            PromptSection baseSection;
            if ("coding".equalsIgnoreCase(scenario)) {
                baseSection = CodingMemorySection.build(normalizedLanguage, readOnly, "coding_memory/");
            } else {
                baseSection = MemorySection.build(
                        normalizedLanguage, readOnly, "proactive".equalsIgnoreCase(promptMode));
            }
            cachedBaseSection = new PromptSection(SECTION_NAME, baseSection.getContent(), baseSection.getPriority());
        }
        Object builder = resolvePromptBuilder(deepAgent);
        invokePromptAdd(builder, cachedBaseSection);
    }

    private String normalizeLanguage() {
        return "cn".equalsIgnoreCase(language) ? "cn" : "en";
    }

    private static Object resolvePromptBuilder(Object deepAgent) {
        if (deepAgent == null) {
            return null;
        }
        try {
            Method method = deepAgent.getClass().getMethod("getSystemPromptBuilder");
            return method.invoke(deepAgent);
        } catch (Exception ignored) {
            // Fall through to field lookup.
        }
        return readField(deepAgent, "systemPromptBuilder", "system_prompt_builder");
    }

    private static Object resolveAbilityManager(Object deepAgent) {
        if (deepAgent == null) {
            return null;
        }
        try {
            Method method = deepAgent.getClass().getMethod("getAbilityManager");
            return method.invoke(deepAgent);
        } catch (Exception ignored) {
            // Fall through to field lookup.
        }
        return readField(deepAgent, "abilityManager", "ability_manager");
    }

    private static Object readField(Object target, String... names) {
        for (String name : names) {
            try {
                Field field = target.getClass().getField(name);
                return field.get(target);
            } catch (Exception ignored) {
                try {
                    Field field = target.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Exception ignoredAgain) {
                    // Try the next name.
                }
            }
        }
        return null;
    }

    private static void invokePromptAdd(Object builder, PromptSection section) {
        if (builder == null || section == null) {
            return;
        }
        try {
            Method method = builder.getClass().getMethod("addSection", PromptSection.class);
            method.invoke(builder, section);
        } catch (Exception ignored) {
            // Prompt injection is best effort for lightweight test doubles.
        }
    }

    private static void invokeAbilityAdd(Object abilityManager, ToolCard card) {
        if (abilityManager == null || card == null) {
            return;
        }
        try {
            Method method = abilityManager.getClass().getMethod("add", Object.class);
            method.invoke(abilityManager, card);
        } catch (Exception ignored) {
            // Ability registration is best effort for lightweight test doubles.
        }
    }

    private static void invokeAbilityRemove(Object abilityManager, List<String> names) {
        if (abilityManager == null || names == null || names.isEmpty()) {
            return;
        }
        try {
            Method method = abilityManager.getClass().getMethod("remove", List.class);
            method.invoke(abilityManager, names);
            return;
        } catch (Exception ignored) {
            // Try single-name removal below.
        }
        for (String name : names) {
            try {
                Method method = abilityManager.getClass().getMethod("remove", String.class);
                method.invoke(abilityManager, name);
            } catch (Exception ignored) {
                return;
            }
        }
    }
}
