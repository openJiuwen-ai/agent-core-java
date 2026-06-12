/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.tools.worktree.WorktreeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Registers team coordination tools on an agent.
 *
 * <p>Mirrors Python's {@code TeamToolRail} in
 * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
 */
public class TeamToolRail {

    public static final int PRIORITY = 90;

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final TeamBackend teamBackend;
    private final String role;
    private final String teammateMode;
    private final String lifecycle;
    private final String language;
    private final TeammateCreatedCallback onTeammateCreated;
    private final ModelConfigAllocator modelConfigAllocator;
    private final Set<String> excludeTools;
    private final TeamWorkspaceManager workspaceManager;
    private final WorktreeManager worktreeManager;
    private final boolean qualifyIds;
    private final String teamName;
    private final String memberName;
    private final TeamToolFactory teamToolFactory;
    private final ExtensionToolFactory extensionToolFactory;
    private final ResourceManagerView resourceManager;

    private List<ToolView> tools;

    public TeamToolRail(Config config) {
        Config effectiveConfig = Objects.requireNonNull(config, "config");
        this.teamBackend = Objects.requireNonNull(effectiveConfig.teamBackend(), "teamBackend");
        this.role = defaultString(effectiveConfig.role(), "");
        this.teammateMode = defaultString(effectiveConfig.teammateMode(), "build_mode");
        this.lifecycle = defaultString(effectiveConfig.lifecycle(), "temporary");
        this.language = defaultString(effectiveConfig.language(), "cn");
        this.onTeammateCreated = effectiveConfig.onTeammateCreated();
        this.modelConfigAllocator = effectiveConfig.modelConfigAllocator();
        this.excludeTools = effectiveConfig.excludeTools() == null ? Set.of() : Set.copyOf(effectiveConfig.excludeTools());
        this.workspaceManager = effectiveConfig.workspaceManager();
        this.worktreeManager = effectiveConfig.worktreeManager();
        this.qualifyIds = effectiveConfig.qualifyIds();
        this.teamName = defaultString(effectiveConfig.teamName(), "default");
        this.memberName = defaultString(effectiveConfig.memberName(), "unknown");
        this.teamToolFactory = effectiveConfig.teamToolFactory() == null
                ? TeamToolFactory.empty()
                : effectiveConfig.teamToolFactory();
        this.extensionToolFactory = effectiveConfig.extensionToolFactory() == null
                ? ExtensionToolFactory.none()
                : effectiveConfig.extensionToolFactory();
        this.resourceManager = effectiveConfig.resourceManager() == null
                ? ResourceManagerView.noop()
                : effectiveConfig.resourceManager();
    }

    public int getPriority() {
        return PRIORITY;
    }

    public List<ToolView> getTools() {
        return tools == null ? List.of() : List.copyOf(tools);
    }

    public void init(TeamToolAgent agent) {
        if (tools != null) {
            return;
        }

        List<ToolView> builtTools = new ArrayList<>(teamToolFactory.createTeamTools(
                teamBackend,
                role,
                teammateMode,
                lifecycle,
                language,
                onTeammateCreated,
                modelConfigAllocator,
                excludeTools
        ));

        if (workspaceManager != null) {
            ToolView workspaceTool = extensionToolFactory.createWorkspaceMetaTool(workspaceManager, language);
            if (workspaceTool != null) {
                builtTools.add(workspaceTool);
            }
        }

        if (worktreeManager != null) {
            builtTools.addAll(extensionToolFactory.createWorktreeTools(worktreeManager, language));
            extensionToolFactory.initWorktreeSessionState();
        }

        if (qualifyIds) {
            qualifyTeamToolIds(builtTools, teamName, memberName);
        }

        try {
            resourceManager.addTools(builtTools, true);
        } catch (RuntimeException exception) {
            TEAM_LOGGER.debug("Runner.resource_mgr not available, skipping tool registration");
        }

        AbilityManagerView abilityManager = agent == null ? null : agent.getAbilityManager();
        if (abilityManager != null) {
            for (ToolView tool : builtTools) {
                ToolCardView card = tool == null ? null : tool.getCard();
                if (card != null) {
                    abilityManager.add(card);
                }
            }
        }

        tools = List.copyOf(builtTools);
    }

    public void uninit(TeamToolAgent agent) {
        if (tools == null || tools.isEmpty()) {
            return;
        }

        AbilityManagerView abilityManager = agent == null ? null : agent.getAbilityManager();
        for (ToolView tool : tools) {
            ToolCardView card = tool == null ? null : tool.getCard();
            if (card == null) {
                continue;
            }

            String name = card.getName();
            if (abilityManager != null && isNonBlank(name)) {
                abilityManager.remove(name);
            }

            String id = card.getId();
            if (isNonBlank(id)) {
                try {
                    resourceManager.removeTool(id);
                } catch (RuntimeException exception) {
                    TEAM_LOGGER.debug("Runner.resource_mgr removal failed for {}", id);
                }
            }
        }

        tools = null;
    }

