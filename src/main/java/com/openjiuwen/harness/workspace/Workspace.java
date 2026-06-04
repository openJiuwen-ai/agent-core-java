/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Workspace schema descriptor and link manager for DeepAgents.
 *
 * <p>Mirrors Python's {@code Workspace} in
 * {@code openjiuwen.harness.workspace.workspace}.</p>
 */
public class Workspace {

    public static final String TEAM_LINKS_DIR = ".team";
    public static final String WORKTREE_LINKS_DIR = ".worktree";

    private String rootPath;
    private String language;
    private List<Map<String, Object>> directories;

    public Workspace() {
        this("./", null, "cn");
    }

    public Workspace(Path rootPath) {
        this(rootPath != null ? rootPath.toString() : "./", null, "cn");
    }

    public Workspace(String rootPath, String language) {
        this(rootPath, null, language);
    }

    public Workspace(String rootPath, List<Map<String, Object>> directories) {
        this(rootPath, directories, "cn");
    }

    public Workspace(String rootPath, List<Map<String, Object>> directories, String language) {
        this.rootPath = rootPath == null || rootPath.isBlank() ? "./" : rootPath;
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.directories = directories == null || directories.isEmpty()
                ? deepCopySchema(getWorkspaceSchema(this.language))
                : deepCopySchema(directories);
        supplementMissingDefaults();
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath == null || rootPath.isBlank() ? "./" : rootPath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language == null || language.isBlank() ? "cn" : language;
        supplementMissingDefaults();
    }

    @JsonProperty("directories")
    public List<Map<String, Object>> getDirectories() {
        return directories;
    }

    @JsonProperty("directories")
    public void setDirectories(List<Map<String, Object>> directories) {
        this.directories = directories == null || directories.isEmpty()
                ? deepCopySchema(getWorkspaceSchema(language))
                : deepCopySchema(directories);
        supplementMissingDefaults();
    }

