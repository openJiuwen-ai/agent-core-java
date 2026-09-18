/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceManager;
import com.openjiuwen.harness.tools.worktree.WorktreeConfig;
import com.openjiuwen.harness.tools.worktree.WorktreeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests team tool rail registration and cleanup behavior.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/rails/team_tool_rail.py}.</p>
 */
class TeamToolRailTest {

    @Test
    void initRegistersFactoryToolsOnResourceAndAbilityManagers() {
        FakeBackend backend = new FakeBackend();
        FakeFactory factory = new FakeFactory(List.of(tool("create_task", "create_task")));
        FakeResourceManager resourceManager = new FakeResourceManager();
        FakeAbilityManager abilityManager = new FakeAbilityManager();
        TeamToolRail rail = new TeamToolRail(new TeamToolRail.Config(
                backend,
                "leader",
                "plan_mode",
                "persistent",
                "en",
                null,
                null,
                Set.of("clean_team"),
                null,
                null,
                false,
                "alpha",
                "leader",
                factory,
                null,
                resourceManager
        ));

        rail.init(new FakeAgent(abilityManager));
        rail.init(new FakeAgent(abilityManager));

        assertThat(factory.calls).isEqualTo(1);
        assertThat(factory.lastBackend).isSameAs(backend);
        assertThat(factory.lastRole).isEqualTo("leader");
        assertThat(factory.lastTeammateMode).isEqualTo("plan_mode");
        assertThat(factory.lastLifecycle).isEqualTo("persistent");
        assertThat(factory.lastLanguage).isEqualTo("en");
        assertThat(factory.lastExcludeTools).containsExactly("clean_team");
        assertThat(resourceManager.refreshValues).containsExactly(true);
        assertThat(resourceManager.addedIds).containsExactly("create_task");
        assertThat(abilityManager.addedNames).containsExactly("create_task");
        assertThat(rail.getTools()).hasSize(1);
    }

    @Test
    void initAppendsWorkspaceAndWorktreeExtensionTools() {
        TeamWorkspaceManager workspaceManager = new TeamWorkspaceManager(null, "workspace", "team-a");
        WorktreeManager worktreeManager = new WorktreeManager(new WorktreeConfig());
        FakeExtensionFactory extensionFactory = new FakeExtensionFactory();
        FakeResourceManager resourceManager = new FakeResourceManager();
        FakeAbilityManager abilityManager = new FakeAbilityManager();
        TeamToolRail rail = new TeamToolRail(new TeamToolRail.Config(
                new FakeBackend(),
                "teammate",
                null,
                null,
                "cn",
                null,
                null,
                null,
                workspaceManager,
                worktreeManager,
                false,
                null,
                null,
                new FakeFactory(List.of(tool("view_task", "view_task"))),
                extensionFactory,
                resourceManager
        ));

        rail.init(new FakeAgent(abilityManager));

        assertThat(extensionFactory.workspaceManager).isSameAs(workspaceManager);
        assertThat(extensionFactory.worktreeManager).isSameAs(worktreeManager);
        assertThat(extensionFactory.sessionStateInitCalls).isEqualTo(1);
        assertThat(resourceManager.addedIds)
                .containsExactly("view_task", "workspace_meta", "enter_worktree", "exit_worktree");
        assertThat(abilityManager.addedNames)
                .containsExactly("view_task", "workspace_meta", "enter_worktree", "exit_worktree");
    }

    @Test
    void qualifyToolIdsSuffixesTeamAndMember() {
        TeamToolRail.CardTool first = tool("send_message", "send_message");
        TeamToolRail.CardTool missing = new TeamToolRail.CardTool(null);
        List<TeamToolRail.ToolView> tools = new ArrayList<>(List.of(first, missing));

        TeamToolRail.qualifyTeamToolIds(tools, "alpha", "leader");
        TeamToolRail.qualifyTeamToolIds(tools, "alpha", "leader");

        assertThat(first.getCard().getId()).isEqualTo("send_message.alpha.leader.alpha.leader");
        assertThat(missing.getCard()).isNull();
    }

    @Test
    void initQualifiesIdsWhenConfigured() {
        FakeResourceManager resourceManager = new FakeResourceManager();
        TeamToolRail rail = new TeamToolRail(new TeamToolRail.Config(
                new FakeBackend(),
                "leader",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                "team-x",
                "member-y",
                new FakeFactory(List.of(tool("build_team", "build_team"))),
                null,
                resourceManager
        ));

        rail.init(new FakeAgent(new FakeAbilityManager()));

        assertThat(resourceManager.addedIds).containsExactly("build_team.team-x.member-y");
    }

    @Test
    void uninitRemovesAbilityAndResourceRegistrations() {
        FakeResourceManager resourceManager = new FakeResourceManager();
        FakeAbilityManager abilityManager = new FakeAbilityManager();
        TeamToolRail rail = new TeamToolRail(new TeamToolRail.Config(
                new FakeBackend(),
                "leader",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                new FakeFactory(List.of(tool("claim_task", "claim_task"))),
                null,
                resourceManager
        ));

        rail.init(new FakeAgent(abilityManager));
        rail.uninit(new FakeAgent(abilityManager));
        rail.uninit(new FakeAgent(abilityManager));

        assertThat(abilityManager.removedNames).containsExactly("claim_task");
        assertThat(resourceManager.removedIds).containsExactly("claim_task");
        assertThat(rail.getTools()).isEmpty();
    }

