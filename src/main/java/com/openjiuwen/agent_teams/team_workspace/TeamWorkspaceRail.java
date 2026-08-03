/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceArtifactEvent;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sys_operation.Cwd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.DoubleSupplier;

/**
 * Transparent version control and locking for team shared space.
 *
 * <p>Mirrors Python's {@code TeamWorkspaceRail} in
 * {@code openjiuwen/agent_teams/team_workspace/rails.py}.</p>
 */
public class TeamWorkspaceRail {

    public static final String TEAM_PREFIX = ".team/";
    public static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");
    public static final Set<String> READ_TOOLS = Set.of("read_file", "glob", "grep", "list_files");

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final double DEFAULT_PULL_INTERVAL_SECONDS = 5.0D;

    private final TeamWorkspaceManager workspaceManager;
    private final String memberName;
    private final DoubleSupplier monotonicSeconds;
    private double lastPullTime;
    private double pullInterval;

    public TeamWorkspaceRail(TeamWorkspaceManager workspaceManager, String memberName) {
        this(workspaceManager, memberName, () -> System.nanoTime() / 1_000_000_000.0D);
    }

    TeamWorkspaceRail(
            TeamWorkspaceManager workspaceManager,
            String memberName,
            DoubleSupplier monotonicSeconds
    ) {
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager");
        this.memberName = memberName;
        this.monotonicSeconds = monotonicSeconds == null
                ? () -> System.nanoTime() / 1_000_000_000.0D
                : monotonicSeconds;
        this.lastPullTime = 0.0D;
        this.pullInterval = DEFAULT_PULL_INTERVAL_SECONDS;
    }

    public void init(Object ignoredAgent) {
        Cwd.setTeamWorkspace(workspaceManager.getWorkspacePath());
    }

    public CompletionStage<Void> beforeToolCall(WorkspaceCallbackContext context) {
        WorkspaceCallbackContext effectiveContext = WorkspaceCallbackContext.of(context);
        String toolName = effectiveContext.inputs().toolName();
        String path = filePath(effectiveContext.inputs().toolArgs());
        if (path.isEmpty() || !path.startsWith(TEAM_PREFIX)) {
            return CompletableFuture.completedFuture(null);
        }

        if (READ_TOOLS.contains(toolName)) {
            return maybePull();
        }
        if (!WRITE_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(null);
        }

        return maybePull().thenRun(() -> checkLock(path, effectiveContext.extra()));
    }

    public CompletionStage<Void> beforeToolCall(
            String toolName,
            Map<String, ?> toolArgs,
            Map<String, Object> extra
    ) {
        return beforeToolCall(new WorkspaceCallbackContext(new ToolCallInputs(toolName, toolArgs), extra));
    }

    public CompletionStage<Void> afterToolCall(WorkspaceCallbackContext context) {
        WorkspaceCallbackContext effectiveContext = WorkspaceCallbackContext.of(context);
        String toolName = effectiveContext.inputs().toolName();
        if (!WRITE_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(null);
        }

        String path = filePath(effectiveContext.inputs().toolArgs());
        if (!path.startsWith(TEAM_PREFIX)) {
            return CompletableFuture.completedFuture(null);
        }

        String realPath = resolveWorkspaceRelative(path);
        CompletionStage<?> committed = workspaceManager.getConfig().isVersionControl()
                ? workspaceManager.autoCommit(realPath, memberName)
                : CompletableFuture.completedFuture(null);
        return committed.thenCompose(ignored -> publishArtifactEvent(realPath));
    }

    public CompletionStage<Void> afterToolCall(String toolName, Map<String, ?> toolArgs) {
        return afterToolCall(new WorkspaceCallbackContext(new ToolCallInputs(toolName, toolArgs), null));
    }

    String resolveWorkspaceRelative(String path) {
        String afterPrefix = path.substring(TEAM_PREFIX.length());
        String teamNamePrefix = workspaceManager.getTeamName() + "/";
        if (afterPrefix.startsWith(teamNamePrefix)) {
            return afterPrefix.substring(teamNamePrefix.length());
        }
        return afterPrefix;
    }

    void setPullIntervalForTests(double pullInterval) {
        this.pullInterval = pullInterval;
    }

    private CompletionStage<Void> maybePull() {
        if (workspaceManager.getMode() != WorkspaceMode.DISTRIBUTED) {
            return CompletableFuture.completedFuture(null);
        }
        double now = monotonicSeconds.getAsDouble();
        if (now - lastPullTime < pullInterval) {
            return CompletableFuture.completedFuture(null);
        }
        lastPullTime = now;
        return workspaceManager.pull().thenApply(ignored -> null);
    }

    private void checkLock(String path, Map<String, Object> extra) {
        if (workspaceManager.getConfig().getConflictStrategy() != ConflictStrategy.LOCK) {
            return;
        }
        WorkspaceFileLock lock = workspaceManager.getLock(path);
        if (lock == null || Objects.equals(lock.getHolderId(), memberName) || lock.isExpired()) {
            return;
        }
        String message = "File '" + path + "' is locked by "
                + lock.getHolderName() + " (" + lock.getHolderId() + ")";
        TEAM_LOGGER.warning(message);
        extra.put("workspace_lock_rejected", message);
    }

    private CompletionStage<Void> publishArtifactEvent(String realPath) {
        TeamWorkspaceManager.PublishEventCallback publisher = workspaceManager.getPublishEvent();
        if (publisher == null) {
            return CompletableFuture.completedFuture(null);
        }
        WorkspaceArtifactEvent event = new WorkspaceArtifactEvent();
        event.setTeamName(workspaceManager.getTeamName());
        event.setMemberName(memberName);
        event.setArtifactPath(realPath);
        return publisher.publish(TeamEvent.WORKSPACE_ARTIFACT_UPDATED, event);
    }

    private static String filePath(Map<String, ?> toolArgs) {
        if (toolArgs == null) {
            return "";
        }
        Object value = toolArgs.get("file_path");
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Minimal callback context for tool-call rail hooks.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext} argument in
     * {@code openjiuwen/agent_teams/team_workspace/rails.py}.</p>
     */
    public record WorkspaceCallbackContext(ToolCallInputs inputs, Map<String, Object> extra) {
        public WorkspaceCallbackContext {
            inputs = inputs == null ? new ToolCallInputs("", Map.of()) : inputs;
            extra = extra == null ? new LinkedHashMap<>() : extra;
        }

        static WorkspaceCallbackContext of(WorkspaceCallbackContext context) {
            return context == null ? new WorkspaceCallbackContext(null, null) : context;
        }
    }

    /**
     * Minimal tool-call input view used by {@link WorkspaceCallbackContext}.
     *
     * <p>Mirrors Python's {@code ctx.inputs.tool_name} and
     * {@code ctx.inputs.tool_args} access in
     * {@code openjiuwen/agent_teams/team_workspace/rails.py}.</p>
     */
    public record ToolCallInputs(String toolName, Map<String, ?> toolArgs) {
        public ToolCallInputs {
            toolName = toolName == null ? "" : toolName;
            toolArgs = toolArgs == null ? Map.of() : new LinkedHashMap<>(toolArgs);
        }
    }
}
