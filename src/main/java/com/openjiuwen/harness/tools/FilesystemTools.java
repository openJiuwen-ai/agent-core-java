/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local filesystem tool helpers and lightweight tool facades.
 *
 * <p>Mirrors Python's {@code ReadFileTool}, {@code WriteFileTool},
 * {@code EditFileTool}, {@code GlobTool}, {@code ListDirTool}, and
 * {@code GrepTool} in {@code openjiuwen/harness/tools/filesystem.py}.</p>
 */
public final class FilesystemTools {

    private FilesystemTools() {
    }

    public static Path resolveWorkspacePath(String workspaceRoot, String requestedPath) {
        Path root = Path.of(workspaceRoot == null || workspaceRoot.isBlank() ? "." : workspaceRoot)
                .toAbsolutePath()
                .normalize();
        Path resolved = root.resolve(requestedPath == null ? "" : requestedPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes workspace root");
        }
        return resolved;
    }

    /**
     * Mirrors Python's file read state in {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public record FileReadState(String path, String content) {
    }

    /**
     * Mirrors Python's read_file tool in {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public static class ReadFileTool extends AbstractHarnessTool {
        private final String workspaceRoot;

        public ReadFileTool(String workspaceRoot) {
            super(toolCard("read_file", "ReadFileTool", "Read a UTF-8 file from the workspace."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            Path path = resolveWorkspacePath(workspaceRoot, requiredString(inputs, "path"));
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return ToolOutput.success(Map.of("path", path.toString(), "content", content));
        }
    }

    /**
     * Mirrors Python's write_file tool in {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public static class WriteFileTool extends AbstractHarnessTool {
        private final String workspaceRoot;

        public WriteFileTool(String workspaceRoot) {
            super(toolCard("write_file", "WriteFileTool", "Write UTF-8 content into a workspace file."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            Path path = resolveWorkspacePath(workspaceRoot, requiredString(inputs, "path"));
            Files.createDirectories(path.getParent());
            String content = stringValue(inputs == null ? null : inputs.get("content"));
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return ToolOutput.success(Map.of("path", path.toString(), "bytes", content.getBytes(StandardCharsets.UTF_8).length));
        }
    }

    /**
     * Mirrors Python's edit_file string replacement path in
     * {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public static class EditFileTool extends AbstractHarnessTool {
        private final String workspaceRoot;

        public EditFileTool(String workspaceRoot) {
            super(toolCard("edit_file", "EditFileTool", "Replace text inside a workspace file."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            Path path = resolveWorkspacePath(workspaceRoot, requiredString(inputs, "path"));
            String oldText = requiredString(inputs, "old_text");
            String newText = stringValue(inputs == null ? null : inputs.get("new_text"));
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.contains(oldText)) {
                return ToolOutput.failure("old_text not found");
            }
            String updated = content.replace(oldText, newText);
            Files.writeString(path, updated, StandardCharsets.UTF_8);
            return ToolOutput.success(Map.of("path", path.toString(), "replacements", 1));
        }
    }

    /**
     * Mirrors Python's glob tool in {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public static class GlobTool extends AbstractHarnessTool {
        private final String workspaceRoot;

        public GlobTool(String workspaceRoot) {
            super(toolCard("glob", "GlobTool", "List files under the workspace matching a glob suffix."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            String pattern = requiredString(inputs, "pattern").replace("\\", "/");
            String suffix = pattern.startsWith("**/*") ? pattern.substring(4) : pattern.replace("*", "");
            Path root = resolveWorkspacePath(workspaceRoot, "");
            try (Stream<Path> stream = Files.walk(root)) {
                List<String> matches = stream.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .map(Path::toString)
                        .map(value -> value.replace("\\", "/"))
                        .filter(value -> suffix.isBlank() || value.endsWith(suffix))
                        .sorted()
                        .toList();
                return ToolOutput.success(Map.of("matches", matches));
            }
        }
    }

    /**
     * Mirrors Python's list_dir tool in {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public static class ListDirTool extends AbstractHarnessTool {
        private final String workspaceRoot;

        public ListDirTool(String workspaceRoot) {
            super(toolCard("list_dir", "ListDirTool", "List a workspace directory."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            Path path = resolveWorkspacePath(workspaceRoot, stringValue(inputs == null ? null : inputs.get("path")));
            try (Stream<Path> stream = Files.list(path)) {
                List<Map<String, Object>> entries = stream.sorted(Comparator.comparing(Path::toString))
                        .map(entry -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("name", entry.getFileName().toString());
                            item.put("path", entry.toString());
                            item.put("is_dir", Files.isDirectory(entry));
                            return item;
                        })
                        .toList();
                return ToolOutput.success(Map.of("entries", entries));
            }
        }
    }

    /**
     * Mirrors Python's grep tool in {@code openjiuwen/harness/tools/filesystem.py}.
     */
    public static class GrepTool extends AbstractHarnessTool {
        private final String workspaceRoot;

        public GrepTool(String workspaceRoot) {
            super(toolCard("grep", "GrepTool", "Search text files in the workspace."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            Pattern pattern = Pattern.compile(requiredString(inputs, "pattern"));
            Path root = resolveWorkspacePath(workspaceRoot, stringValue(inputs == null ? null : inputs.get("path")));
            try (Stream<Path> stream = Files.walk(root)) {
                List<String> matches = stream.filter(Files::isRegularFile)
                        .filter(path -> contains(path, pattern))
                        .map(Path::toString)
                        .sorted()
                        .toList();
                return ToolOutput.success(Map.of("matches", matches));
            }
        }

        private static boolean contains(Path path, Pattern pattern) {
            try {
                return pattern.matcher(Files.readString(path, StandardCharsets.UTF_8)).find();
            } catch (IOException ignored) {
                return false;
            }
        }
    }
}
