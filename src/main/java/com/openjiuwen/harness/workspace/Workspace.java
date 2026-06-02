/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Workspace schema descriptor and link manager for DeepAgents.
 *
 * <p>Mirrors Python's {@code Workspace} in
 * {@code openjiuwen.harness.workspace.workspace}.
 */
public class Workspace {

    public static final String TEAM_LINKS_DIR = ".team";
    public static final String WORKTREE_LINKS_DIR = ".worktree";

    private final String rootPath;
    private final String language;

    public Workspace() {
        this("./", "cn");
    }

    public Workspace(String rootPath, String language) {
        this.rootPath = (rootPath == null || rootPath.isBlank()) ? "./" : rootPath;
        this.language = (language == null || language.isBlank()) ? "cn" : language;
    }

    public String getRootPath() {
        return rootPath;
    }

    public String getLanguage() {
        return language;
    }

    public Path root() {
        return Paths.get(rootPath).normalize();
    }

    public Path getNodePath(WorkspaceNode node) {
        if (node == null) {
            return root();
        }
        return root().resolve(node.getValue()).normalize();
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return root();
        }
        return root().resolve(relativePath).normalize();
    }

    public Path linkTeam(String teamId, String targetPath) {
        return createNamedDirectoryLink(TEAM_LINKS_DIR, teamId, targetPath);
    }

    public boolean unlinkTeam(String teamId) {
        return removeNamedDirectoryLink(TEAM_LINKS_DIR, teamId);
    }

    public Path linkWorktree(String slug, String targetPath) {
        return createNamedDirectoryLink(WORKTREE_LINKS_DIR, slug, targetPath);
    }

    public boolean unlinkWorktree(String slug) {
        return removeNamedDirectoryLink(WORKTREE_LINKS_DIR, slug);
    }

    public List<Map.Entry<String, String>> listTeamLinks() {
        return listLinks(TEAM_LINKS_DIR);
    }

    public List<Map.Entry<String, String>> listWorktreeLinks() {
        return listLinks(WORKTREE_LINKS_DIR);
    }

    public boolean isDirectoryLink(Path entry) {
        if (entry == null || !Files.exists(entry)) {
            return false;
        }
        if (Files.isSymbolicLink(entry)) {
            return true;
        }
        return isWindows() && Files.isDirectory(entry);
    }

    public void createDirectoryLink(String targetPath, Path linkPath) throws IOException {
        try {
            Files.createSymbolicLink(linkPath, Paths.get(targetPath));
        } catch (IOException | UnsupportedOperationException exc) {
            if (!isWindows()) {
                throw exc;
            }
            createWindowsJunction(targetPath, linkPath.toString());
        }
    }

    public void createWindowsJunction(String targetPath, String linkPath) throws IOException {
        String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
        ProcessBuilder builder = new ProcessBuilder(
                systemRoot + "\\System32\\cmd.exe",
                "/c",
                "mklink",
                "/J",
                linkPath,
                targetPath);
        Process process = builder.redirectErrorStream(true).start();
        try {
            int exit = process.waitFor();
            if (exit != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IOException("Failed to create junction " + linkPath + " -> " + targetPath + ": " + output);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while creating junction", interrupted);
        }
    }

    private Path createNamedDirectoryLink(String subdir, String name, String targetPath) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("link name must be non-empty");
        }
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("target_path must be non-empty");
        }
        Path linkDir = ensureLinkDir(subdir);
        Path link = linkDir.resolve(name).normalize();
        if (!Files.exists(link)) {
            try {
                createDirectoryLink(Paths.get(targetPath).toAbsolutePath().normalize().toString(), link);
            } catch (IOException exc) {
                throw new IllegalStateException("Failed to create directory link " + link, exc);
            }
        }
        return link;
    }

    private boolean removeNamedDirectoryLink(String subdir, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Path link = root().resolve(subdir).resolve(name).normalize();
        if (!Files.exists(link)) {
            return false;
        }
        if (!isDirectoryLink(link)) {
            return false;
        }
        try {
            Files.delete(link);
            return true;
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to remove directory link " + link, exc);
        }
    }

    private List<Map.Entry<String, String>> listLinks(String subdir) {
        Path linkDir = root().resolve(subdir).normalize();
        if (!Files.isDirectory(linkDir)) {
            return List.of();
        }
        List<Map.Entry<String, String>> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(linkDir)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .filter(this::isDirectoryLink)
                    .forEach(path -> {
                        try {
                            result.add(new AbstractMap.SimpleImmutableEntry<>(
                                    path.getFileName().toString(),
                                    path.toRealPath().toString()));
                        } catch (IOException exc) {
                            result.add(new AbstractMap.SimpleImmutableEntry<>(
                                    path.getFileName().toString(),
                                    path.toAbsolutePath().normalize().toString()));
                        }
                    });
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to list workspace links under " + linkDir, exc);
        }
        return result;
    }

    private Path ensureLinkDir(String subdir) {
        Path linkDir = root().resolve(subdir).normalize();
        try {
            Files.createDirectories(linkDir);
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to create workspace link directory " + linkDir, exc);
        }
        return linkDir;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