    public static void qualifyTeamToolIds(
            List<? extends ToolView> teamTools,
            String teamName,
            String memberName
    ) {
        if (teamTools == null || teamTools.isEmpty()) {
            return;
        }
        String teamKey = defaultString(teamName, "default");
        String memberKey = defaultString(memberName, "unknown");
        for (ToolView tool : teamTools) {
            ToolCardView card = tool == null ? null : tool.getCard();
            if (card == null || !isNonBlank(card.getId())) {
                continue;
            }
            String qualifiedId = card.getId() + "." + teamKey + "." + memberKey;
            if (!Objects.equals(card.getId(), qualifiedId)) {
                card.setId(qualifiedId);
            }
        }
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Constructor options for {@link TeamToolRail}.
     *
     * <p>Mirrors Python's keyword-only {@code TeamToolRail.__init__} parameters in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public record Config(
            TeamBackend teamBackend,
            String role,
            String teammateMode,
            String lifecycle,
            String language,
            TeammateCreatedCallback onTeammateCreated,
            ModelConfigAllocator modelConfigAllocator,
            Set<String> excludeTools,
            TeamWorkspaceManager workspaceManager,
            WorktreeManager worktreeManager,
            boolean qualifyIds,
            String teamName,
            String memberName,
            TeamToolFactory teamToolFactory,
            ExtensionToolFactory extensionToolFactory,
            ResourceManagerView resourceManager
    ) {
        public Config {
            role = defaultString(role, "");
            teammateMode = defaultString(teammateMode, "build_mode");
            lifecycle = defaultString(lifecycle, "temporary");
            language = defaultString(language, "cn");
            excludeTools = excludeTools == null ? Set.of() : Set.copyOf(excludeTools);
            teamName = defaultString(teamName, "default");
            memberName = defaultString(memberName, "unknown");
        }
    }

    /**
     * Minimal agent view used to access the shared ability manager.
     *
     * <p>Mirrors Python's {@code getattr(agent, "ability_manager", None)} access in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface TeamToolAgent {
        AbilityManagerView getAbilityManager();
    }

    /**
     * Minimal ability manager view used to add and remove tool cards.
     *
     * <p>Mirrors Python's {@code ability_manager.add/remove} calls in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface AbilityManagerView {
        void add(ToolCardView card);

        void remove(String name);
    }

    /**
     * Minimal resource manager view for process-global tool registration.
     *
     * <p>Mirrors Python's {@code Runner.resource_mgr.add_tool/remove_tool} calls in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface ResourceManagerView {
        void addTools(Collection<? extends ToolView> tools, boolean refresh);

        void removeTool(String toolId);

        static ResourceManagerView noop() {
            return new ResourceManagerView() {
                @Override
                public void addTools(Collection<? extends ToolView> tools, boolean refresh) {
                    // Intentionally empty default resource manager.
                }

                @Override
                public void removeTool(String toolId) {
                    // Intentionally empty default resource manager.
                }
            };
        }
    }

    /**
     * Factory boundary for Python's {@code create_team_tools(...)} helper.
     *
     * <p>Mirrors Python's {@code create_team_tools} import in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    @FunctionalInterface
    public interface TeamToolFactory {
        List<ToolView> createTeamTools(
                TeamBackend teamBackend,
                String role,
                String teammateMode,
                String lifecycle,
                String language,
                TeammateCreatedCallback onTeammateCreated,
                ModelConfigAllocator modelConfigAllocator,
                Set<String> excludeTools
        );

        static TeamToolFactory empty() {
            return (teamBackend, role, teammateMode, lifecycle, language, onTeammateCreated,
                    modelConfigAllocator, excludeTools) -> List.of();
        }
    }

    /**
     * Factory boundary for optional workspace/worktree tools.
     *
     * <p>Mirrors Python's lazy imports of workspace and worktree tools in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface ExtensionToolFactory {
        ToolView createWorkspaceMetaTool(TeamWorkspaceManager workspaceManager, String language);

        List<ToolView> createWorktreeTools(WorktreeManager worktreeManager, String language);

        void initWorktreeSessionState();

        static ExtensionToolFactory none() {
            return new ExtensionToolFactory() {
                @Override
                public ToolView createWorkspaceMetaTool(TeamWorkspaceManager workspaceManager, String language) {
                    return null;
                }

                @Override
                public List<ToolView> createWorktreeTools(WorktreeManager worktreeManager, String language) {
                    return List.of();
                }

                @Override
                public void initWorktreeSessionState() {
                    // Intentionally empty when no worktree tools are configured.
                }
            };
        }
    }

    /**
     * Minimal tool view exposing the mutable card used by the rail.
     *
     * <p>Mirrors Python's {@code tool.card} access in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface ToolView {
        ToolCardView getCard();
    }

    /**
     * Mutable tool-card view required because Python mutates {@code tool.card.id}.
     *
     * <p>Mirrors Python's {@code ToolCard.id/name} fields in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface ToolCardView {
        String getId();

        void setId(String id);

        String getName();
    }

    /**
     * Simple mutable card useful for adapters and focused tests.
     *
     * <p>Mirrors Python's mutable {@code ToolCard} objects in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public static final class MutableToolCard implements ToolCardView {
        private String id;
        private final String name;

        public MutableToolCard(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * Simple card-backed tool useful for adapters and focused tests.
     *
     * <p>Mirrors Python's generic {@code Tool} instances in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public static final class CardTool implements ToolView {
        private final ToolCardView card;

        public CardTool(ToolCardView card) {
            this.card = card;
        }

        @Override
        public ToolCardView getCard() {
            return card;
        }
    }

    /**
     * Marker for the team backend supplied to {@code create_team_tools}.
     *
     * <p>Mirrors Python's {@code TeamBackend} parameter in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface TeamBackend {
    }

    /**
     * Callback invoked when a teammate is created.
     *
     * <p>Mirrors Python's {@code on_teammate_created} callable in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    @FunctionalInterface
    public interface TeammateCreatedCallback {
        CompletionStage<Void> onCreated(String memberName);
    }

    /**
     * Allocates optional model configuration for a teammate.
     *
     * <p>Mirrors Python's {@code model_config_allocator} callable in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    @FunctionalInterface
    public interface ModelConfigAllocator {
        AllocationView allocate(String memberName);
    }

    /**
     * Marker for allocation results passed through to team-tool creation.
     *
     * <p>Mirrors Python's {@code Allocation} type-checking import in
     * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
     */
    public interface AllocationView {
    }
}
