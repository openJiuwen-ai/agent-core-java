/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors Python's {@code uri_to_file_path} and {@code filter_git_ignored_locations} in
 * {@code openjiuwen/harness/lsp/core/utils/git_ignore.py}.
 */
public final class GitIgnoreFilter {

    private static final Logger LOGGER = Logger.getLogger(GitIgnoreFilter.class.getName());
    private static final int BATCH_SIZE = 50;
    private static final long TIMEOUT_MILLIS = 5_000L;

    private GitIgnoreFilter() {
    }

    public static String uriToFilePath(String uri) {
        return FileUriUtils.fileUriToPath(uri);
    }

    public static List<Map<String, Object>> filterGitIgnoredLocations(List<Map<String, Object>> locations, String cwd) {
        return filterGitIgnoredLocations(locations, cwd, GitIgnoreFilter::runGitCheckIgnore);
    }

    static List<Map<String, Object>> filterGitIgnoredLocations(
            List<Map<String, Object>> locations,
            String cwd,
            GitCheckIgnoreRunner runner
    ) {
        if (locations == null || locations.isEmpty()) {
            return locations == null ? List.of() : locations;
        }

        Map<String, String> uriToPath = new LinkedHashMap<>();
        for (Map<String, Object> location : locations) {
            String uri = extractUriForCollection(location);
            if (uri != null && !uriToPath.containsKey(uri)) {
                uriToPath.put(uri, uriToFilePath(uri));
            }
        }

        List<String> uniquePaths = new ArrayList<>(new LinkedHashSet<>(uriToPath.values()));
        if (uniquePaths.isEmpty()) {
            return locations;
        }

        Set<String> ignoredPaths = new LinkedHashSet<>();
        for (int index = 0; index < uniquePaths.size(); index += BATCH_SIZE) {
            List<String> batch = uniquePaths.subList(index, Math.min(index + BATCH_SIZE, uniquePaths.size()));
            try {
                ignoredPaths.addAll(runner.checkIgnore(batch, cwd));
            } catch (IOException | TimeoutException exception) {
                LOGGER.log(Level.FINE, "git check-ignore batch failed (ignored): {0}", exception.toString());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.FINE, "git check-ignore batch failed (ignored): {0}", exception.toString());
            }
        }

        if (ignoredPaths.isEmpty()) {
            return locations;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> location : locations) {
            String uri = extractUriForFilter(location);
            if (uri == null) {
                filtered.add(location);
                continue;
            }
            String filePath = uriToPath.getOrDefault(uri, "");
            if (!ignoredPaths.contains(filePath)) {
                filtered.add(location);
            }
        }
        return filtered;
    }

    private static Set<String> runGitCheckIgnore(List<String> batch, String cwd)
            throws IOException, InterruptedException, TimeoutException {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "check-ignore", "--stdin");
        if (cwd != null && !cwd.isBlank()) {
            processBuilder.directory(Path.of(cwd).toFile());
        }
        Process process = processBuilder.start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(String.join("\n", batch).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        boolean finished = process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("git check-ignore timed out");
        }

        byte[] stdoutBytes = process.getInputStream().readAllBytes();
        process.getErrorStream().readAllBytes();
        if (process.exitValue() != 0 || stdoutBytes.length == 0) {
            return Set.of();
        }

        Set<String> ignoredPaths = new LinkedHashSet<>();
        String stdout = new String(stdoutBytes, StandardCharsets.UTF_8);
        for (String line : stdout.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                ignoredPaths.add(trimmed);
            }
        }
        return ignoredPaths;
    }

    private static String extractUriForCollection(Map<String, Object> location) {
        if (location == null) {
            return null;
        }
        Object uri = location.get("uri");
        if (uri instanceof String value && !value.isBlank()) {
            return value;
        }
        Object targetUri = location.get("targetUri");
        if (targetUri instanceof String value && !value.isBlank()) {
            return value;
        }
        Object nestedLocation = location.get("location");
        if (nestedLocation instanceof Map<?, ?> nestedMap) {
            Object nestedUri = nestedMap.get("uri");
            if (nestedUri instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String extractUriForFilter(Map<String, Object> location) {
        if (location == null) {
            return null;
        }
        Object uri = location.get("uri");
        if (uri instanceof String value && !value.isBlank()) {
            return value;
        }
        Object targetUri = location.get("targetUri");
        if (targetUri instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    @FunctionalInterface
    interface GitCheckIgnoreRunner {
        Set<String> checkIgnore(List<String> batch, String cwd) throws IOException, InterruptedException, TimeoutException;
    }
}
