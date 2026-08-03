/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.CodingMemorySection;
import com.openjiuwen.harness.prompts.sections.MemorySection;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team-scoped orchestration for per-member memory tools and prompt injection.
 *
 * <p>Mirrors Python's {@code TeamMemoryManager} in
 * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
 */
public class TeamMemoryManager {

    public static final String SECTION_NAME = "team_memory";
    public static final int MAX_PERSONAL_MEMORY_BYTES = 10 * 1024;

    private static final ResourceManagerView DEFAULT_RESOURCE_MANAGER = new InMemoryResourceManager();

    private final String memberName;
    private final String teamName;
    private final String role;
    private final String lifecycle;
    private final String scenario;
    private final EmbeddingConfig embeddingConfig;
    private final String language;
    private final String promptMode;
    private final boolean enableAutoExtract;
    private final String readOnlySource;
    private final TeamMemoryExtractor.TeamDatabaseView database;
    private final TeamMemoryExtractor.TeamTaskManagerView taskManager;
    private TeamMemoryExtractor.ModelView extractionModel;
    private final double timezoneOffsetHours;
    private final TeamMemoryExtractor.FileSystemView sysOperation;
    private final Workspace workspace;
    private final String teamMemoryDir;
    private final ToolkitFactory toolkitFactory;
    private final SharedMemoryManagerFactory sharedMemoryManagerFactory;
    private final ResourceManagerView resourceManager;
    private final ExtractionInvoker extractionInvoker;
    private final TeamMemoryExtractor.AgentFactory agentFactory;
    private final TeamMemoryExtractor.RunnerView runner;

    private MemberMemoryToolkitView toolkit;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private DeepAgentView deepAgentForCleanup;
    private SharedMemoryManagerView sharedManager;
    private PromptSection cachedBaseSection;

    public TeamMemoryManager(Parameters params) {
        Objects.requireNonNull(params, "params");
        this.memberName = nullToEmpty(params.memberName());
        this.teamName = nullToEmpty(params.teamName());
        this.role = nullToEmpty(params.role());
        this.lifecycle = nullToEmpty(params.lifecycle());
        this.scenario = nullToEmpty(params.scenario());
        this.embeddingConfig = params.embeddingConfig();
        this.language = isBlank(params.language()) ? "cn" : params.language();
        this.promptMode = isBlank(params.promptMode()) ? "passive" : params.promptMode();
        this.enableAutoExtract = params.enableAutoExtract();
        this.readOnlySource = emptyToNull(params.readOnlySourceWorkspace());
        this.database = params.database();
        this.taskManager = params.taskManager();
        this.extractionModel = params.extractionModel();
        this.timezoneOffsetHours = params.timezoneOffsetHours();
        this.sysOperation = params.sysOperation();
        this.workspace = this.readOnlySource == null
                ? params.workspace()
                : new Workspace(this.readOnlySource, this.language);
        this.teamMemoryDir = emptyToNull(params.teamMemoryDir());
        this.toolkitFactory = params.toolkitFactory() == null
                ? new DefaultToolkitFactory()
                : params.toolkitFactory();
        this.sharedMemoryManagerFactory = params.sharedMemoryManagerFactory() == null
                ? new FileSharedMemoryManagerFactory()
                : params.sharedMemoryManagerFactory();
        this.resourceManager = params.resourceManager() == null
                ? DEFAULT_RESOURCE_MANAGER
                : params.resourceManager();
        this.extractionInvoker = params.extractionInvoker() == null
                ? new DefaultExtractionInvoker()
                : params.extractionInvoker();
        this.agentFactory = params.agentFactory();
        this.runner = params.runner();
    }