    public Path root() {
        return Paths.get(rootPath).normalize();
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return root();
        }
        return root().resolve(relativePath).normalize();
    }

    public String getDirectory(String name) {
        return findDirectoryPath(directories, name);
    }

    public String getDirectory(WorkspaceNode node) {
        return node == null ? null : getDirectory(node.getValue());
    }

    @JsonIgnore
    public void setDirectory(Map<String, Object> node) {
        setDirectory(List.of(node));
    }

    @JsonIgnore
    public void setDirectory(List<Map<String, Object>> nodes) {
        if (nodes == null) {
            return;
        }
        for (Map<String, Object> node : nodes) {
            if (node == null) {
                continue;
            }
            String name = asString(node.get("name"));
            boolean replaced = false;
            for (int i = 0; i < directories.size(); i++) {
                if (Objects.equals(name, asString(directories.get(i).get("name")))) {
                    directories.set(i, deepCopyNode(node));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                directories.add(deepCopyNode(node));
            }
        }
    }

    public Path getNodePath(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return null;
        }
        for (Map<String, Object> node : directories) {
            if (nodeName.equals(node.get("name"))) {
                return root().resolve(asString(node.getOrDefault("path", nodeName))).normalize();
            }
        }
        return null;
    }

    public Path getNodePath(WorkspaceNode node) {
        return node == null ? null : getNodePath(node.getValue());
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

    public static List<Map<String, Object>> getDefaultDirectory(String language) {
        return deepCopySchema(getWorkspaceSchema(language));
    }

    public static List<Map<String, Object>> getWorkspaceSchema(String language) {
        String effectiveLanguage = language == null || language.isBlank() ? "cn" : language;
        boolean english = "en".equalsIgnoreCase(effectiveLanguage);

        List<Map<String, Object>> schema = new ArrayList<>();
        schema.add(fileNode("AGENT.md", "AGENT.md",
                english ? "Basic configuration and capabilities" : "基础配置和能力",
                english ? "This folder is home to your agent.\nFirst-run guidance lives here.\n"
                        : "智能体基础配置与能力说明。\n首次运行时请先阅读这里。\n"));
        schema.add(fileNode("SOUL.md", "SOUL.md",
                english ? "Soul and values" : "人格、性格和价值观",
                english ? "SOUL\nBe genuinely helpful. Have opinions when useful.\n"
                        : "灵魂\n保持真诚、有帮助，并在必要时表达判断。\n"));
        schema.add(fileNode("HEARTBEAT.md", "HEARTBEAT.md",
                english ? "Heartbeat tasks" : "心跳日志和状态记录",
                english ? "(No heartbeat content)\n" : "（无心跳内容）\n"));
        schema.add(fileNode("IDENTITY.md", "IDENTITY.md",
                english ? "Identity credentials" : "身份凭证",
                english ? "Identity notes for the current agent.\n" : "当前智能体的身份说明。\n"));
        schema.add(fileNode("USER.md", "USER.md",
                english ? "User information" : "用户信息",
                english ? "User profile and preferences.\n" : "用户资料与偏好。\n"));

        Map<String, Object> memory = dirNode("memory", "memory",
                english ? "Memory core module" : "记忆核心模块");
        children(memory).add(fileNode("MEMORY.md", "MEMORY.md",
                english ? "Long-term memory" : "长期记忆",
                english ? "Long-term memory for durable project facts.\n"
                        : "长期记忆，用于保存持久的重要事实。\n"));
        children(memory).add(dirNode("daily_memory", "daily_memory",
                english ? "Daily structured memory" : "每日结构化记忆"));
        schema.add(memory);

        schema.add(dirNode("todo", "todo", english ? "Todo items" : "待办事项"));
        schema.add(dirNode("messages", "messages", english ? "Message history" : "消息历史"));
        schema.add(dirNode("skills", "skills", english ? "Skills library" : "技能库"));
        schema.add(dirNode("agents", "agents", english ? "Sub-agents" : "子智能体"));
        return schema;
    }

    private void supplementMissingDefaults() {
        List<Map<String, Object>> defaults = getWorkspaceSchema(language);
        Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
        for (Map<String, Object> node : directories) {
            existing.put(asString(node.get("name")), node);
        }
        for (Map<String, Object> defaultNode : defaults) {
            String name = asString(defaultNode.get("name"));
            if (!existing.containsKey(name)) {
                directories.add(deepCopyNode(defaultNode));
            }
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
        if (!Files.exists(link) || !isDirectoryLink(link)) {
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.computeIfAbsent("children", ignored -> new ArrayList<Map<String, Object>>());
    }

    private static Map<String, Object> fileNode(String name, String path, String description, String defaultContent) {
        Map<String, Object> node = dirNode(name, path, description);
        node.put("is_file", true);
        node.put("default_content", defaultContent);
        return node;
    }

    private static Map<String, Object> dirNode(String name, String path, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name);
        node.put("path", path);
        node.put("description", description);
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    private static String findDirectoryPath(List<Map<String, Object>> nodes, String name) {
        if (nodes == null || name == null) {
            return null;
        }
        for (Map<String, Object> node : nodes) {
            if (name.equals(node.get("name"))) {
                return asString(node.get("path"));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            String child = findDirectoryPath(children, name);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> deepCopySchema(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> copies = new ArrayList<>();
        if (nodes == null) {
            return copies;
        }
        for (Map<String, Object> node : nodes) {
            copies.add(deepCopyNode(node));
        }
        return copies;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyNode(Map<String, Object> node) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if ("children".equals(entry.getKey()) && entry.getValue() instanceof List<?> children) {
                List<Map<String, Object>> childCopies = new ArrayList<>();
                for (Object child : children) {
                    if (child instanceof Map<?, ?> childMap) {
                        childCopies.add(deepCopyNode((Map<String, Object>) childMap));
                    }
                }
                copy.put("children", childCopies);
            } else {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        copy.putIfAbsent("children", new ArrayList<Map<String, Object>>());
        return copy;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
