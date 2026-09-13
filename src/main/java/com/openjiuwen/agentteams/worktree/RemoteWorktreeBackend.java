/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import com.openjiuwen.agentteams.messager.Messager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leader-side remote worktree backend matching Python worktree.remote.RemoteWorktreeBackend.
 * 
 * @since 0.1.7
 */
public class RemoteWorktreeBackend {
    private final WorktreeConfig config;
    private final Messager messager;
    private final String nodeId;
    private final Duration requestTimeout;

    /**
     * RemoteWorktreeBackend.
     * 
     * @param config config
     * @param messager messager
     * @param nodeId nodeId
     * @since 0.1.7
     */
    public RemoteWorktreeBackend(WorktreeConfig config, Messager messager, String nodeId) {
        this(config, messager, nodeId, Duration.ofSeconds(30));
    }

    /**
     * RemoteWorktreeBackend.
     * 
     * @param config config
     * @param messager messager
     * @param nodeId nodeId
     * @param requestTimeout requestTimeout
     * @since 0.1.7
     */
    public RemoteWorktreeBackend(WorktreeConfig config, Messager messager, String nodeId, Duration requestTimeout) {
        this.config = config != null ? config : WorktreeConfig.builder().build();
        this.messager = messager;
        this.nodeId = nodeId;
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
    }

    /**
     * create.
     * 
     * @param slug slug
     * @param repoRoot repoRoot
     * @param targetPath targetPath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public WorktreeCreateResult create(String slug, String repoRoot, String targetPath) throws IOException {
        String repoUrl = getRepoUrl(repoRoot);
        String baseBranch = defaultBranch(repoRoot);
        WorktreeRemoteRequest request = WorktreeRemoteRequest.builder().action("create").slug(slug).repoUrl(repoUrl)
                .baseBranch(baseBranch).config(configPayload()).build();
        WorktreeRemoteResponse response = sendAndWait(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("Remote worktree creation failed: " + response.getError());
        }
        return WorktreeCreateResult.builder().worktreePath(response.getWorktreePath())
                .worktreeBranch(response.getWorktreeBranch()).headCommit(response.getHeadCommit())
                .isExisted(response.isExisted()).build();
    }

    /**
     * remove.
     * 
     * @param worktreePath worktreePath
     * @param repoRoot repoRoot
     * @return the result
     * @since 0.1.7
     */
    public boolean remove(String worktreePath, String repoRoot) {
        WorktreeRemoteResponse response =
            sendAndWait(WorktreeRemoteRequest.builder().action("remove").worktreePath(worktreePath).build());
        return response.isSuccess();
    }

    /**
     * isExists.
     * 
     * @param worktreePath worktreePath
     * @return the result
     * @since 0.1.7
     */
    public boolean isExists(String worktreePath) {
        WorktreeRemoteResponse response =
            sendAndWait(WorktreeRemoteRequest.builder().action("exists").worktreePath(worktreePath).build());
        return response.isExists();
    }

    /**
     * exists.
     * 
     * @param worktreePath worktreePath
     * @return the result
     * @since 0.1.7
     */
    public boolean exists(String worktreePath) {
        return isExists(worktreePath);
    }

    /**
     * sendAndWait.
     * 
     * @param request request
     * @return the result
     * @since 0.1.7
     */
    private WorktreeRemoteResponse sendAndWait(WorktreeRemoteRequest request) {
        if (messager == null || nodeId == null || nodeId.isBlank()) {
            throw new IllegalStateException("messager and nodeId are required for remote worktree backend");
        }
        Map<String, Object> response = messager.sendAndWait(nodeId, request.toPayload(), requestTimeout).join();
        return WorktreeRemoteResponse.fromPayload(response);
    }

    /**
     * configPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> configPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("base_dir", config.getBaseDir());
        payload.put("lifecycle_policy",
                config.getLifecyclePolicy() != null ? config.getLifecyclePolicy().name() : null);
        payload.put("cleanup_after_days", config.getCleanupAfterDays());
        payload.put("sparse_paths", config.getSparsePaths());
        return payload;
    }

    /**
     * getRepoUrl.
     * 
     * @param repoRoot repoRoot
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static String getRepoUrl(String repoRoot) throws IOException {
        GitCommandResult result = runGit(Path.of(repoRoot), java.util.List.of("remote", "get-url", "origin"));
        if (result.code() != 0 || result.output().isBlank()) {
            throw new IllegalStateException("Cannot determine remote URL");
        }
        return result.output().trim();
    }

    /**
     * defaultBranch.
     * 
     * @param repoRoot repoRoot
     * @return the result
     * @since 0.1.7
     */
    private static String defaultBranch(String repoRoot) {
        GitCommandResult symbolic = runGit(Path.of(repoRoot), java.util.List.of("symbolic-ref", "--short", "HEAD"));
        if (symbolic.code() == 0 && !symbolic.output().isBlank()) {
            return symbolic.output().trim();
        }
        return "main";
    }

    /**
     * runGit.
     * 
     * @param cwd cwd
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static GitCommandResult runGit(Path cwd, java.util.List<String> args) {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        java.util.Map<String, String> env = builder.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "");
        try {
            Process process = builder.start();
            byte[] output = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            return new GitCommandResult(code, new String(output, StandardCharsets.UTF_8).replaceAll("\\R+$", ""));
        } catch (IOException e) {
            return new GitCommandResult(1, e.getMessage() == null ? "" : e.getMessage());
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10)
            return new GitCommandResult(1, e.getMessage() == null ? "" : e.getMessage());
        }
    }

    /**
     * GitCommandResult.
     * 
     * @param code code
     * @param output output
     * @since 0.1.7
     */
    private record GitCommandResult(int code, String output) {
    }
}
