/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
    public static final int MAX_HISTORY_PER_FILE = 100;
    private static final Object HISTORY_LOCK = new Object();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, List<LinkedHashMap<String, Object>>>> HISTORY_TYPE =
            new TypeReference<>() {
            };

    private FilesystemTools() {
    }

    @FunctionalInterface
    interface FileContentReader {
        String read(Path path) throws IOException;
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

    static void appendOpHistory(String historyPath, String filePath, String action,
                                String oldContent, String newContent) {
        appendOpHistory(Path.of(historyPath), filePath, action, oldContent, newContent);
    }

    static void appendOpHistory(Path historyPath, String filePath, String action,
                                String oldContent, String newContent) {
        try {
            synchronized (HISTORY_LOCK) {
                LinkedHashMap<String, List<LinkedHashMap<String, Object>>> history = readHistory(historyPath);
                LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                entry.put("action", action);
                entry.put("timestamp", Instant.now().toString());
                entry.put("old_content", oldContent);
                entry.put("new_content", newContent);

                List<LinkedHashMap<String, Object>> entries =
                        history.computeIfAbsent(filePath, ignored -> new ArrayList<>());
                entries.add(entry);
                if (entries.size() > MAX_HISTORY_PER_FILE) {
                    history.put(filePath, new ArrayList<>(entries.subList(entries.size() - MAX_HISTORY_PER_FILE,
                            entries.size())));
                }
                writeHistory(historyPath, history);
            }
        } catch (Exception ignored) {
            // Mirrors Python: history persistence failures are logged there, never raised to callers.
        }
    }

    static List<String> parseRmTargets(String command) {
        String stripped = command == null ? "" : command.trim();
        if (stripped.isEmpty() || containsAny(stripped, "|", ";", "&&", "||", "\n", "`", "$(")) {
            return List.of();
        }

        List<String> parts = splitCommand(stripped);
        if (parts.isEmpty() || !"rm".equals(lastPathSegment(parts.get(0)))) {
            return List.of();
        }

        List<String> targets = new ArrayList<>();
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            if (part.startsWith("-")) {
                if (part.indexOf('r') >= 0 || part.indexOf('R') >= 0) {
                    return List.of();
                }
                continue;
            }
            if (containsAny(part, "*", "?", "[", "{")) {
                continue;
            }
            targets.add(part);
        }
        return targets;
    }

    static List<String> parsePsRemoveTargets(String command) {
        String stripped = command == null ? "" : command.trim();
        if (stripped.isEmpty() || containsAny(stripped, "|", ";", "\n", "`")) {
            return List.of();
        }

        String lower = stripped.toLowerCase();
        String matchedAlias = null;
        for (String alias : List.of("remove-item", "ri", "rm", "del", "erase")) {
            if (lower.equals(alias) || lower.startsWith(alias + " ")) {
                matchedAlias = alias;
                break;
            }
        }
        if (matchedAlias == null) {
            return List.of();
        }

        String rest = stripped.substring(matchedAlias.length()).trim();
        if (Pattern.compile("-Recurse\\b", Pattern.CASE_INSENSITIVE).matcher(rest).find()) {
            return List.of();
        }
        rest = rest.replaceAll("(?i)-(?:Force|WhatIf|Confirm|Verbose)\\b", "").trim();
        rest = rest.replaceAll("(?i)-ErrorAction\\s+\\S+", "").trim();

        Matcher pathMatcher = Pattern.compile("(?i)-(?:Path|LiteralPath)\\s+([\"']?)(.+?)\\1(?:\\s|$)")
                .matcher(rest);
        if (pathMatcher.find()) {
            String path = pathMatcher.group(2).trim();
            return containsAny(path, "*", "?", "[") ? List.of() : List.of(path);
        }

        List<String> targets = new ArrayList<>();
        for (String token : splitCommand(rest)) {
            if (token.startsWith("-")) {
                continue;
            }
            if (containsAny(token, "*", "?", "[")) {
                continue;
            }
            targets.add(token);
        }
        return targets;
    }

    static void recordRmTargetsBeforeDeletion(Path historyPath, List<String> filePaths,
                                              FileContentReader contentReader) {
        for (String rawPath : filePaths) {
            try {
                Path path = Path.of(rawPath).toAbsolutePath().normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                Path realPath = path.toRealPath();
                appendOpHistory(historyPath, realPath.toString(), "delete", contentReader.read(realPath), null);
            } catch (Exception ignored) {
                // Mirrors Python: per-target failures are skipped and do not abort command execution.
            }
        }
    }

    static void detectAndRecordDeletions(Path historyPath) {
        try {
            synchronized (HISTORY_LOCK) {
                if (!Files.exists(historyPath)) {
                    return;
                }
                LinkedHashMap<String, List<LinkedHashMap<String, Object>>> history = readHistory(historyPath);
                boolean changed = false;
                for (Map.Entry<String, List<LinkedHashMap<String, Object>>> item : history.entrySet()) {
                    List<LinkedHashMap<String, Object>> entries = item.getValue();
                    if (entries == null || entries.isEmpty()) {
                        continue;
                    }
                    LinkedHashMap<String, Object> last = entries.get(entries.size() - 1);
                    if ("delete".equals(last.get("action")) || exists(item.getKey())) {
                        continue;
                    }
                    LinkedHashMap<String, Object> deleteEntry = new LinkedHashMap<>();
                    deleteEntry.put("action", "delete");
                    deleteEntry.put("timestamp", Instant.now().toString());
                    deleteEntry.put("old_content", last.get("new_content"));
                    deleteEntry.put("new_content", null);
                    entries.add(deleteEntry);
                    if (entries.size() > MAX_HISTORY_PER_FILE) {
                        history.put(item.getKey(), new ArrayList<>(entries.subList(entries.size() - MAX_HISTORY_PER_FILE,
                                entries.size())));
                    }
                    changed = true;
                }
                if (changed) {
                    writeHistory(historyPath, history);
                }
            }
        } catch (Exception ignored) {
            // Mirrors Python: corrupted history files and IO failures are warning-only.
        }
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
        private final boolean enableImageMultimodal;

        public ReadFileTool(String workspaceRoot) {
            this(workspaceRoot, true);
        }

        public ReadFileTool(String workspaceRoot, boolean enableImageMultimodal) {
            super(toolCard("read_file", "ReadFileTool", "Read a UTF-8 file from the workspace."));
            this.workspaceRoot = workspaceRoot;
            this.enableImageMultimodal = enableImageMultimodal;
        }

        public boolean isEnableImageMultimodal() {
            return enableImageMultimodal;
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
            super(toolCard("list_files", "ListDirTool", "List a workspace directory."));
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
        private static final int MAX_COLUMNS = 500;
        private static final List<String> VCS_DIRECTORIES_TO_EXCLUDE =
                List.of(".git", ".svn", ".hg", ".bzr", ".jj", ".sl");

        private final String workspaceRoot;

        public GrepTool(String workspaceRoot) {
            super(toolCard("grep", "GrepTool", "Search text files in the workspace."));
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            if (!hasRipgrep() && isWindows()) {
                if (inputs != null && inputs.get("type") != null) {
                    return ToolOutput.failure("type filter requires ripgrep (rg) to be installed");
                }
                if (boolValue(inputs == null ? null : inputs.get("multiline"), false)) {
                    return ToolOutput.failure("multiline search requires ripgrep (rg) to be installed");
                }
            }
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

        public String buildSelectStringCommand(String pattern,
                                               Path path,
                                               String glob,
                                               String outputMode,
                                               Integer contextBefore,
                                               Integer contextAfter,
                                               Integer contextC,
                                               Integer context,
                                               boolean caseInsensitive) {
            List<String> expandedGlobs = new ArrayList<>();
            Pattern bracePattern = Pattern.compile("^(.*)\\{([^}]+)}(.*)$");
            for (String globPattern : splitGlobPatterns(glob)) {
                Matcher matcher = bracePattern.matcher(globPattern);
                if (matcher.matches()) {
                    for (String alternative : matcher.group(2).split(",")) {
                        expandedGlobs.add(matcher.group(1) + alternative + matcher.group(3));
                    }
                } else {
                    expandedGlobs.add(globPattern);
                }
            }

            Integer effectiveContext = context != null ? context : contextC;
            int before = effectiveContext != null ? effectiveContext : (contextBefore == null ? 0 : contextBefore);
            int after = effectiveContext != null ? effectiveContext : (contextAfter == null ? 0 : contextAfter);

            String vcsAlternatives = String.join("|", VCS_DIRECTORIES_TO_EXCLUDE.stream()
                    .map(directory -> directory.replace(".", "\\."))
                    .toList());
            String vcsPattern = quotePowerShell("(\\\\|/)(" + vcsAlternatives + ")(\\\\|/|$)");

            List<String> pipeline = new ArrayList<>();
            if (Files.isRegularFile(path)) {
                pipeline.add("Get-Item -LiteralPath " + quotePowerShell(path.toString()));
            } else {
                pipeline.add("Get-ChildItem -LiteralPath " + quotePowerShell(path.toString()) + " -Recurse -File");
                pipeline.add("Where-Object { $_.FullName -notmatch " + vcsPattern + " }");
            }

            if (!expandedGlobs.isEmpty() && !Files.isRegularFile(path)) {
                List<String> conditions = expandedGlobs.stream()
                        .map(globPattern -> "$_.Name -like " + quotePowerShell(globPattern))
                        .toList();
                pipeline.add("Where-Object { " + String.join(" -or ", conditions) + " }");
            }

            String caseFlag = caseInsensitive ? "" : " -CaseSensitive";
            String contextFlag = "content".equals(outputMode) && (before > 0 || after > 0)
                    ? " -Context " + before + "," + after
                    : "";
            pipeline.add("Select-String -Pattern " + quotePowerShell(pattern) + caseFlag + contextFlag);

            if ("files_with_matches".equals(outputMode)) {
                pipeline.add("Select-Object -ExpandProperty Path -Unique");
            } else if ("count".equals(outputMode)) {
                pipeline.add("Group-Object Path | ForEach-Object { \"$($_.Name):$($_.Count)\" }");
            } else if (before > 0 || after > 0) {
                pipeline.add(
                        "ForEach-Object {"
                                + " $m=$_; $p=$m.Context.PreContext.Length;"
                                + " for($i=0;$i-lt$p;$i++){ \"$($m.Path):$([int]$m.LineNumber-$p+$i):$($m.Context.PreContext[$i])\" };"
                                + " \"$($m.Path):$($m.LineNumber):$($m.Line)\";"
                                + " for($i=0;$i-lt$m.Context.PostContext.Length;$i++)"
                                + "{ \"$($m.Path):$([int]$m.LineNumber+1+$i):$($m.Context.PostContext[$i])\" }"
                                + " }");
            } else {
                pipeline.add("ForEach-Object { \"$($_.Path):$($_.LineNumber):$($_.Line)\" }");
            }

            return "$ErrorActionPreference='SilentlyContinue'; " + String.join(" | ", pipeline);
        }

        protected boolean hasRipgrep() {
            return commandOnPath("rg");
        }

        protected boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase().contains("win");
        }

        private static boolean contains(Path path, Pattern pattern) {
            try {
                return pattern.matcher(Files.readString(path, StandardCharsets.UTF_8)).find();
            } catch (IOException ignored) {
                return false;
            }
        }

        private static List<String> splitGlobPatterns(String globValue) {
            if (globValue == null || globValue.isBlank()) {
                return List.of();
            }
            List<String> patterns = new ArrayList<>();
            for (String rawPattern : globValue.split("\\s+")) {
                if (rawPattern.contains("{") && rawPattern.contains("}")) {
                    patterns.add(rawPattern);
                } else {
                    for (String part : rawPattern.split(",")) {
                        if (!part.isBlank()) {
                            patterns.add(part);
                        }
                    }
                }
            }
            return patterns;
        }

        private static String quotePowerShell(String value) {
            return "'" + String.valueOf(value).replace("'", "''") + "'";
        }

        private static boolean commandOnPath(String command) {
            String path = System.getenv("PATH");
            if (path == null || path.isBlank()) {
                return false;
            }
            String[] extensions = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? new String[]{"", ".exe", ".cmd", ".bat"}
                    : new String[]{""};
            for (String directory : path.split(Pattern.quote(System.getProperty("path.separator")))) {
                for (String extension : extensions) {
                    if (Files.isRegularFile(Path.of(directory, command + extension))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static LinkedHashMap<String, List<LinkedHashMap<String, Object>>> readHistory(Path historyPath)
            throws IOException {
        if (!Files.exists(historyPath)) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(historyPath.toFile(), HISTORY_TYPE);
    }

    private static void writeHistory(Path historyPath,
                                     LinkedHashMap<String, List<LinkedHashMap<String, Object>>> history)
            throws IOException {
        Path parent = historyPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Path.of(historyPath.toString() + ".tmp");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), history);
        Files.move(tmp, historyPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean exists(String path) {
        try {
            return Files.exists(Path.of(path));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String lastPathSegment(String commandName) {
        int slash = Math.max(commandName.lastIndexOf('/'), commandName.lastIndexOf('\\'));
        return slash >= 0 ? commandName.substring(slash + 1) : commandName;
    }

    private static List<String> splitCommand(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (quote != 0) {
            return List.of();
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
