/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.tools.worktree.Git;
import com.openjiuwen.harness.tools.worktree.WorktreeBackend;
import com.openjiuwen.harness.tools.worktree.WorktreeConfig;
import com.openjiuwen.harness.tools.worktree.WorktreeCreateResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Distributed worktree backend for remote nodes.
 *
 * <p>Mirrors Python's {@code WorktreeRemoteRequest},
 * {@code WorktreeRemoteResponse}, {@code RemoteWorktreeBackend}, and
 * {@code WorktreeRemoteHandler} in
 * {@code openjiuwen/agent_teams/worktree_remote.py}.</p>
 */
public final class WorktreeRemote {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private WorktreeRemote() {
    }

    /**
     * Java adapter for the direct request/response messaging boundary used by
     * {@code RemoteWorktreeBackend} in
     * {@code openjiuwen/agent_teams/worktree_remote.py}.
     */
    public interface RemoteWorktreeMessager {

        CompletionStage<Map<String, Object>> sendAndWait(String nodeId, Map<String, Object> payload);
    }

    /**
     * Java adapter for the owner-scoped worktree manager calls used by
     * {@code WorktreeRemoteHandler} in
     * {@code openjiuwen/agent_teams/worktree_remote.py}.
     */
    public interface RemoteWorktreeManager {

        CompletionStage<WorktreeCreateResult> createOwnerWorktree(String slug);

        CompletionStage<Boolean> removeWorktree(String worktreePath, String repoRoot);

        WorktreeBackend getBackend();
    }

    /**
     * Request sent to a remote node to manage a worktree.
     *
     * <p>Mirrors Python's {@code WorktreeRemoteRequest} in
     * {@code openjiuwen/agent_teams/worktree_remote.py}.</p>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorktreeRemoteRequest {
        private String action;
        private String slug;
        @JsonProperty("repo_url")
        private String repoUrl;
        @JsonProperty("base_branch")
        private String baseBranch;
        @JsonProperty("worktree_path")
        private String worktreePath;
        private Map<String, Object> config;
    }

    /**
     * Response from a remote node after a worktree operation.
     *
     * <p>Mirrors Python's {@code WorktreeRemoteResponse} in
     * {@code openjiuwen/agent_teams/worktree_remote.py}.</p>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorktreeRemoteResponse {
        private boolean success = true;
        @JsonProperty("worktree_path")
        private String worktreePath;
        @JsonProperty("worktree_branch")
        private String worktreeBranch;
        @JsonProperty("head_commit")
        private String headCommit;
        private boolean existed = false;
        private boolean exists = false;
        private String error;
    }

    /**
     * Worktree backend for remote nodes.
     *
     * <p>Mirrors Python's {@code RemoteWorktreeBackend} in
     * {@code openjiuwen/agent_teams/worktree_remote.py}.</p>
     */
    public static class RemoteWorktreeBackend implements WorktreeBackend {

        private final WorktreeConfig config;
        private final RemoteWorktreeMessager messager;
        private final String nodeId;

        public RemoteWorktreeBackend(WorktreeConfig config, RemoteWorktreeMessager messager, String nodeId) {
            this.config = config;
            this.messager = messager;
            this.nodeId = nodeId;
        }

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath) {
            WorktreeRemoteRequest request = new WorktreeRemoteRequest();
            request.setAction("create");
            request.setSlug(slug);
            request.setRepoUrl(getRepoUrl(repoRoot).join());
            request.setBaseBranch(Git.getDefaultBranch(repoRoot).join());
            request.setConfig(OBJECT_MAPPER.convertValue(config, MAP_TYPE));
            return sendAndWait(request).thenApply(response -> {
                if (!response.isSuccess()) {
                    throw new RuntimeException("Remote worktree creation failed: " + response.getError());
                }
                WorktreeCreateResult result = new WorktreeCreateResult();
                result.setWorktreePath(defaultString(response.getWorktreePath()));
                result.setWorktreeBranch(response.getWorktreeBranch());
                result.setHeadCommit(response.getHeadCommit());
                result.setExisted(response.isExisted());
                return result;
            });
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
            WorktreeRemoteRequest request = new WorktreeRemoteRequest();
            request.setAction("remove");
            request.setWorktreePath(worktreePath);
            return sendAndWait(request).thenApply(WorktreeRemoteResponse::isSuccess);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            WorktreeRemoteRequest request = new WorktreeRemoteRequest();
            request.setAction("exists");
            request.setWorktreePath(worktreePath);
            return sendAndWait(request).thenApply(WorktreeRemoteResponse::isExists);
        }

        private CompletableFuture<WorktreeRemoteResponse> sendAndWait(WorktreeRemoteRequest request) {
            TEAM_LOGGER.debug("Sending worktree {} request to node {}", request.getAction(), nodeId);
            Map<String, Object> payload = OBJECT_MAPPER.convertValue(request, MAP_TYPE);
            return messager.sendAndWait(nodeId, payload).toCompletableFuture()
                    .thenApply(responseData -> OBJECT_MAPPER.convertValue(responseData, WorktreeRemoteResponse.class));
        }

