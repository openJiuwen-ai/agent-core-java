/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemoryIndexManagerView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemorySearchResult;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.SearchOptions;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolCard;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolView;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Per-member memory toolkit for team agents.
 *
 * <p>Mirrors Python's {@code MemberMemoryToolkit} in
 * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
 */
public class MemberMemoryToolkit implements TeamMemoryManager.MemberMemoryToolkitView {

    private final String memberName;
    private final String teamName;
    private final Workspace workspace;
    private final String scenario;
    private final EmbeddingConfig embeddingConfig;
    private final TeamMemoryExtractor.FileSystemView sysOperation;
    private final boolean readOnly;
    private final MemoryIndexManagerProvider managerProvider;
    private final MemoryEnabledPolicy memoryEnabledPolicy;

    private MemoryIndexManagerView manager;
    private MemoryToolContextView context;
    private List<MemoryLocalFunction> tools = new ArrayList<>();
    private boolean initialized;

    public MemberMemoryToolkit(String memberName, String teamName, Workspace workspace) {
        this(memberName, teamName, workspace, "general", null, null, false);
    }

    public MemberMemoryToolkit(
            String memberName,
            String teamName,
            Workspace workspace,
            String scenario,
            EmbeddingConfig embeddingConfig,
            TeamMemoryExtractor.FileSystemView sysOperation,
            boolean readOnly
    ) {
        this(
                memberName,
                teamName,
                workspace,
                scenario,
                embeddingConfig,
                sysOperation,
                readOnly,
                new DefaultMemoryIndexManagerProvider(),
                MemorySettings::isMemoryEnabled
        );
    }

