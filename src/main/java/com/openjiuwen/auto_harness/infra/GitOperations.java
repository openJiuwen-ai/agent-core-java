/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Git operations helper for auto-harness.
 * <p>
 * Mirrors Python's {@code GitOperations} in {@code openjiuwen.auto_harness.infra.git_operations}.
 * <p>
 * This Java port is intentionally minimal and focuses on command execution and branch helpers.
 */
public class GitOperations {

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

    public GitOperations(String workspace) {
        this(workspace, "", "develop", "", "openJiuwen", "agent-core", "", "", "", "");
    }

    public GitOperations(String workspace, String remote, String baseBranch, String forkOwner,
                         String upstreamOwner, String upstreamRepo, String gitcodeUsername,
                         String gitcodeToken, String userName, String userEmail) {
        this.workspace = workspace;
        this.remote = remote;
        this.baseBranch = baseBranch;
        this.forkOwner = forkOwner;
        this.upstreamOwner = upstreamOwner;
        this.upstreamRepo = upstreamRepo;
        this.gitcodeUsername = gitcodeUsername;
        this.gitcodeToken = gitcodeToken;
        this.userName = userName;
        this.userEmail = userEmail;
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
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new java.io.File(workspace));
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
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
            return new GitResult(code, out.toString());
        }
    }

    public record GitResult(int returnCode, String output) {}
}