    /**
     * Initialize the member toolkit and optional shared-memory manager.
     *
     * <p>Mirrors Python's {@code init_toolkit} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     *
     * @return future resolving to whether the toolkit initialized successfully
     */
    public CompletionStage<Boolean> initToolkit() {
        if (toolkit != null) {
            return CompletableFuture.completedFuture(true);
        }
        if (workspace == null) {
            return CompletableFuture.completedFuture(false);
        }

        MemberMemoryToolkitView createdToolkit;
        try {
            createdToolkit = toolkitFactory.create(new ToolkitRequest(
                    memberName,
                    teamName,
                    workspace,
                    scenario,
                    embeddingConfig,
                    sysOperation,
                    readOnlySource != null
            ));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(false);
        }
        if (createdToolkit == null) {
            return CompletableFuture.completedFuture(false);
        }
        this.toolkit = createdToolkit;

        return createdToolkit.initialize()
                .thenCompose(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    if (teamMemoryDir == null) {
                        return CompletableFuture.completedFuture(true);
                    }
                    sharedManager = sharedMemoryManagerFactory.create(teamMemoryDir, sysOperation);
                    if (sharedManager == null) {
                        return CompletableFuture.completedFuture(true);
                    }
                    return sharedManager.ensureDir().thenApply(ignored -> true);
                })
                .exceptionally(throwable -> false);
    }

    /**
     * Remove memory rails and register toolkit tools with the agent and resource manager.
     *
     * <p>Mirrors Python's {@code register_tools} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     *
     * @param deepAgent agent view to update
     */
    public void registerTools(DeepAgentView deepAgent) {
        if (!ownedToolNames.isEmpty() || deepAgent == null) {
            return;
        }

        try {
            deepAgent.stripMemoryRailsByType();
        } catch (RuntimeException ignored) {
            // Python logs and continues when optional runtime cleanup fails.
        }

        AbilityManagerView abilityManager = deepAgent.getAbilityManager();
        if (toolkit == null || abilityManager == null) {
            return;
        }

        deepAgentForCleanup = deepAgent;
        for (ToolView tool : safeTools(toolkit.getTools())) {
            try {
                ToolCard toolCard = tool.card();
                if (toolCard == null || isBlank(toolCard.id())) {
                    continue;
                }
                if (resourceManager.getTool(toolCard.id()).isEmpty()) {
                    resourceManager.addTool(tool);
                    ownedToolIds.add(toolCard.id());
                }
                AddResult result = abilityManager.add(toolCard);
                if (result != null && result.added()) {
                    ownedToolNames.add(toolCard.name());
                }
            } catch (RuntimeException ignored) {
                // Python catches per-tool failures so one bad tool does not block the manager.
            }
        }
    }

    /**
     * Load the memory prompt section and inject the per-round personal/team memory content.
     *
     * <p>Mirrors Python's {@code load_and_inject} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     *
     * @param deepAgent agent view containing a system prompt builder
     * @return completion signal
     */
    public CompletionStage<Void> loadAndInject(DeepAgentView deepAgent) {
        return loadAndInject(deepAgent, "");
    }

    public CompletionStage<Void> loadAndInject(DeepAgentView deepAgent, String query) {
        PromptBuilderView builder = deepAgent == null ? null : deepAgent.getSystemPromptBuilder();
        if (builder == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (cachedBaseSection == null) {
            PromptSection base = buildBaseSection();
            if (base == null) {
                return CompletableFuture.completedFuture(null);
            }
            cachedBaseSection = new PromptSection(SECTION_NAME, base.getContent(), base.getPriority());
        }

        Map<String, String> sectionContent = new LinkedHashMap<>(cachedBaseSection.getContent());
        CompletionStage<Optional<String>> personalStage = fetchPersonalMemoryForPrompt(query);
        CompletionStage<Optional<String>> teamStage = sharedManager == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : sharedManager.readTeamSummary().exceptionally(throwable -> Optional.empty());

        return personalStage.thenAcceptBoth(teamStage, (personalMemory, teamSummary) -> {
            boolean chinese = "cn".equals(language);
            personalMemory.filter(content -> !content.isBlank())
                    .ifPresent(content -> appendToAll(
                            sectionContent,
                            chinese
                                    ? "\n\n## \u4f60\u7684\u76f8\u5173\u8bb0\u5fc6\n\n"
                                    : "\n\n## Your relevant memories\n\n",
                            content
                    ));
            teamSummary.filter(content -> !content.isBlank())
                    .ifPresent(content -> appendToAll(
                            sectionContent,
                            chinese
                                    ? "\n\n## \u56e2\u961f\u5171\u4eab\u8bb0\u5fc6\n\n"
                                    : "\n\n## Team shared memory\n\n",
                            content
                    ));
            builder.removeSection(SECTION_NAME);
            builder.addSection(new PromptSection(
                    cachedBaseSection.getName(),
                    sectionContent,
                    cachedBaseSection.getPriority()
            ));
        }).exceptionally(throwable -> null);
    }

    /**
     * Trigger leader-side team-memory extraction when all Python gate conditions hold.
     *
     * <p>Mirrors Python's {@code extract_after_round} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     *
     * @return completion signal
     */
    public CompletionStage<Void> extractAfterRound() {
        if (!enableAutoExtract
                || !"persistent".equals(lifecycle)
                || !"leader".equals(role)
                || teamMemoryDir == null
                || database == null) {
            return CompletableFuture.completedFuture(null);
        }

        ExtractionInvocation invocation = new ExtractionInvocation(
                teamName,
                database,
                taskManager,
                teamMemoryDir,
                sysOperation,
                extractionModel,
                timezoneOffsetHours,
                agentFactory,
                runner
        );
        return extractionInvoker.extract(invocation).exceptionally(throwable -> null);
    }

    /**
     * Unmount prompt sections, abilities, resource-manager tools, and the member toolkit.
     *
     * <p>Mirrors Python's {@code close} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     *
     * @return completion signal
     */
    public CompletionStage<Void> close() {
        List<String> toolIds = new ArrayList<>(ownedToolIds);
        List<String> toolNames = new ArrayList<>(ownedToolNames);
        DeepAgentView cleanupAgent = deepAgentForCleanup;

        if (cleanupAgent != null) {
            PromptBuilderView builder = cleanupAgent.getSystemPromptBuilder();
            if (builder != null) {
                try {
                    builder.removeSection(SECTION_NAME);
                } catch (RuntimeException ignored) {
                    // Python logs and continues during best-effort cleanup.
                }
            }

            AbilityManagerView abilityManager = cleanupAgent.getAbilityManager();
            if (abilityManager != null && !toolNames.isEmpty()) {
                try {
                    abilityManager.remove(toolNames);
                } catch (RuntimeException ignored) {
                    // Python logs and continues during best-effort cleanup.
                }
            }
        }

        deepAgentForCleanup = null;
        ownedToolNames.clear();
        ownedToolIds.clear();

        for (String toolId : toolIds) {
            try {
                resourceManager.removeTool(toolId);
            } catch (RuntimeException ignored) {
                // Python logs and continues during best-effort cleanup.
            }
        }

        CompletionStage<Void> closeStage = toolkit == null
                ? CompletableFuture.completedFuture(null)
                : toolkit.close().exceptionally(throwable -> null);
        return closeStage.thenAccept(ignored -> {
            toolkit = null;
            cachedBaseSection = null;
        });
    }

    public TeamMemoryExtractor.ModelView getExtractionModel() {
        return extractionModel;
    }

    public void setExtractionModel(TeamMemoryExtractor.ModelView extractionModel) {
        this.extractionModel = extractionModel;
    }

    Set<String> getOwnedToolNames() {
        return Set.copyOf(ownedToolNames);
    }

    Set<String> getOwnedToolIds() {
        return Set.copyOf(ownedToolIds);
    }

    PromptSection getCachedBaseSection() {
        return cachedBaseSection;
    }

    MemberMemoryToolkitView getToolkit() {
        return toolkit;
    }

    DeepAgentView getDeepAgentForCleanup() {
        return deepAgentForCleanup;
    }

    Workspace getWorkspace() {
        return workspace;
    }

    private PromptSection buildBaseSection() {
        boolean readOnly = readOnlySource != null;
        if ("coding".equals(scenario)) {
            String memoryDir = "coding_memory/";
            if (workspace != null && workspace.getNodePath("coding_memory") != null) {
                memoryDir = workspace.getNodePath("coding_memory").toString();
            }
            return CodingMemorySection.buildCodingMemorySection(language, readOnly, memoryDir);
        }
        return MemorySection.buildMemorySection(language, readOnly, "proactive".equals(promptMode));
    }

    private CompletionStage<Optional<String>> fetchPersonalMemoryForPrompt(String query) {
        String nodeName = "coding".equals(scenario) ? "coding_memory" : "memory";
        MemoryIndexManagerView indexManager = toolkit == null ? null : toolkit.manager();
        if (!isBlank(query) && workspace != null && sysOperation != null && indexManager != null) {
            Path memoryDirPath = workspace.getNodePath(nodeName);
            return indexManager.search(query, new SearchOptions(5))
                    .thenCompose(results -> collectSearchResults(memoryDirPath, results))
                    .exceptionally(throwable -> Optional.empty())
                    .thenCompose(result -> result.isPresent()
                            ? CompletableFuture.completedFuture(result)
                            : readMemoryIndex(nodeName));
        }
        return readMemoryIndex(nodeName);
    }

    private CompletionStage<Optional<String>> collectSearchResults(
            Path memoryDirPath,
            List<MemorySearchResult> results
    ) {
        if (memoryDirPath == null || results == null || results.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        List<String> parts = new ArrayList<>();
        int[] totalBytes = new int[] {0};
        boolean[] stop = new boolean[] {false};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (MemorySearchResult result : results) {
            if (result == null || isBlank(result.path()) || result.path().endsWith("MEMORY.md")) {
                continue;
            }
            String resultPath = result.path();
            String fullPath = memoryDirPath.resolve(resultPath).normalize().toString();
            chain = chain.thenCompose(ignored -> {
                if (stop[0]) {
                    return CompletableFuture.completedFuture(null);
                }
                return sysOperation.readFile(fullPath).handle((content, throwable) -> {
                    if (throwable != null || content.isEmpty() || content.get().isBlank()) {
                        return null;
                    }
                    String memoryContent = content.get();
                    int contentBytes = memoryContent.getBytes(StandardCharsets.UTF_8).length;
                    if (totalBytes[0] + contentBytes > MAX_PERSONAL_MEMORY_BYTES) {
                        int remaining = MAX_PERSONAL_MEMORY_BYTES - totalBytes[0];
                        if (remaining > 200) {
                            parts.add("### " + resultPath + "\n\n"
                                    + truncateByCharBudget(memoryContent, remaining)
                                    + "\n... (truncated)");
                        }
                        stop[0] = true;
                        return null;
                    }
                    parts.add("### " + resultPath + "\n\n" + memoryContent);
                    totalBytes[0] += contentBytes;
                    return null;
                });
            });
        }

        return chain.thenApply(ignored -> parts.isEmpty()
                ? Optional.empty()
                : Optional.of(String.join("\n\n---\n\n", parts)));
    }

    private CompletionStage<Optional<String>> readMemoryIndex(String nodeName) {
        if (workspace == null || sysOperation == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Path memoryDirPath = workspace.getNodePath(nodeName);
        if (memoryDirPath == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String indexPath = memoryDirPath.resolve("MEMORY.md").normalize().toString();
        return sysOperation.readFile(indexPath)
                .thenApply(content -> content.map(String::trim).filter(value -> !value.isBlank()))
                .exceptionally(throwable -> Optional.empty());
    }

    private static List<ToolView> safeTools(List<ToolView> tools) {
        return tools == null ? List.of() : tools;
    }

    private static void appendToAll(Map<String, String> content, String header, String addition) {
        content.replaceAll((key, value) -> nullToEmpty(value) + header + addition);
    }

    private static String truncateByCharBudget(String value, int budget) {
        return value.substring(0, Math.min(value.length(), budget));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Constructor parameters for the manager boundary.
     *
     * <p>Mirrors Python's {@code TeamMemoryManager.__init__} inputs in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface Parameters {
        String memberName();

        String teamName();

        String role();

        String lifecycle();

        String scenario();

        default EmbeddingConfig embeddingConfig() {
            return null;
        }

        default Workspace workspace() {
            return null;
        }

        default TeamMemoryExtractor.FileSystemView sysOperation() {
            return null;
        }

        default String teamMemoryDir() {
            return null;
        }

        default String language() {
            return "cn";
        }

        default String promptMode() {
            return "passive";
        }

        default boolean enableAutoExtract() {
            return false;
        }

        default String readOnlySourceWorkspace() {
            return null;
        }

        default TeamMemoryExtractor.TeamDatabaseView database() {
            return null;
        }

        default TeamMemoryExtractor.TeamTaskManagerView taskManager() {
            return null;
        }

        default TeamMemoryExtractor.ModelView extractionModel() {
            return null;
        }

        default double timezoneOffsetHours() {
            return 8.0d;
        }

        default ToolkitFactory toolkitFactory() {
            return null;
        }

        default SharedMemoryManagerFactory sharedMemoryManagerFactory() {
            return null;
        }

        default ResourceManagerView resourceManager() {
            return null;
        }

        default ExtractionInvoker extractionInvoker() {
            return null;
        }

        default TeamMemoryExtractor.AgentFactory agentFactory() {
            return null;
        }

        default TeamMemoryExtractor.RunnerView runner() {
            return null;
        }
    }

    /**
     * Toolkit construction request.
     *
     * <p>Mirrors Python's {@code MemberMemoryToolkit(...)} call from
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public record ToolkitRequest(
            String memberName,
            String teamName,
            Workspace workspace,
            String scenario,
            EmbeddingConfig embeddingConfig,
            TeamMemoryExtractor.FileSystemView sysOperation,
            boolean readOnly
    ) {
    }

    /**
     * Factory for member-memory toolkit adapters.
     *
     * <p>Mirrors Python's runtime import of {@code MemberMemoryToolkit} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface ToolkitFactory {
        MemberMemoryToolkitView create(ToolkitRequest request);
    }

    /**
     * Narrow member-memory toolkit surface used by the manager.
     *
     * <p>Mirrors Python's {@code MemberMemoryToolkit} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface MemberMemoryToolkitView {
        CompletionStage<Boolean> initialize();

        List<ToolView> getTools();

        MemoryIndexManagerView manager();

        CompletionStage<Void> close();
    }

    /**
     * Personal memory index search surface.
     *
     * <p>Mirrors Python's {@code MemoryIndexManager.search} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface MemoryIndexManagerView {
        CompletionStage<List<MemorySearchResult>> search(String query, SearchOptions options);
    }

    /**
     * Search options for personal-memory lookup.
     *
     * <p>Mirrors Python's {@code opts={"max_results": 5}} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public record SearchOptions(int maxResults) {
    }

    /**
     * Search result path from the personal-memory index.
     *
     * <p>Mirrors Python's search result dict path in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public record MemorySearchResult(String path) {
    }

    /**
     * Tool adapter used for registration.
     *
     * <p>Mirrors Python's toolkit tool objects in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface ToolView {
        ToolCard card();
    }

    /**
     * Tool identity metadata used by ability/resource managers.
     *
     * <p>Mirrors Python's {@code tool.card} access in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public record ToolCard(String id, String name) {
    }

    /**
     * Deep agent surface needed by memory manager lifecycle hooks.
     *
     * <p>Mirrors Python's {@code deep_agent} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface DeepAgentView {
        PromptBuilderView getSystemPromptBuilder();

        AbilityManagerView getAbilityManager();

        default int stripMemoryRailsByType() {
            return 0;
        }
    }

    /**
     * System prompt builder surface.
     *
     * <p>Mirrors Python's {@code system_prompt_builder} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface PromptBuilderView {
        void removeSection(String name);

        void addSection(PromptSection section);
    }

    /**
     * Ability manager surface.
     *
     * <p>Mirrors Python's {@code ability_manager.add/remove} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface AbilityManagerView {
        AddResult add(ToolCard toolCard);

        void remove(List<String> names);
    }

    /**
     * Result returned by ability registration.
     *
     * <p>Mirrors Python's {@code result.added} contract in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public record AddResult(boolean added) {
    }

    /**
     * Global resource manager surface.
     *
     * <p>Mirrors Python's {@code Runner.resource_mgr} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface ResourceManagerView {
        Optional<ToolView> getTool(String id);

        void addTool(ToolView tool);

        void removeTool(String id);
    }

    /**
     * Shared-memory manager factory.
     *
     * <p>Mirrors Python's runtime import of {@code SharedMemoryManager} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface SharedMemoryManagerFactory {
        SharedMemoryManagerView create(String teamMemoryDir, TeamMemoryExtractor.FileSystemView sysOperation);
    }

    /**
     * Shared team-memory surface.
     *
     * <p>Mirrors Python's {@code SharedMemoryManager.ensure_dir/read_team_summary} usage in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface SharedMemoryManagerView {
        CompletionStage<Void> ensureDir();

        CompletionStage<Optional<String>> readTeamSummary();
    }

    /**
     * Team-memory extraction call boundary.
     *
     * <p>Mirrors Python's {@code extract_team_memories(...)} call in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public interface ExtractionInvoker {
        CompletionStage<Void> extract(ExtractionInvocation invocation);
    }

    /**
     * Arguments forwarded to the extraction helper.
     *
     * <p>Mirrors Python's keyword arguments to {@code extract_team_memories} in
     * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
     */
    public record ExtractionInvocation(
            String teamName,
            TeamMemoryExtractor.TeamDatabaseView database,
            TeamMemoryExtractor.TeamTaskManagerView taskManager,
            String teamMemoryDir,
            TeamMemoryExtractor.FileSystemView sysOperation,
            TeamMemoryExtractor.ModelView model,
            double timezoneOffsetHours,
            TeamMemoryExtractor.AgentFactory agentFactory,
            TeamMemoryExtractor.RunnerView runner
    ) {
    }

    private static final class DefaultToolkitFactory implements ToolkitFactory {
        @Override
        public MemberMemoryToolkitView create(ToolkitRequest request) {
            return new MemberMemoryToolkit(
                    request.memberName(),
                    request.teamName(),
                    request.workspace(),
                    request.scenario(),
                    request.embeddingConfig(),
                    request.sysOperation(),
                    request.readOnly()
            );
        }
    }

    private static final class InMemoryResourceManager implements ResourceManagerView {
        private final Map<String, ToolView> tools = new LinkedHashMap<>();

        @Override
        public Optional<ToolView> getTool(String id) {
            return Optional.ofNullable(tools.get(id));
        }

        @Override
        public void addTool(ToolView tool) {
            if (tool != null && tool.card() != null) {
                tools.put(tool.card().id(), tool);
            }
        }

        @Override
        public void removeTool(String id) {
            tools.remove(id);
        }
    }

    private static final class FileSharedMemoryManagerFactory implements SharedMemoryManagerFactory {
        @Override
        public SharedMemoryManagerView create(String teamMemoryDir, TeamMemoryExtractor.FileSystemView sysOperation) {
            return new SharedMemoryManager(teamMemoryDir, sysOperation);
        }
    }

    private static final class DefaultExtractionInvoker implements ExtractionInvoker {
        @Override
        public CompletionStage<Void> extract(ExtractionInvocation invocation) {
            if (invocation.agentFactory() == null || invocation.runner() == null) {
                return CompletableFuture.completedFuture(null);
            }
            return TeamMemoryExtractor.extractTeamMemories(new TeamMemoryExtractor.ExtractionRequest(
                    invocation.teamName(),
                    invocation.database(),
                    invocation.taskManager(),
                    invocation.teamMemoryDir(),
                    invocation.sysOperation(),
                    invocation.model(),
                    invocation.timezoneOffsetHours(),
                    invocation.agentFactory(),
                    invocation.runner()
            ));
        }
    }
}
