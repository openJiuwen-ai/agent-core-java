/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Git operations helper for auto-harness orchestrators.
 * <p>
 * Mirrors Python's {@code GitOperations} in
 * {@code openjiuwen/auto_harness/infra/git_operations.py}.
 */
public class GitOperations {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Mirrors Python's subprocess execution seam in
     * {@code openjiuwen/auto_harness/infra/git_operations.py}.
     */
    public interface CommandExecutor {
        CommandResult execute(List<String> command, String cwd, Map<String, String> env)
                throws IOException, InterruptedException;
    }

    /**
     * Mirrors Python's ``(returncode, output)`` git subprocess result in
     * {@code openjiuwen/auto_harness/infra/git_operations.py}.
     */
    public record CommandResult(int returnCode, String output) {}

    private String workspace;
    private final String remote;
    private final String baseBranch;
    private final String forkOwner;
    private final String upstreamOwner;
    private final String upstreamRepo;
    private final String gitcodeUsername;
    private final String gitcodeToken;
    private final String userName;
    private final String userEmail;
    private final Map<String, String> gitEnv;
    private final CommandExecutor executor;

    public GitOperations(String workspace) {
        this(workspace, "", "develop", "", "openJiuwen", "agent-core", "", "", "", "");
    }

    public GitOperations(String workspace, String remote, String gitcodeUsername, String gitcodeToken) {
        this(workspace, remote, "develop", "", "openJiuwen", "agent-core", gitcodeUsername, gitcodeToken, "", "");
    }

    public GitOperations(String workspace, String remote, String baseBranch, String forkOwner,
                         String upstreamOwner, String upstreamRepo, String gitcodeUsername,
                         String gitcodeToken, String userName, String userEmail) {
        this(workspace, remote, baseBranch, forkOwner, upstreamOwner, upstreamRepo,
                gitcodeUsername, gitcodeToken, userName, userEmail, null);
    }

    public GitOperations(String workspace, String remote, String baseBranch, String forkOwner,
                         String upstreamOwner, String upstreamRepo, String gitcodeUsername,
                         String gitcodeToken, String userName, String userEmail,
                         CommandExecutor executor) {
        this.workspace = workspace;
        this.remote = remote != null ? remote : "";
        this.baseBranch = baseBranch;
        this.forkOwner = forkOwner;
        this.upstreamOwner = upstreamOwner;
        this.upstreamRepo = upstreamRepo;
        this.gitcodeUsername = gitcodeUsername != null ? gitcodeUsername : "";
        String envToken = System.getenv().getOrDefault("GITCODE_ACCESS_TOKEN", "");
        this.gitcodeToken = gitcodeToken != null && !gitcodeToken.isBlank() ? gitcodeToken : envToken;
        this.userName = userName;
        this.userEmail = userEmail;
        this.gitEnv = GitAuth.buildGitAuthEnv(this.gitcodeUsername, this.gitcodeToken);
        this.executor = executor != null ? executor : new ProcessCommandExecutor();
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public GitResult git(String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String arg : args) {
            cmd.add(arg);
        }
        CommandResult result = executor.execute(cmd, workspace, gitEnv);
        return new GitResult(result.returnCode(), stripTrailing(result.output()));
    }

    public Map<String, Object> createBranch(String branchName) throws IOException, InterruptedException {
        GitResult result = git("checkout", "-b", branchName);
        return Map.of(
                "success", result.returnCode() == 0,
                "branch", branchName,
                "output", result.output());
    }

    public Map<String, List<String>> collectStatus() throws IOException, InterruptedException {
        GitResult result = git("status", "--porcelain", "--untracked-files=all");
        Map<String, List<String>> status = new LinkedHashMap<>();
        status.put("dirty_files", new ArrayList<>());
        status.put("tracked_modified_files", new ArrayList<>());
        status.put("untracked_files", new ArrayList<>());
        status.put("renamed_files", new ArrayList<>());
        if (result.returnCode() != 0 || result.output().isBlank()) {
            return status;
        }

        for (String line : result.output().split("\\R")) {
            if (line.length() < 4) {
                continue;
            }
            String marker = line.substring(0, 2);
            String path = line.substring(3).trim();
            if (path.isEmpty()) {
                continue;
            }
            if (path.contains(" -> ")) {
                path = path.split(" -> ", 2)[1].trim();
                status.get("renamed_files").add(normalize(path));
            }
            String normalized = normalize(path);
            status.get("dirty_files").add(normalized);
            if ("??".equals(marker)) {
                status.get("untracked_files").add(normalized);
            } else {
                status.get("tracked_modified_files").add(normalized);
            }
        }

        status.replaceAll((key, value) -> unique(value));
        return status;
    }

