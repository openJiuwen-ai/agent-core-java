/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Worktree manager for auto-harness.
 * <p>
 * Mirrors Python's {@code WorktreeManager} in {@code openjiuwen.auto_harness.infra.worktree_manager}.
 * <p>
 * This minimal Java port keeps the repository path and slug helpers and provides
 * a basic git command wrapper for worktree-related operations.
 */
public class WorktreeManager {

    private final AutoHarnessConfig config;

    public WorktreeManager(AutoHarnessConfig config) {
        this.config = config;
    }

    private static String slugify(String topic) {
        String slug = topic == null ? "" : topic.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return slug.isBlank() ? "task" : slug;
    }

    public String baseRepo() {
        if (config.getLocalRepo() != null && !config.getLocalRepo().isBlank()) {
            return Path.of(config.getLocalRepo()).toAbsolutePath().normalize().toString();
        }
        return config.getCacheRepoDir();
    }

    public String worktreeNameFor(String topic) {
        return slugify(topic);
    }

    public GitResult runGit(String cwd, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String arg : args) {
            cmd.add(arg);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new java.io.File(cwd));
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
