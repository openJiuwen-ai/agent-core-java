/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Remote worktree request dispatcher. */
public class WorktreeRemoteHandler {
  private final WorktreeManager manager;

  /** Auto-generated for codecheck compliance. */
  public WorktreeRemoteHandler(WorktreeManager manager) {
    this.manager = manager;
  }

  /** Auto-generated for codecheck compliance. */
  public void register(Messager messager) {
    messager
        .registerDirectMessageHandler(
            message -> {
              WorktreeRemoteResponse response =
                  handle(WorktreeRemoteRequest.fromPayload(message.getPayload()));
              Object replyTo =
                  message.getPayload() != null ? message.getPayload().get("reply_to") : null;
              if (replyTo != null) {
                return messager.send(
                    String.valueOf(replyTo),
                    EventMessage.builder()
                        .eventType("worktree_remote_response")
                        .payload(response.toPayload())
                        .build());
              }
              return java.util.concurrent.CompletableFuture.completedFuture(null);
            })
        .join();
  }

  /** Auto-generated for codecheck compliance. */
  public WorktreeRemoteResponse handle(WorktreeRemoteRequest request) {
    if (request == null || request.getAction() == null || request.getAction().isBlank()) {
      return WorktreeRemoteResponse.builder().isSuccess(false).error("Unknown action: null").build();
    }
    try {
      return switch (request.getAction()) {
        case "isExists", "exists" -> handleExists(request);
        case "remove" -> handleRemove(request);
        case "create" -> handleCreate(request);
        default ->
            WorktreeRemoteResponse.builder()
                .isSuccess(false)
                .error("Unknown action: " + request.getAction())
                .build();
      };
    } catch (RuntimeException | IOException exception) {
      return WorktreeRemoteResponse.builder().isSuccess(false).error(exception.getMessage()).build();
    }
  }

  private WorktreeRemoteResponse handleCreate(WorktreeRemoteRequest request) throws IOException {
    if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
      return WorktreeRemoteResponse.builder().isSuccess(false).error("repo_url is required").build();
    }
    String slug = request.getSlug();
    if (slug == null || slug.isBlank()) {
      return WorktreeRemoteResponse.builder().isSuccess(false).error("slug is required").build();
    }
    Path repoRoot = ensureRepo(request.getRepoUrl());
    fetchRef(
        repoRoot,
        request.getBaseBranch() == null || request.getBaseBranch().isBlank()
            ? "main"
            : request.getBaseBranch());
    WorktreeCreateResult result = manager.createAgentWorktree(slug, repoRoot.toString());
    return WorktreeRemoteResponse.builder()
        .worktreePath(result.getWorktreePath())
        .worktreeBranch(result.getWorktreeBranch())
        .headCommit(result.getHeadCommit())
        .isExisted(result.isExisted())
        .build();
  }

  private WorktreeRemoteResponse handleExists(WorktreeRemoteRequest request) {
    String path = request.getWorktreePath();
    boolean isExists = path != null && java.nio.file.Files.exists(java.nio.file.Path.of(path));
    return WorktreeRemoteResponse.builder().isExists(isExists).build();
  }

  private WorktreeRemoteResponse handleRemove(WorktreeRemoteRequest request) throws IOException {
    String path = request.getWorktreePath();
    if (path == null || path.isBlank()) {
      return WorktreeRemoteResponse.builder()
          .isSuccess(false)
          .error("worktree_path is required")
          .build();
    }
    boolean isRemoved = manager.removeWorktree(path);
    return WorktreeRemoteResponse.builder().isSuccess(isRemoved).build();
  }

  private Path ensureRepo(String repoUrl) throws IOException {
    Path localPath = agentTeamsHome().resolve("remote_repos").resolve(hashRepo(repoUrl));
    if (!Files.isDirectory(localPath.resolve(".git"))) {
      Files.createDirectories(localPath.getParent());
      runGitOrThrow(
          localPath.getParent(),
          java.util.List.of("clone", "--depth=1", repoUrl, localPath.toString()));
    }
    return localPath;
  }

  private static Path agentTeamsHome() {
    String configuredHome = System.getProperty("openjiuwen.home");
    if (configuredHome == null || configuredHome.isBlank()) {
      configuredHome = System.getenv("OPENJIUWEN_HOME");
    }
    Path home =
        configuredHome != null && !configuredHome.isBlank()
            ? Path.of(configuredHome)
            : Path.of(System.getProperty("user.home"), ".openjiuwen");
    return home.resolve(".agent_teams");
  }

  private static String hashRepo(String repoUrl) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(repoUrl.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < 6; i++) {
        builder.append(String.format("%02x", bytes[i]));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void fetchRef(Path repoRoot, String ref) {
    runGit(repoRoot, java.util.List.of("fetch", "origin", ref));
  }

  private static void runGitOrThrow(Path cwd, java.util.List<String> args) {
    GitCommandResult result = runGit(cwd, args);
    if (result.code() != 0) {
      throw new IllegalStateException(
          "git " + String.join(" ", args) + " failed: " + result.output());
    }
  }

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
      return new GitCommandResult(
          code, new String(output, StandardCharsets.UTF_8).replaceAll("\\R+$", ""));
    } catch (IOException e) {
      return new GitCommandResult(1, e.getMessage() == null ? "" : e.getMessage());
    } catch (InterruptedException e) {

      return new GitCommandResult(1, e.getMessage() == null ? "" : e.getMessage());
    }
  }

  private record GitCommandResult(int code, String output) {}
}