    public List<String> listDirtyFiles() throws IOException, InterruptedException {
        return collectStatus().get("dirty_files");
    }

    public String currentBranch() throws IOException, InterruptedException {
        return git("rev-parse", "--abbrev-ref", "HEAD").output().trim();
    }

    public String currentHead() throws IOException, InterruptedException {
        return git("rev-parse", "HEAD").output().trim();
    }

    public String diffStat(List<String> paths) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>(List.of("diff", "--stat"));
        if (paths != null && !paths.isEmpty()) {
            args.add("--");
            args.addAll(paths);
        }
        return git(args.toArray(String[]::new)).output().trim();
    }

    public List<String> diffNameOnly(String revision) throws IOException, InterruptedException {
        GitResult result = git("diff", "--name-only", revision != null ? revision : "HEAD");
        List<String> files = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            String normalized = normalize(line.trim());
            if (!normalized.isEmpty()) {
                files.add(normalized);
            }
        }
        return unique(files);
    }

    public String statusPorcelain() throws IOException, InterruptedException {
        return stripTrailing(git("status", "--porcelain", "--untracked-files=all").output());
    }

    public String showLastCommitStat() throws IOException, InterruptedException {
        return git("show", "--stat", "--format=fuller", "-1").output().trim();
    }

    public boolean discardWorktreeChanges() throws IOException, InterruptedException {
        return git("checkout", ".").returnCode() == 0;
    }

    public String diffAgainst(String revision) throws IOException, InterruptedException {
        return git("diff", revision).output();
    }

    public Map<String, Object> push(String branchName) throws IOException, InterruptedException {
        GitResult result = git("push", "-u", remote, branchName);
        return Map.of(
                "success", result.returnCode() == 0,
                "output", result.output());
    }

    public Map<String, Object> createPr(String title, String body, String headBranch)
            throws IOException, InterruptedException {
        String owner = urlEncode(upstreamOwner);
        String repo = urlEncode(upstreamRepo);
        String url = "https://api.gitcode.com/api/v5/repos/"
                + owner + "/" + repo + "/pulls?access_token=" + urlEncode(gitcodeToken);
        String payload = OBJECT_MAPPER.writeValueAsString(Map.of(
                "title", title != null ? title : "",
                "head", (forkOwner != null ? forkOwner : "") + ":" + (headBranch != null ? headBranch : ""),
                "base", baseBranch != null ? baseBranch : "",
                "body", body != null ? body : ""
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> data = response.body() == null || response.body().isBlank()
                    ? Map.of()
                    : OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
            Object htmlUrl = data.get("html_url");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Map.of("success", true, "pr_url", htmlUrl != null ? String.valueOf(htmlUrl) : "");
            }
            return Map.of(
                    "success", false,
                    "error", data.getOrDefault("message", response.body() != null ? response.body() : "")
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (IOException | RuntimeException e) {
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<String> unique(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String stripTrailing(String value) {
        return value == null ? "" : value.stripTrailing();
    }

    /**
     * Mirrors Python's subprocess-backed git invocation path in
     * {@code openjiuwen/auto_harness/infra/git_operations.py}.
     */
    private static final class ProcessCommandExecutor implements CommandExecutor {
        @Override
        public CommandResult execute(List<String> command, String cwd, Map<String, String> env)
                throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(cwd));
            pb.environment().putAll(env);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (!first) {
                        out.append('\n');
                    }
                    out.append(line);
                    first = false;
                }
                int code = proc.waitFor();
                return new CommandResult(code, out.toString());
            }
        }
    }

    /**
     * Mirrors Python's normalized git command result in
     * {@code openjiuwen/auto_harness/infra/git_operations.py}.
     */
    public record GitResult(int returnCode, String output) {}
}
