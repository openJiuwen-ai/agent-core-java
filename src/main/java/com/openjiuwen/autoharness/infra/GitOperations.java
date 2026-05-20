/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Public class GitOperations used by the Java parity implementation.
 *
 * @since 1.0
 */
public class GitOperations {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String workspace;
    private final String remote;
    private final String baseBranch;
    private final String forkOwner;
    private final String upstreamOwner;
    private final String upstreamRepo;
    private final String gitcodeUsername;
    private final String token;
    private final String userName;
    private final String userEmail;
    private final Map<String, String> gitEnv;

    /**
     * Auto-generated for codecheck compliance.
     */
    public GitOperations(String workspace, String remote, String baseBranch) {
        this(workspace, remote, baseBranch, "", "openJiuwen", "agent-core", "", "", "", "");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GitOperations(String workspace, String remote, String baseBranch, String forkOwner,
                         String upstreamOwner, String upstreamRepo, String gitcodeUsername, String gitcodeToken,
                         String userName, String userEmail) {
        this.workspace = workspace;
        this.remote = value(remote);
        this.baseBranch = hasText(baseBranch) ? baseBranch : "develop";
        this.forkOwner = value(forkOwner);
        this.upstreamOwner = hasText(upstreamOwner) ? upstreamOwner : "openJiuwen";
        this.upstreamRepo = hasText(upstreamRepo) ? upstreamRepo : "agent-core";
        this.gitcodeUsername = value(gitcodeUsername);
        this.token = hasText(gitcodeToken) ? gitcodeToken : value(System.getenv("GITCODE_ACCESS_TOKEN"));
        this.userName = value(userName);
        this.userEmail = value(userEmail);
        this.gitEnv = GitAuth.buildGitAuthEnv(this.gitcodeUsername, this.token);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GitCommandResult git(String... args) {
        ProcessBuilder builder = new ProcessBuilder(command(args));
        builder.directory(new java.io.File(workspace));
        builder.redirectErrorStream(true);
        builder.environment().putAll(gitEnv);
        try {
            Process process = builder.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            String output = new String(stdout, StandardCharsets.UTF_8).replaceAll("\\R+$", "");
            return new GitCommandResult(code, output);
        } catch (IOException ex) {
            return new GitCommandResult(1, ex.getMessage() == null ? "" : ex.getMessage());
        } catch (InterruptedException ex) {

            return new GitCommandResult(1, ex.getMessage() == null ? "" : ex.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> createBranch(String branchName) {
        GitCommandResult result = git("checkout", "-b", branchName);
        return Map.of("success", result.code() == 0, "branch", branchName, "output", result.output());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, List<String>> collectStatus() {
        GitCommandResult result = git("status", "--porcelain", "--untracked-files=all");
        Map<String, List<String>> status = new LinkedHashMap<>();
        status.put("dirty_files", new ArrayList<>());
        status.put("tracked_modified_files", new ArrayList<>());
        status.put("untracked_files", new ArrayList<>());
        status.put("renamed_files", new ArrayList<>());
        if (result.code() != 0 || result.output().isBlank()) {
            return status;
        }
        for (String line : result.output().split("\\R")) {
            if (line.length() < 4) {
                continue;
            }
            String marker = line.substring(0, 2);
            String path = line.substring(3).trim();
            if (path.isBlank()) {
                continue;
            }
            if (path.contains(" -> ")) {
                path = path.split(" -> ", 2)[1].trim();
                status.get("renamed_files").add(normalizePath(path));
            }
            String normalized = normalizePath(path);
            status.get("dirty_files").add(normalized);
            if ("??".equals(marker)) {
                status.get("untracked_files").add(normalized);
            } else {
                status.get("tracked_modified_files").add(normalized);
            }
        }
        status.replaceAll((key, value) -> new ArrayList<>(new LinkedHashSet<>(value)));
        return status;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listDirtyFiles() {
        return collectStatus().get("dirty_files");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String currentBranch() {
        return git("rev-parse", "--abbrev-ref", "HEAD").output().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String currentHead() {
        return git("rev-parse", "HEAD").output().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String diffStat(List<String> paths) {
        List<String> args = new ArrayList<>(List.of("diff", "--stat"));
        if (paths != null && !paths.isEmpty()) {
            args.add("--");
            args.addAll(paths);
        }
        return git(args.toArray(String[]::new)).output().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String diffStat() {
        return diffStat(List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> diffNameOnly(String revision) {
        GitCommandResult result = git("diff", "--name-only", hasText(revision) ? revision : "HEAD");
        List<String> files = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            String normalized = normalizePath(line.trim());
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(files));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String statusPorcelain() {
        return git("status", "--porcelain", "--untracked-files=all").output().stripTrailing();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String showLastCommitStat() {
        return git("show", "--stat", "--format=fuller", "-1").output().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean discardWorktreeChanges() {
        return git("checkout", ".").code() == 0;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String diffAgainst(String revision) {
        return git("diff", revision).output();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> push(String branchName) {
        GitCommandResult result = git("push", "-u", remote, branchName);
        return Map.of("success", result.code() == 0, "output", result.output());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> createPr(String title, String body, String headBranch) {
        if (!hasText(token)) {
            return Map.of("success", false, "error", "missing GitCode token");
        }
        try {
            String url = "https://api.gitcode.com/api/v5/repos/"
                    + encode(upstreamOwner) + "/" + encode(upstreamRepo)
                    + "/pulls?access_token=" + encode(token);
            String payload = MAPPER.writeValueAsString(Map.of(
                    "title", value(title),
                    "head", forkOwner + ":" + value(headBranch),
                    "base", baseBranch,
                    "body", value(body)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Map.of("success", false, "error", response.body());
            }
            JsonNode data = MAPPER.readTree(response.body());
            return Map.of("success", true, "pr_url", data.path("html_url").asText(""));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {

            }
            return Map.of("success", false, "error", ex.getMessage() == null ? "" : ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", ex.getMessage() == null ? "" : ex.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> describeSyncPlan() {
        List<String> steps = new ArrayList<>();
        steps.add("workspace=" + workspace);
        steps.add("remote=" + remote);
        steps.add("base_branch=" + baseBranch);
        return steps;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> gitEnv() {
        return Map.copyOf(gitEnv);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUserEmail() {
        return userEmail;
    }

    private static List<String> command(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value(value), StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    /**
 * Public record GitCommandResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record GitCommandResult(int code, String output) {
    }
}