    MemberMemoryToolkit(
            String memberName,
            String teamName,
            Workspace workspace,
            String scenario,
            EmbeddingConfig embeddingConfig,
            TeamMemoryExtractor.FileSystemView sysOperation,
            boolean readOnly,
            MemoryIndexManagerProvider managerProvider,
            MemoryEnabledPolicy memoryEnabledPolicy
    ) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.scenario = normalizeScenario(scenario);
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.readOnly = readOnly;
        this.managerProvider = Objects.requireNonNull(managerProvider, "managerProvider");
        this.memoryEnabledPolicy = Objects.requireNonNull(memoryEnabledPolicy, "memoryEnabledPolicy");
    }

    /**
     * Initialize the memory index manager, context, and tool list.
     *
     * <p>Mirrors Python's {@code initialize} in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     *
     * @return future resolving to initialization success
     */
    @Override
    public CompletionStage<Boolean> initialize() {
        if (initialized && manager instanceof ClosableMemoryIndexManagerView closable && !closable.isClosed()) {
            return CompletableFuture.completedFuture(true);
        }
        if (initialized && manager != null && !(manager instanceof ClosableMemoryIndexManagerView)) {
            return CompletableFuture.completedFuture(true);
        }
        if (!memoryEnabledPolicy.isMemoryEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        String agentId = teamName + "." + memberName;
        String nodeName = isCodingScenario() ? "coding_memory" : "memory";
        Path nodePath = workspace.getNodePath(nodeName);
        String memoryDir = nodePath == null ? "" : nodePath.toString();
        MemorySettings settings = MemorySettings.createMemorySettings(memoryDir, Map.of());
        MemoryManagerParams params = new MemoryManagerParams(
                agentId,
                workspace,
                settings,
                embeddingConfig,
                sysOperation,
                nodeName
        );

        return managerProvider.get(params)
                .thenApply(resolvedManager -> {
                    if (resolvedManager == null) {
                        manager = null;
                        return false;
                    }
                    manager = resolvedManager;
                    if (isCodingScenario()) {
                        context = new CodingMemoryToolContext(
                                workspace,
                                settings,
                                agentId,
                                embeddingConfig,
                                sysOperation,
                                manager,
                                memoryDir,
                                "coding_memory"
                        );
                        tools = createCodingTools(this, readOnly);
                    } else {
                        context = new MemoryToolContext(
                                workspace,
                                settings,
                                agentId,
                                embeddingConfig,
                                sysOperation,
                                manager
                        );
                        tools = createGeneralTools(this, readOnly);
                    }
                    initialized = true;
                    return true;
                })
                .exceptionally(throwable -> {
                    manager = null;
                    return false;
                });
    }

    @Override
    public List<ToolView> getTools() {
        return List.copyOf(tools);
    }

    public List<MemoryLocalFunction> getLocalFunctions() {
        return List.copyOf(tools);
    }

    public List<ToolCard> getToolCards() {
        return tools.stream().map(MemoryLocalFunction::card).toList();
    }

    @Override
    public MemoryIndexManagerView manager() {
        return manager;
    }

    public MemoryIndexManagerView getManager() {
        return manager;
    }

    public MemoryToolContextView getContext() {
        return context;
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

    /**
     * Close the manager and clear local toolkit state.
     *
     * <p>Mirrors Python's {@code close} in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     *
     * @return completion signal
     */
    @Override
    public CompletionStage<Void> close() {
        CompletionStage<Void> closeStage = manager instanceof ClosableMemoryIndexManagerView closable
                ? closable.close().exceptionally(throwable -> null)
                : CompletableFuture.completedFuture(null);
        return closeStage.thenAccept(ignored -> {
            manager = null;
            context = null;
            tools = new ArrayList<>();
            initialized = false;
        });
    }

    /**
     * Create general memory tools.
     *
     * <p>Mirrors Python's {@code _create_general_tools} in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public static List<MemoryLocalFunction> createGeneralTools(MemberMemoryToolkit toolkit, boolean readOnly) {
        if (!(toolkit.context instanceof MemoryToolContext)) {
            throw new IllegalStateException("Expected MemoryToolContext");
        }
        String prefix = "memory." + toolkit.teamName + "." + toolkit.memberName;
        List<MemoryLocalFunction> result = new ArrayList<>();
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".memory_search", "memory_search"),
                MemoryToolOperation.MEMORY_SEARCH,
                InputSchema.of(
                        List.of("query"),
                        new ToolParameter("query", "string", "search query text", null),
                        new ToolParameter("max_results", "integer", "max number of results to return", null),
                        new ToolParameter("min_score", "number", "minimum similarity score threshold", null),
                        new ToolParameter("session_key", "string", "optional session key for context", null)
                )
        ));
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".memory_get", "memory_get"),
                MemoryToolOperation.MEMORY_GET,
                InputSchema.of(
                        List.of("path"),
                        new ToolParameter("path", "string", "file path", null),
                        new ToolParameter("from_line", "integer", "start line number", null),
                        new ToolParameter("lines", "integer", "number of lines to read", null)
                )
        ));
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".read_memory", "read_memory"),
                MemoryToolOperation.READ_MEMORY,
                InputSchema.of(
                        List.of("path"),
                        new ToolParameter("path", "string", "file path", null),
                        new ToolParameter("offset", "integer", "start line offset", null),
                        new ToolParameter("limit", "integer", "max lines to read", null)
                )
        ));
        if (readOnly) {
            return result;
        }
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".write_memory", "write_memory"),
                MemoryToolOperation.WRITE_MEMORY,
                InputSchema.of(
                        List.of("path", "content"),
                        new ToolParameter("path", "string", "file path", null),
                        new ToolParameter("content", "string", "content to be write", null),
                        new ToolParameter("append", "boolean", "append to file or overwrite", Boolean.FALSE)
                )
        ));
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".edit_memory", "edit_memory"),
                MemoryToolOperation.EDIT_MEMORY,
                InputSchema.of(
                        List.of("path", "old_text", "new_text"),
                        new ToolParameter("path", "string", "file path", null),
                        new ToolParameter("old_text", "string", "old memory in file", null),
                        new ToolParameter("new_text", "string", "new memory to be write", null)
                )
        ));
        return result;
    }

    /**
     * Create coding-memory tools.
     *
     * <p>Mirrors Python's {@code _create_coding_tools} in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public static List<MemoryLocalFunction> createCodingTools(MemberMemoryToolkit toolkit, boolean readOnly) {
        if (!(toolkit.context instanceof CodingMemoryToolContext)) {
            throw new IllegalStateException("Expected CodingMemoryToolContext");
        }
        String prefix = "coding_memory." + toolkit.teamName + "." + toolkit.memberName;
        List<MemoryLocalFunction> result = new ArrayList<>();
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".coding_memory_read", "coding_memory_read"),
                MemoryToolOperation.CODING_MEMORY_READ,
                InputSchema.of(
                        List.of("path"),
                        new ToolParameter("path", "string", "file path to read", null),
                        new ToolParameter("offset", "integer", "start line offset", null),
                        new ToolParameter("limit", "integer", "max number of lines to read", null)
                )
        ));
        if (readOnly) {
            return result;
        }
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".coding_memory_write", "coding_memory_write"),
                MemoryToolOperation.CODING_MEMORY_WRITE,
                InputSchema.of(
                        List.of("path", "content"),
                        new ToolParameter("path", "string", "file path to write", null),
                        new ToolParameter("content", "string", "content to write", null)
                )
        ));
        result.add(new MemoryLocalFunction(
                new ToolCard(prefix + ".coding_memory_edit", "coding_memory_edit"),
                MemoryToolOperation.CODING_MEMORY_EDIT,
                InputSchema.of(
                        List.of("path", "old_text", "new_text"),
                        new ToolParameter("path", "string", "file path to edit", null),
                        new ToolParameter("old_text", "string", "old text to be replaced", null),
                        new ToolParameter("new_text", "string", "new text to replace with", null)
                )
        ));
        return result;
    }

    private boolean isCodingScenario() {
        return "coding".equals(scenario);
    }

    private static String normalizeScenario(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return "general";
        }
        return scenario.strip().toLowerCase();
    }

    /**
     * Memory manager construction parameters.
     *
     * <p>Mirrors Python's {@code MemoryManagerParams} usage in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public record MemoryManagerParams(
            String agentId,
            Workspace workspace,
            MemorySettings settings,
            EmbeddingConfig embeddingConfig,
            TeamMemoryExtractor.FileSystemView sysOperation,
            String nodeName
    ) {
    }

    /**
     * Provider for memory index managers.
     *
     * <p>Mirrors Python's {@code MemoryIndexManager.get} call in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public interface MemoryIndexManagerProvider {
        CompletionStage<MemoryIndexManagerView> get(MemoryManagerParams params);
    }

    /**
     * Memory enablement policy.
     *
     * <p>Mirrors Python's {@code is_memory_enabled()} check in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public interface MemoryEnabledPolicy {
        boolean isMemoryEnabled();
    }

    /**
     * Close-aware memory manager surface.
     *
     * <p>Mirrors Python's {@code MemoryIndexManager.closed/close} usage in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public interface ClosableMemoryIndexManagerView extends MemoryIndexManagerView {
        boolean isClosed();

        CompletionStage<Void> close();
    }

    /**
     * Toolkit context marker.
     *
     * <p>Mirrors Python memory tool context objects in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public interface MemoryToolContextView {
    }

    /**
     * General memory tool context.
     *
     * <p>Mirrors Python's {@code MemoryToolContext} use in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public record MemoryToolContext(
            Workspace workspace,
            MemorySettings settings,
            String agentId,
            EmbeddingConfig embeddingConfig,
            TeamMemoryExtractor.FileSystemView sysOperation,
            MemoryIndexManagerView manager
    ) implements MemoryToolContextView {
    }

    /**
     * Coding memory tool context.
     *
     * <p>Mirrors Python's {@code CodingMemoryToolContext} use in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public record CodingMemoryToolContext(
            Workspace workspace,
            MemorySettings settings,
            String agentId,
            EmbeddingConfig embeddingConfig,
            TeamMemoryExtractor.FileSystemView sysOperation,
            MemoryIndexManagerView manager,
            String codingMemoryDir,
            String nodeName
    ) implements MemoryToolContextView {
    }

    /**
     * Local memory tool descriptor.
     *
     * <p>Mirrors Python's decorated {@code LocalFunction} objects in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public record MemoryLocalFunction(
            ToolCard card,
            MemoryToolOperation operation,
            InputSchema inputSchema
    ) implements ToolView {
    }

    /**
     * Memory tool operation kind.
     *
     * <p>Mirrors Python's tool function names in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public enum MemoryToolOperation {
        MEMORY_SEARCH,
        MEMORY_GET,
        READ_MEMORY,
        WRITE_MEMORY,
        EDIT_MEMORY,
        CODING_MEMORY_READ,
        CODING_MEMORY_WRITE,
        CODING_MEMORY_EDIT
    }

    /**
     * Tool input schema.
     *
     * <p>Mirrors Python's {@code input_params} dictionaries in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public record InputSchema(List<String> required, List<ToolParameter> properties) {
        public InputSchema {
            required = required == null ? List.of() : List.copyOf(required);
            properties = properties == null ? List.of() : List.copyOf(properties);
        }

        public static InputSchema of(List<String> required, ToolParameter... properties) {
            return new InputSchema(required, List.of(properties));
        }
    }

    /**
     * Tool parameter schema.
     *
     * <p>Mirrors Python's parameter schema entries in
     * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
     */
    public record ToolParameter(String name, String type, String description, Boolean defaultBoolean) {
    }

    private static final class DefaultMemoryIndexManagerProvider implements MemoryIndexManagerProvider {
        @Override
        public CompletionStage<MemoryIndexManagerView> get(MemoryManagerParams params) {
            return CompletableFuture.completedFuture(new InMemoryMemoryIndexManager());
        }
    }

    private static final class InMemoryMemoryIndexManager implements ClosableMemoryIndexManagerView {
        private boolean closed;

        @Override
        public CompletionStage<List<MemorySearchResult>> search(String query, SearchOptions options) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