    @Test
    void resourceManagerFailuresAreBestEffort() {
        FakeResourceManager resourceManager = new FakeResourceManager();
        resourceManager.failAdd = true;
        resourceManager.failRemove = true;
        FakeAbilityManager abilityManager = new FakeAbilityManager();
        TeamToolRail rail = new TeamToolRail(new TeamToolRail.Config(
                new FakeBackend(),
                "leader",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                new FakeFactory(List.of(tool("send_message", "send_message"))),
                null,
                resourceManager
        ));

        rail.init(new FakeAgent(abilityManager));
        rail.uninit(new FakeAgent(abilityManager));

        assertThat(abilityManager.addedNames).containsExactly("send_message");
        assertThat(abilityManager.removedNames).containsExactly("send_message");
    }

    private static TeamToolRail.CardTool tool(String id, String name) {
        return new TeamToolRail.CardTool(new TeamToolRail.MutableToolCard(id, name));
    }

    private static final class FakeBackend implements TeamToolRail.TeamBackend {
    }

    private static final class FakeAgent implements TeamToolRail.TeamToolAgent {
        private final FakeAbilityManager abilityManager;

        private FakeAgent(FakeAbilityManager abilityManager) {
            this.abilityManager = abilityManager;
        }

        @Override
        public TeamToolRail.AbilityManagerView getAbilityManager() {
            return abilityManager;
        }
    }

    private static final class FakeAbilityManager implements TeamToolRail.AbilityManagerView {
        private final List<String> addedNames = new ArrayList<>();
        private final List<String> removedNames = new ArrayList<>();

        @Override
        public void add(TeamToolRail.ToolCardView card) {
            addedNames.add(card.getName());
        }

        @Override
        public void remove(String name) {
            removedNames.add(name);
        }
    }

    private static final class FakeResourceManager implements TeamToolRail.ResourceManagerView {
        private final List<String> addedIds = new ArrayList<>();
        private final List<String> removedIds = new ArrayList<>();
        private final List<Boolean> refreshValues = new ArrayList<>();
        private boolean failAdd;
        private boolean failRemove;

        @Override
        public void addTools(Collection<? extends TeamToolRail.ToolView> tools, boolean refresh) {
            if (failAdd) {
                throw new IllegalStateException("resource manager unavailable");
            }
            refreshValues.add(refresh);
            for (TeamToolRail.ToolView tool : tools) {
                if (tool != null && tool.getCard() != null) {
                    addedIds.add(tool.getCard().getId());
                }
            }
        }

        @Override
        public void removeTool(String toolId) {
            if (failRemove) {
                throw new IllegalStateException("resource manager unavailable");
            }
            removedIds.add(toolId);
        }
    }

    private static final class FakeFactory implements TeamToolRail.TeamToolFactory {
        private final List<TeamToolRail.ToolView> tools;
        private int calls;
        private TeamToolRail.TeamBackend lastBackend;
        private String lastRole;
        private String lastTeammateMode;
        private String lastLifecycle;
        private String lastLanguage;
        private Set<String> lastExcludeTools;

        private FakeFactory(List<TeamToolRail.ToolView> tools) {
            this.tools = tools;
        }

        @Override
        public List<TeamToolRail.ToolView> createTeamTools(
                TeamToolRail.TeamBackend teamBackend,
                String role,
                String teammateMode,
                String lifecycle,
                String language,
                TeamToolRail.TeammateCreatedCallback onTeammateCreated,
                TeamToolRail.ModelConfigAllocator modelConfigAllocator,
                Set<String> excludeTools
        ) {
            calls += 1;
            lastBackend = teamBackend;
            lastRole = role;
            lastTeammateMode = teammateMode;
            lastLifecycle = lifecycle;
            lastLanguage = language;
            lastExcludeTools = excludeTools;
            return tools;
        }
    }

    private static final class FakeExtensionFactory implements TeamToolRail.ExtensionToolFactory {
        private TeamWorkspaceManager workspaceManager;
        private WorktreeManager worktreeManager;
        private int sessionStateInitCalls;

        @Override
        public TeamToolRail.ToolView createWorkspaceMetaTool(
                TeamWorkspaceManager workspaceManager,
                String language
        ) {
            this.workspaceManager = workspaceManager;
            return tool("workspace_meta", "workspace_meta");
        }

        @Override
        public List<TeamToolRail.ToolView> createWorktreeTools(
                WorktreeManager worktreeManager,
                String language
        ) {
            this.worktreeManager = worktreeManager;
            return List.of(tool("enter_worktree", "enter_worktree"), tool("exit_worktree", "exit_worktree"));
        }

        @Override
        public void initWorktreeSessionState() {
            sessionStateInitCalls += 1;
        }
    }
}