        private CompletableFuture<String> getRepoUrl(String repoRoot) {
            return Git.runGit(java.util.List.of("remote", "get-url", "origin"), repoRoot)
                    .thenApply(result -> {
                        if (!result.ok()) {
                            throw new RuntimeException("Cannot determine remote URL");
                        }
                        return result.stdout();
                    });
        }
    }

    /**
     * Handles worktree requests on a remote node.
     *
     * <p>Mirrors Python's {@code WorktreeRemoteHandler} in
     * {@code openjiuwen/agent_teams/worktree_remote.py}.</p>
     */
    public static class WorktreeRemoteHandler {

        private final RemoteWorktreeManager manager;
        private final Map<String, String> clonedRepos = new LinkedHashMap<>();

        public WorktreeRemoteHandler(RemoteWorktreeManager manager) {
            this.manager = manager;
        }

        public CompletableFuture<WorktreeRemoteResponse> handle(WorktreeRemoteRequest request) {
            return switch (request.getAction()) {
                case "create" -> handleCreate(request);
                case "remove" -> handleRemove(request);
                case "exists" -> handleExists(request);
                default -> CompletableFuture.completedFuture(
                        new WorktreeRemoteResponse(false, null, null, null, false, false,
                                "Unknown action: " + request.getAction()));
            };
        }

        private CompletableFuture<WorktreeRemoteResponse> handleCreate(WorktreeRemoteRequest request) {
            return ensureRepo(defaultString(request.getRepoUrl()))
                    .thenCompose(repoRoot -> Git.fetchRef(repoRoot, defaultString(request.getBaseBranch(), "main"))
                            .thenCompose(ignored -> manager.createOwnerWorktree(defaultString(request.getSlug())).toCompletableFuture()))
                    .thenApply(result -> {
                        return new WorktreeRemoteResponse(
                                true,
                                result.getWorktreePath(),
                                result.getWorktreeBranch(),
                                result.getHeadCommit(),
                                result.isExisted(),
                                false,
                                null);
                    })
                    .exceptionally(error -> {
                        TEAM_LOGGER.exception(
                                "Remote worktree handler failed for action={}",
                                unwrap(error),
                                request.getAction());
                        return new WorktreeRemoteResponse(false, null, null, null, false, false, unwrap(error).getMessage());
                    });
        }

        private CompletableFuture<WorktreeRemoteResponse> handleRemove(WorktreeRemoteRequest request) {
            String worktreePath = defaultString(request.getWorktreePath());
            return Git.findCanonicalGitRoot(worktreePath)
                    .thenCompose(repoRoot -> {
                        if (repoRoot == null || repoRoot.isBlank()) {
                            return CompletableFuture.completedFuture(
                                    new WorktreeRemoteResponse(false, null, null, null, false, false,
                                            "Cannot find repo root for worktree"));
                        }
                        return manager.removeWorktree(worktreePath, repoRoot).toCompletableFuture()
                                .thenApply(ok -> new WorktreeRemoteResponse(Boolean.TRUE.equals(ok), null, null, null, false, false, null));
                    })
                    .exceptionally(error -> new WorktreeRemoteResponse(
                            false,
                            null,
                            null,
                            null,
                            false,
                            false,
                            unwrap(error).getMessage()));
        }

        private CompletableFuture<WorktreeRemoteResponse> handleExists(WorktreeRemoteRequest request) {
            String worktreePath = defaultString(request.getWorktreePath());
            return manager.getBackend().exists(worktreePath)
                    .thenApply(found -> new WorktreeRemoteResponse(true, null, null, null, false, Boolean.TRUE.equals(found), null))
                    .exceptionally(error -> new WorktreeRemoteResponse(
                            false,
                            null,
                            null,
                            null,
                            false,
                            false,
                            unwrap(error).getMessage()));
        }

        private CompletableFuture<String> ensureRepo(String repoUrl) {
            if (clonedRepos.containsKey(repoUrl)) {
                return CompletableFuture.completedFuture(clonedRepos.get(repoUrl));
            }

            String repoHash = sha256Prefix(repoUrl, 12);
            String localPath = AgentTeamPaths.getAgentTeamsHome().resolve("remote_repos").resolve(repoHash).toString();
            Path gitDir = Path.of(localPath).resolve(".git");
            if (Files.isDirectory(gitDir)) {
                clonedRepos.put(repoUrl, localPath);
                return CompletableFuture.completedFuture(localPath);
            }

            try {
                Files.createDirectories(Path.of(localPath).getParent());
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }

            TEAM_LOGGER.info("Cloning {} to {}", repoUrl, localPath);
            return Git.runGit(java.util.List.of("clone", "--depth=1", repoUrl, localPath), null, true)
                    .thenApply(ignored -> {
                        clonedRepos.put(repoUrl, localPath);
                        return localPath;
                    });
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sha256Prefix(String value, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.substring(0, Math.min(length, builder.length()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash repo URL", exception);
        }
    }
}
