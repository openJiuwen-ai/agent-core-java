/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.FileSystemItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Repository search tool with Java-side fallback behavior for tests and local execution.
 *
 * <p>Mirrors Python's filesystem search behaviors and Select-String fallback in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class GrepTool extends AbstractHarnessTool {

    private static final int DEFAULT_HEAD_LIMIT = 250;
    private static final String[] VCS_DIRECTORIES_TO_EXCLUDE = {".git", ".svn", ".hg", ".bzr", ".jj", ".sl"};

    public GrepTool(SysOperation sysOperation) {
        super(toolCard("harness.grep", "grep", "Search files under a path using a textual pattern."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.getOrDefault("path", "."));
        String pattern = stringValue(inputs.get("pattern"));
        if (pattern.isBlank()) {
            return new ToolOutput(false, null, "pattern is required");
        }
        if (inputs.containsKey("type") && !stringValue(inputs.get("type")).isBlank()) {
            return new ToolOutput(false, null, "type filter requires ripgrep (rg) to be installed");
        }
        if (asBoolean(inputs.get("multiline"), false)) {
            return new ToolOutput(false, null, "multiline search requires ripgrep (rg) to be installed");
        }

        String outputMode = stringValue(inputs.getOrDefault("output_mode", "content"));
        if (!List.of("content", "files_with_matches", "count").contains(outputMode)) {
            return new ToolOutput(false, null, "output_mode must be one of: content, files_with_matches, count");
        }

        try {
            return new ToolOutput(true, searchWithJava(path, pattern, inputs, outputMode), null);
        } catch (IOException e) {
            return searchViaSysOperation(path, pattern);
        } catch (RuntimeException e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }

    String buildSelectStringCommand(Map<String, Object> inputs) {
        return buildSelectStringCommand(
                stringValue(inputs.get("pattern")),
                stringValue(inputs.getOrDefault("path", ".")),
                stringValue(inputs.get("glob")),
                stringValue(inputs.getOrDefault("output_mode", "content")),
                asInteger(inputs.get("-B")),
                asInteger(inputs.get("-A")),
                asInteger(inputs.get("-C")),
                asInteger(inputs.get("context")),
                asBoolean(inputs.getOrDefault("ignore_case", inputs.get("-i")), false)
        );
    }

    String buildSelectStringCommand(
            String pattern,
            String path,
            String glob,
            String outputMode,
            Integer contextBefore,
            Integer contextAfter,
            Integer contextC,
            Integer context,
            boolean caseInsensitive) {
        List<String> expandedGlobs = expandGlobPatterns(splitGlobPatterns(glob));
        Integer effectiveC = context != null ? context : contextC;
        int ctxB = effectiveC != null ? effectiveC : (contextBefore != null ? contextBefore : 0);
        int ctxA = effectiveC != null ? effectiveC : (contextAfter != null ? contextAfter : 0);
        String vcsPattern = "'(\\\\|/)(" + String.join("|", escapedVcsDirectories()) + ")(\\\\|/|$)'";

        List<String> pipeline = new ArrayList<>();
        if (Files.isRegularFile(Path.of(path))) {
            pipeline.add("Get-Item -LiteralPath " + psQuote(path));
        } else {
            pipeline.add("Get-ChildItem -LiteralPath " + psQuote(path) + " -Recurse -File");
            pipeline.add("Where-Object { $_.FullName -notmatch " + vcsPattern + " }");
        }

        if (!expandedGlobs.isEmpty() && !Files.isRegularFile(Path.of(path))) {
            List<String> conditions = expandedGlobs.stream()
                    .map(globPattern -> "$_.Name -like " + psQuote(globPattern))
                    .toList();
            pipeline.add("Where-Object { " + String.join(" -or ", conditions) + " }");
        }

        String caseFlag = caseInsensitive ? "" : " -CaseSensitive";
        String contextFlag = "content".equals(outputMode) && (ctxB > 0 || ctxA > 0) ? " -Context " + ctxB + "," + ctxA : "";
        pipeline.add("Select-String -Pattern " + psQuote(pattern) + caseFlag + contextFlag);

        if ("files_with_matches".equals(outputMode)) {
            pipeline.add("Select-Object -ExpandProperty Path -Unique");
        } else if ("count".equals(outputMode)) {
            pipeline.add("Group-Object Path | ForEach-Object { \"$($_.Name):$($_.Count)\" }");
        } else if (ctxB > 0 || ctxA > 0) {
            pipeline.add("ForEach-Object {"
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

    private ToolOutput searchViaSysOperation(String path, String pattern) {
        if (sysOperation == null) {
            return new ToolOutput(false, null, "sysOperation is required");
        }
        var result = sysOperation.fs().searchFiles(path, pattern, List.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        @SuppressWarnings("unchecked")
        List<FileSystemItem> items = (List<FileSystemItem>) readField(payload, "matchingFiles");
        List<String> matches = items.stream()
                .map(item -> readStringField(item, "path"))
                .toList();
        return new ToolOutput(true, matches, null);
    }

    private Map<String, Object> searchWithJava(
            String rawPath,
            String pattern,
            Map<String, Object> inputs,
            String outputMode) throws IOException {
        Path root = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            throw new IOException("path does not exist: " + rawPath);
        }
        boolean ignoreCase = asBoolean(inputs.getOrDefault("ignore_case", inputs.get("-i")), false);
        List<String> globPatterns = expandGlobPatterns(splitGlobPatterns(stringValue(inputs.get("glob"))));
        Integer contextBefore = asInteger(inputs.get("-B"));
        Integer contextAfter = asInteger(inputs.get("-A"));
        Integer contextC = asInteger(inputs.get("-C"));
        Integer context = asInteger(inputs.get("context"));
        if (!"content".equals(outputMode)) {
            contextBefore = null;
            contextAfter = null;
            contextC = null;
            context = null;
        }
        int effectiveBefore = Optional.ofNullable(context).orElse(Optional.ofNullable(contextC).orElse(Optional.ofNullable(contextBefore).orElse(0)));
        int effectiveAfter = Optional.ofNullable(context).orElse(Optional.ofNullable(contextC).orElse(Optional.ofNullable(contextAfter).orElse(0)));

        List<Path> files = enumerateFiles(root, globPatterns);
        Map<Path, Integer> counts = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        Pattern compiled = Pattern.compile(Pattern.quote(pattern), ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
        Path basePath = Files.isDirectory(root) ? root : root.getParent();
        for (Path file : files) {
            List<String> fileLines = Files.readAllLines(file);
            for (int i = 0; i < fileLines.size(); i++) {
                if (!compiled.matcher(fileLines.get(i)).find()) {
                    continue;
                }
                counts.merge(file, 1, Integer::sum);
                if ("content".equals(outputMode)) {
                    int from = Math.max(0, i - effectiveBefore);
                    int to = Math.min(fileLines.size() - 1, i + effectiveAfter);
                    for (int j = from; j <= to; j++) {
                        String rel = relativize(basePath, file);
                        lines.add(rel + ":" + (j + 1) + ":" + fileLines.get(j));
                    }
                }
            }
        }

        if ("files_with_matches".equals(outputMode)) {
            lines.addAll(counts.keySet().stream().map(file -> relativize(basePath, file)).toList());
        } else if ("count".equals(outputMode)) {
            counts.forEach((file, count) -> lines.add(relativize(basePath, file) + ":" + count));
        }

        Integer offset = asInteger(inputs.get("offset"));
        Integer headLimit = asInteger(inputs.get("head_limit"));
        List<String> finalLines = applyHeadLimit(lines, headLimit, offset == null ? 0 : offset);
        String content = String.join("\n", finalLines);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stdout", content);
        data.put("stderr", "");
        data.put("exit_code", counts.isEmpty() ? 1 : 0);
        data.put("mode", outputMode);
        data.put("content", content);
        data.put("count", "count".equals(outputMode) ? counts.values().stream().mapToInt(Integer::intValue).sum() : finalLines.size());
        if ("content".equals(outputMode)) {
            data.put("filenames", List.of());
            data.put("numFiles", 0);
            data.put("numLines", finalLines.size());
        } else if ("count".equals(outputMode)) {
            data.put("filenames", List.of());
            data.put("numFiles", counts.size());
            data.put("numMatches", counts.values().stream().mapToInt(Integer::intValue).sum());
        } else {
            data.put("filenames", finalLines);
            data.put("numFiles", finalLines.size());
        }
        return data;
    }

    private static List<Path> enumerateFiles(Path root, List<String> globPatterns) throws IOException {
        if (Files.isRegularFile(root)) {
            return List.of(root);
        }
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isInVcsDirectory(root, path))
                    .filter(path -> globPatterns.isEmpty() || globPatterns.stream().anyMatch(glob -> globMatches(path, glob)))
                    .toList();
        }
    }

    private static boolean isInVcsDirectory(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            String name = String.valueOf(part);
            for (String vcs : VCS_DIRECTORIES_TO_EXCLUDE) {
                if (vcs.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean globMatches(Path file, String globPattern) {
        return file.getFileSystem().getPathMatcher("glob:" + globPattern).matches(file.getFileName());
    }

    private static List<String> applyHeadLimit(List<String> lines, Integer limit, int offset) {
        int start = Math.max(0, offset);
        if (start >= lines.size()) {
            return List.of();
        }
        if (limit != null && limit == 0) {
            return lines.subList(start, lines.size());
        }
        int effectiveLimit = limit == null ? DEFAULT_HEAD_LIMIT : limit;
        int end = Math.min(lines.size(), start + effectiveLimit);
        return lines.subList(start, end);
    }

    private static String relativize(Path basePath, Path file) {
        if (basePath == null) {
            return file.toString();
        }
        try {
            return basePath.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
        } catch (IllegalArgumentException e) {
            return file.toString();
        }
    }

    private static List<String> splitGlobPatterns(String glob) {
        if (glob == null || glob.isBlank()) {
            return List.of();
        }
        List<String> patterns = new ArrayList<>();
        for (String raw : glob.split("\\s+")) {
            if (raw.contains("{") && raw.contains("}")) {
                patterns.add(raw);
            } else {
                for (String part : raw.split(",")) {
                    if (!part.isBlank()) {
                        patterns.add(part);
                    }
                }
            }
        }
        return patterns;
    }

    private static List<String> expandGlobPatterns(List<String> patterns) {
        List<String> expanded = new ArrayList<>();
        Pattern brace = Pattern.compile("^(.*)\\{([^}]+)}(.*)$");
        for (String pattern : patterns) {
            var matcher = brace.matcher(pattern);
            if (matcher.matches()) {
                for (String alt : matcher.group(2).split(",")) {
                    expanded.add(matcher.group(1) + alt + matcher.group(3));
                }
            } else {
                expanded.add(pattern);
            }
        }
        return expanded;
    }

    private static List<String> escapedVcsDirectories() {
        return java.util.Arrays.stream(VCS_DIRECTORIES_TO_EXCLUDE)
                .map(dir -> dir.replace(".", "\\."))
                .toList();
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return switch (String.valueOf(value).trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }

    private static Integer asInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
