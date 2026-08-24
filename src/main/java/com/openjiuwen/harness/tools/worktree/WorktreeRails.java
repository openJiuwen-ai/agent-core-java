/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Worktree lifecycle rails.
 *
 * <p>Mirrors Python's {@code WorktreeRail}, {@code WorktreeLifecycleRail},
 * {@code AutoSetupRail}, and {@code DiffSummaryRail} in
 * {@code openjiuwen/harness/tools/worktree/rails.py}.</p>
 */
public final class WorktreeRails {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String SESSION_STATE_KEY = "_worktree_session";
    public static final String DEFAULT_WORKTREE_NAME_KEY = "_worktree_default_name";

    private WorktreeRails() {
    }

    /**
     * Mirrors Python's {@code WorktreeRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class WorktreeRail extends DeepAgentRail {
        private final WorktreeConfig userConfig;
        private final WorktreeEventHandler eventHandler;
        private final List<WorktreeLifecycleRail> lifecycleRails;
        private WorktreeManager manager;
        private List<Tool> tools = new ArrayList<>();

        public WorktreeRail() {
            this(null, null, null);
        }

        public WorktreeRail(WorktreeConfig config) {
            this(config, null, null);
        }

        public WorktreeRail(WorktreeEventHandler eventHandler) {
            this(null, eventHandler, null);
        }

        public WorktreeRail(
                WorktreeConfig config,
                WorktreeEventHandler eventHandler,
                List<WorktreeLifecycleRail> lifecycleRails
        ) {
            WorktreeConfig resolvedConfig = config == null ? new WorktreeConfig() : config;
            if (config == null) {
                resolvedConfig.setEnabled(true);
            }
            this.userConfig = resolvedConfig;
            this.eventHandler = eventHandler;
            this.lifecycleRails = lifecycleRails == null ? List.of() : List.copyOf(lifecycleRails);
        }

        public WorktreeManager getManager() {
            return manager;
        }

        public List<Tool> getTools() {
            return List.copyOf(tools);
        }

        @Override
        public void init(DeepAgent agent) {
            super.init(agent);
            manager = new WorktreeManager(userConfig, null, eventHandler, lifecycleRails);
            WorktreeSessionContext.initSessionState();

            String agentId = agent == null || agent.getCard() == null ? null : agent.getCard().getId();
            String language = resolveLanguage(agent);
            tools = List.of(
                    new EnterWorktreeTool(manager, language, agentId),
                    new ExitWorktreeTool(manager, language, agentId)
            );
            for (Tool tool : tools) {
                Runner.resourceMgr().addTool(tool);
                if (agent != null && agent.getAbilityManager() != null) {
                    agent.getAbilityManager().add(tool.getCard());
                }
            }
        }

        @Override
        public void uninit(DeepAgent agent) {
            for (Tool tool : tools) {
                if (tool == null || tool.getCard() == null) {
                    continue;
                }
                if (agent != null && agent.getAbilityManager() != null) {
                    agent.getAbilityManager().remove(tool.getCard().getName());
                }
                Runner.resourceMgr().removeTool(tool.getCard().getId());
            }
            tools = new ArrayList<>();
            manager = null;
        }

        @Override
        public void beforeInvoke(CallbackContext ctx) {
            AgentSessionApi session = sessionFrom(ctx);
            if (session == null) {
                return;
            }
            Object defaultName = session.getState(DEFAULT_WORKTREE_NAME_KEY);
            WorktreeSessionContext.setDefaultWorktreeName(defaultName instanceof String text ? text : null);

            Object stored = session.getState(SESSION_STATE_KEY);
            if (stored == null) {
                WorktreeSessionContext.setCurrentSession(null);
                return;
            }
            WorktreeSession restored = stored instanceof WorktreeSession worktreeSession
                    ? worktreeSession
                    : JSON.convertValue(stored, WorktreeSession.class);
            WorktreeSessionContext.setCurrentSession(restored);
            if (restored.getWorktreePath() != null && !restored.getWorktreePath().isBlank()) {
                Cwd.setCwd(restored.getWorktreePath());
                Cwd.setOriginalCwd(restored.getWorktreePath());
            }
        }

        @Override
        public void afterInvoke(CallbackContext ctx) {
            AgentSessionApi session = sessionFrom(ctx);
            if (session == null) {
                return;
            }
            WorktreeSession current = WorktreeSessionContext.getCurrentSession();
            Map<String, Object> update = new LinkedHashMap<>();
            update.put(SESSION_STATE_KEY, current == null ? null : JSON.convertValue(current, Map.class));
            update.put(DEFAULT_WORKTREE_NAME_KEY, WorktreeSessionContext.getDefaultWorktreeName());
            session.updateState(update);
        }

        private static String resolveLanguage(DeepAgent agent) {
            if (agent == null || agent.deepConfig() == null) {
                return "cn";
            }
            String language = agent.deepConfig().getLanguage();
            return language == null || language.isBlank() ? "cn" : language;
        }

        private static AgentSessionApi sessionFrom(CallbackContext ctx) {
            if (ctx == null) {
                return null;
            }
            Object value = ctx.get("session");
            if (!(value instanceof AgentSessionApi)) {
                value = ctx.get("agent_session");
            }
            return value instanceof AgentSessionApi session ? session : null;
        }
    }

    /**
     * Mirrors Python's {@code WorktreeLifecycleRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class WorktreeLifecycleRail extends DeepAgentRail {
        @Override
        public void afterInvoke(CallbackContext ctx) {
        }

        public String beforeWorktreeCreate(CallbackContext ctx, String slug, String repoRoot) {
            return null;
        }

        public void afterWorktreeCreate(CallbackContext ctx, WorktreeSession session) {
        }

        public String beforeWorktreeExit(CallbackContext ctx, WorktreeSession session, String action) {
            return null;
        }

        public void afterWorktreeExit(CallbackContext ctx, WorktreeSession session, String action) {
        }

        public boolean onWorktreeFileWrite(CallbackContext ctx, WorktreeSession session, String filePath) {
            return true;
        }

        public String beforeWorktreeCommit(
                CallbackContext ctx,
                WorktreeSession session,
                String message,
                List<String> files
        ) {
            return null;
        }

        public void afterWorktreeCommit(CallbackContext ctx, WorktreeSession session, String commitSha) {
        }

        public List<String> onWorktreeSync(
                CallbackContext ctx,
                WorktreeSession session,
                String direction,
                List<String> files
        ) {
            return files;
        }
    }

    /**
     * Mirrors Python's {@code AutoSetupRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class AutoSetupRail extends WorktreeLifecycleRail {
        private final List<String> commands;

        public AutoSetupRail() {
            this(null);
        }

        public AutoSetupRail(List<String> commands) {
            this.commands = commands == null ? List.of() : List.copyOf(commands);
        }

        @Override
        public void afterWorktreeCreate(CallbackContext ctx, WorktreeSession session) {
            // The translated Java SDK does not launch shell setup commands from tests.
            // Keep the hook no-op unless an integration caller wires command execution.
        }

        public List<String> getCommands() {
            return commands;
        }
    }

    /**
     * Mirrors Python's {@code DiffSummaryRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class DiffSummaryRail extends WorktreeLifecycleRail {
        @Override
        public String beforeWorktreeExit(CallbackContext ctx, WorktreeSession session, String action) {
            return null;
        }
    }
}
