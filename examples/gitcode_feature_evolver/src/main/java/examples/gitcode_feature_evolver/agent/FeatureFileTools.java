/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Repository read/search and dynamically scoped write tools for one feature stage Agent.
 *
 * @since 0.1.12
 */
public final class FeatureFileTools {
    private static final long MAX_READABLE_FILE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_SEARCHABLE_FILE_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_WRITE_BYTES = 2L * 1024L * 1024L;
    private static final int DEFAULT_READ_LINES = 2_000;
    private static final int MAX_READ_LINES = 2_000;
    private static final int MAX_LINE_CODE_POINTS = 2_000;
    private static final int DEFAULT_SEARCH_RESULTS = 100;
    private static final int MAX_SEARCH_RESULTS = 250;
    private static final int MAX_SEARCH_LINE_BYTES = 2_000;
    private static final int MAX_INLINE_PAYLOAD_BYTES = 40 * 1024;
    private static final int MAX_TOOL_RESULT_BYTES = 50 * 1024;
    private static final int READ_BUFFER_CHARS = 8 * 1024;
    private static final int MAX_LINE_BUFFER_CHARS = MAX_LINE_CODE_POINTS * 2 + 2;
    private static final String LINE_TRUNCATED_SUFFIX = "... (line truncated)";
    private final Path root;
    private final Path realRoot;
    private final List<String> writeScopes;
    private final String toolPrefix;
    private final String publicNamePrefix;
    private final boolean isWritable;

    /**
     * Bind tools to one Worktree and a controller-approved write scope.
     *
     * @param worktree isolated feature Worktree
     * @param approvedWriteScopes exact files or trailing-slash directories
     * @param agentId unique Agent ID
     */
    public FeatureFileTools(Path worktree, List<String> approvedWriteScopes, String agentId) {
        this(worktree, approvedWriteScopes, agentId, "", true);
    }

    private FeatureFileTools(Path worktree, List<String> approvedWriteScopes, String agentId,
                             String publicNamePrefix, boolean isWritable) {
        try {
            this.root = Objects.requireNonNull(worktree, "worktree must not be null")
                    .toAbsolutePath().normalize();
            this.realRoot = root.toRealPath();
        } catch (IOException ex) {
            throw new FeatureToolException(FeatureToolException.Code.WORKTREE_UNAVAILABLE,
                    "Feature Worktree is unavailable", ex);
        }
        this.writeScopes = FeaturePathPolicy.normalizeScopes(approvedWriteScopes);
        this.toolPrefix = Objects.requireNonNull(agentId, "agentId must not be null") + ".";
        this.publicNamePrefix = Objects.requireNonNull(
                publicNamePrefix, "publicNamePrefix must not be null");
        this.isWritable = isWritable;
    }

    /**
     * Bind read-only tools to the merged feature source for a system-test stage.
     *
     * @param sourceWorktree immutable merged feature source
     * @param agentId unique Agent ID
     * @return read-only source tools with distinct public names
     */
    public static FeatureFileTools readOnlySource(Path sourceWorktree, String agentId) {
        return new FeatureFileTools(sourceWorktree, List.of(), agentId, "Source", false);
    }

    /** @return read, search, and dynamically restricted write tools */
    public List<Tool> create() {
        return isWritable ? List.of(readTool(), searchTool(), writeTool(), replaceTool())
                : List.of(readTool(), searchTool());
    }

    private Tool readTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + toolName("readFile"))
                .name(toolName("readFile"))
                .description("Read one non-sensitive UTF-8 " + repositoryLabel()
                        + " file by relative path. Returns exact line range and totalLines. "
                        + "Results are bounded; continue from nextOffset while hasMore is true.")
                .inputParams(schema(Map.of(
                        "path", Map.of("type", "string"),
                        "offset", Map.of("type", "integer", "minimum", 1),
                        "limit", Map.of("type", "integer", "minimum", 1,
                                "maximum", MAX_READ_LINES)), List.of("path")))
                .build();
        return new LocalFunction(card, input -> read(required(input, "path"),
                optionalInteger(input, "offset", 1, 1, Integer.MAX_VALUE),
                optionalInteger(input, "limit", DEFAULT_READ_LINES, 1, MAX_READ_LINES)));
    }

    private Tool searchTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + toolName("searchFiles"))
                .name(toolName("searchFiles"))
                .description("Search non-sensitive UTF-8 " + repositoryLabel()
                        + " files for case-insensitive literal text. path may name one file or "
                        + "a directory. Returns structured matches, totalMatches, and skipped-file "
                        + "counts. offset is a zero-based match offset; continue from nextOffset "
                        + "while hasMore is true.")
                .inputParams(schema(Map.of(
                        "query", Map.of("type", "string"),
                        "path", Map.of("type", "string"),
                        "offset", Map.of("type", "integer", "minimum", 0),
                        "limit", Map.of("type", "integer", "minimum", 1,
                                "maximum", MAX_SEARCH_RESULTS)), List.of("query")))
                .build();
        return new LocalFunction(card, input -> search(required(input, "query"),
                optional(input, "path", "."),
                optionalInteger(input, "offset", 0, 0, Integer.MAX_VALUE),
                optionalInteger(input, "limit", DEFAULT_SEARCH_RESULTS, 1, MAX_SEARCH_RESULTS)));
    }

    private Tool writeTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "writeFile")
                .name("writeFile")
                .description("Replace one small UTF-8 file inside the exact controller-approved "
                        + "write scope. Supply one physical source line per JSON lines item.")
                .inputParams(schema(Map.of(
                        "path", Map.of("type", "string"),
                        "lines", Map.of("type", "array", "items", Map.of("type", "string")),
                        "trailingNewline", Map.of("type", "boolean")),
                        List.of("path", "lines")))
                .build();
        return new LocalFunction(card, input -> write(required(input, "path"),
                lineContent(input, "lines", false)));
    }

    private Tool replaceTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "replaceInFile")
                .name("replaceInFile")
                .description("Replace exactly one matching block in an existing UTF-8 file "
                        + "inside the controller-approved write scope. Supply oldLines and "
                        + "newLines as JSON arrays with one physical source line per item.")
                .inputParams(schema(Map.of(
                        "path", Map.of("type", "string"),
                        "oldLines", Map.of("type", "array",
                                "items", Map.of("type", "string")),
                        "newLines", Map.of("type", "array",
                                "items", Map.of("type", "string"))),
                        List.of("path", "oldLines", "newLines")))
                .build();
        return new LocalFunction(card, input -> replace(required(input, "path"),
                lineContent(input, "oldLines", true),
                lineContent(input, "newLines", false)));
    }

    private String toolName(String baseName) {
        if (publicNamePrefix.isEmpty()) {
            return baseName;
        }
        if ("readFile".equals(baseName)) {
            return "read" + publicNamePrefix + "File";
        }
        if ("searchFiles".equals(baseName)) {
            return "search" + publicNamePrefix + "Files";
        }
        throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                "Unsupported public tool name");
    }

    private String repositoryLabel() {
        return publicNamePrefix.isEmpty() ? "Worktree" : "merged-source Worktree";
    }

    private Map<String, Object> read(String supplied, int offset, int limit) {
        String normalized = normalizePath(supplied);
        if (FeaturePathPolicy.isSensitiveRead(normalized)) {
            throw new FeatureToolException(FeatureToolException.Code.SENSITIVE_PATH,
                    "Sensitive repository paths are unavailable");
        }
        Path file = resolveExisting(normalized);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new FeatureToolException(FeatureToolException.Code.PATH_TYPE_UNSUPPORTED,
                        "Read path is not a regular file");
            }
            if (Files.size(file) > MAX_READABLE_FILE_BYTES) {
                throw new FeatureToolException(FeatureToolException.Code.FILE_TOO_LARGE,
                        "File exceeds the readable size limit");
            }
            return readPage(file, offset, limit);
        } catch (MalformedInputException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_NOT_UTF8,
                    "Repository file is not valid UTF-8", ex);
        } catch (AccessDeniedException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Repository file is not readable", ex);
        } catch (IOException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Unable to read repository file", ex);
        }
    }

    private Map<String, Object> readPage(Path file, int offset, int limit) throws IOException {
        ReadAccumulator page = new ReadAccumulator(offset, limit);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            char[] buffer = new char[READ_BUFFER_CHARS];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                page.accept(buffer, count);
            }
        }
        page.finish();
        return boundedResult(page.result(relative(file)));
    }

    private Map<String, Object> search(String query, String suppliedPath, int offset, int limit) {
        if (query.length() > 200) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    "Search query is too long");
        }
        Path searchRoot = resolveSearchRoot(suppliedPath);
        SearchAccumulator accumulator = new SearchAccumulator(offset, limit);
        for (Path file : searchTargets(searchRoot, accumulator)) {
            collectFileMatches(file, query.toLowerCase(Locale.ROOT), accumulator);
        }
        return boundedResult(accumulator.result());
    }

    private Path resolveSearchRoot(String supplied) {
        if (".".equals(supplied)) {
            return realRoot;
        }
        String normalized = normalizePath(supplied);
        if (FeaturePathPolicy.isSensitiveRead(normalized)) {
            throw new FeatureToolException(FeatureToolException.Code.SENSITIVE_PATH,
                    "Sensitive repository paths are unavailable");
        }
        return resolveExisting(normalized);
    }

    private List<Path> searchTargets(Path searchRoot, SearchAccumulator accumulator) {
        if (Files.isRegularFile(searchRoot, LinkOption.NOFOLLOW_LINKS)) {
            return isSearchable(searchRoot) ? List.of(searchRoot) : List.of();
        }
        if (!Files.isDirectory(searchRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new FeatureToolException(FeatureToolException.Code.PATH_TYPE_UNSUPPORTED,
                    "Search path is not a regular file or directory");
        }
        List<Path> targets = new ArrayList<>();
        try {
            Files.walkFileTree(searchRoot, new SearchFileVisitor(targets, accumulator));
        } catch (IOException ex) {
            throw new FeatureToolException(FeatureToolException.Code.SEARCH_FAILED,
                    "Unable to enumerate repository files", ex);
        }
        targets.sort(Comparator.naturalOrder());
        return targets;
    }

    private void collectFileMatches(Path file, String lowerQuery,
                                    SearchAccumulator accumulator) {
        try {
            if (Files.size(file) > MAX_SEARCHABLE_FILE_BYTES) {
                accumulator.skipLargeFile();
                return;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            accumulator.scanFile();
            scanText(relative(file), content, lowerQuery, accumulator);
        } catch (MalformedInputException ex) {
            accumulator.skipNonUtf8File();
        } catch (AccessDeniedException ex) {
            accumulator.skipUnreadableFile();
        } catch (IOException ex) {
            accumulator.skipUnreadableFile();
        }
    }

    private static void scanText(String path, String content, String lowerQuery,
                                 SearchAccumulator accumulator) {
        int lineNumber = 1;
        int lineStart = 0;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) != '\n') {
                continue;
            }
            addMatchingLine(path, lineNumber, content.substring(lineStart, index),
                    lowerQuery, accumulator);
            lineNumber++;
            lineStart = index + 1;
        }
        if (lineStart < content.length()) {
            addMatchingLine(path, lineNumber, content.substring(lineStart),
                    lowerQuery, accumulator);
        }
    }

    private static void addMatchingLine(String path, int lineNumber, String rawLine,
                                        String lowerQuery, SearchAccumulator accumulator) {
        String line = rawLine.endsWith("\r")
                ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
        if (line.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            accumulator.add(path, lineNumber, line.strip());
        }
    }

    private boolean isSearchable(Path file) {
        String relative = relative(file);
        return !Files.isSymbolicLink(file) && !FeaturePathPolicy.isSensitiveRead(relative)
                && !relative.startsWith("target/");
    }

    private Map<String, Object> write(String supplied, String content) {
        String normalized = normalizePath(supplied);
        if (!FeaturePathPolicy.isAllowedWrite(normalized, writeScopes)) {
            throw new FeatureToolException(FeatureToolException.Code.WRITE_SCOPE_DENIED,
                    "Write path is outside the controller-approved scope");
        }
        Path file = resolveForWrite(normalized);
        writeContent(file, content);
        return boundedResult(Map.of("path", normalized, "written", true));
    }

    private Map<String, Object> replace(String supplied, String oldContent, String newContent) {
        String normalized = normalizePath(supplied);
        if (!FeaturePathPolicy.isAllowedWrite(normalized, writeScopes)) {
            throw new FeatureToolException(FeatureToolException.Code.WRITE_SCOPE_DENIED,
                    "Write path is outside the controller-approved scope");
        }
        Path file = resolveForWrite(normalized);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new FeatureToolException(FeatureToolException.Code.PATH_TYPE_UNSUPPORTED,
                        "Replace target is not a regular file");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int first = content.indexOf(oldContent);
            if (first < 0) {
                throw new FeatureToolException(FeatureToolException.Code.REPLACEMENT_NOT_FOUND,
                        "Replacement block was not found");
            }
            if (content.indexOf(oldContent, first + oldContent.length()) >= 0) {
                throw new FeatureToolException(FeatureToolException.Code.REPLACEMENT_NOT_UNIQUE,
                        "Replacement block is not unique");
            }
            String updated = content.substring(0, first) + newContent
                    + content.substring(first + oldContent.length());
            writeContent(file, updated);
            return boundedResult(Map.of("path", normalized, "replaced", true));
        } catch (MalformedInputException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_NOT_UTF8,
                    "Replace target is not valid UTF-8", ex);
        } catch (AccessDeniedException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Replace target is not readable", ex);
        } catch (IOException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Unable to replace repository file", ex);
        }
    }

    private void writeContent(Path file, String content) {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_WRITE_BYTES) {
            throw new FeatureToolException(FeatureToolException.Code.CONTENT_TOO_LARGE,
                    "File content is too large");
        }
        try {
            Files.createDirectories(file.getParent());
            rejectSymbolicLinks(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".feature-write-", ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                moveReplacing(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Unable to write repository file", ex);
        }
    }

    private Path resolveExisting(String supplied) {
        Path candidate = resolveRelative(supplied);
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new FeatureToolException(FeatureToolException.Code.PATH_OUTSIDE_WORKTREE,
                        "Path escapes the Worktree");
            }
            return real;
        } catch (NoSuchFileException ex) {
            throw new FeatureToolException(FeatureToolException.Code.PATH_NOT_FOUND,
                    "Repository path does not exist", ex);
        } catch (AccessDeniedException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Repository path is not readable", ex);
        } catch (IOException ex) {
            throw new FeatureToolException(FeatureToolException.Code.FILE_UNREADABLE,
                    "Unable to resolve repository path", ex);
        }
    }

    private Path resolveForWrite(String supplied) {
        Path candidate = resolveRelative(supplied);
        rejectSymbolicLinks(candidate.getParent());
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(candidate)) {
            throw new FeatureToolException(FeatureToolException.Code.SYMBOLIC_LINK_DENIED,
                    "Symbolic-link writes are not allowed");
        }
        return candidate;
    }

    private Path resolveRelative(String supplied) {
        Path candidate = root.resolve(normalizePath(supplied)).normalize();
        if (!candidate.startsWith(root)) {
            throw new FeatureToolException(FeatureToolException.Code.PATH_OUTSIDE_WORKTREE,
                    "Path escapes the Worktree");
        }
        return candidate;
    }

    private void rejectSymbolicLinks(Path candidate) {
        Path current = root;
        Path relative = root.relativize(candidate.toAbsolutePath().normalize());
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new FeatureToolException(FeatureToolException.Code.SYMBOLIC_LINK_DENIED,
                        "Symbolic-link paths are not allowed");
            }
        }
    }

    private String relative(Path file) {
        return realRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String normalizePath(String supplied) {
        try {
            return FeaturePathPolicy.normalize(supplied);
        } catch (IllegalArgumentException ex) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    "Repository path is invalid", ex);
        }
    }

    private static Map<String, Object> boundedResult(Map<String, Object> result) {
        int bytes = utf8Bytes(String.valueOf(result));
        if (bytes > MAX_TOOL_RESULT_BYTES) {
            throw new FeatureToolException(FeatureToolException.Code.RESULT_TOO_LARGE,
                    "Tool result exceeded its complete output budget");
        }
        return result;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
    }

    private static String required(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    key + " is required");
        }
        return text;
    }

    private static String optional(Map<String, Object> input, String key, String fallback) {
        Object value = input.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static int optionalInteger(Map<String, Object> input, String key,
                                       int fallback, int minimum, int maximum) {
        Object value = input.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    key + " must be an integer");
        }
        long supplied = number.longValue();
        if (supplied < minimum || supplied > maximum || number.doubleValue() != supplied) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    key + " is outside the supported range");
        }
        return (int) supplied;
    }

    private static String lineContent(Map<String, Object> input, String key,
                                      boolean isContentRequired) {
        Object value = input.get(key);
        if (!(value instanceof List<?> supplied)) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    key + " is required");
        }
        List<String> lines = new ArrayList<>(supplied.size());
        for (Object item : supplied) {
            if (!(item instanceof String line) || line.indexOf('\n') >= 0
                    || line.indexOf('\r') >= 0) {
                throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                        key + " must contain one physical line per string");
            }
            lines.add(line);
        }
        if (isContentRequired && lines.isEmpty()) {
            throw new FeatureToolException(FeatureToolException.Code.INVALID_ARGUMENT,
                    key + " must not be empty");
        }
        String content = String.join("\n", lines);
        Object trailing = input.get("trailingNewline");
        return !lines.isEmpty() && !Boolean.FALSE.equals(trailing)
                ? content + "\n" : content;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String truncateCodePoints(String value, int maximum) {
        int count = value.codePointCount(0, value.length());
        if (count <= maximum) {
            return value;
        }
        int suffixPoints = LINE_TRUNCATED_SUFFIX.codePointCount(
                0, LINE_TRUNCATED_SUFFIX.length());
        int prefixPoints = Math.max(0, maximum - suffixPoints);
        return value.substring(0, value.offsetByCodePoints(0, prefixPoints))
                + LINE_TRUNCATED_SUFFIX;
    }

    private static String truncateUtf8(String value, int maximumBytes) {
        if (utf8Bytes(value) <= maximumBytes) {
            return value;
        }
        int budget = maximumBytes - utf8Bytes(LINE_TRUNCATED_SUFFIX);
        int end = 0;
        int retainedBytes = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int codePointBytes = utf8CodePointBytes(codePoint);
            if (retainedBytes + codePointBytes > budget) {
                break;
            }
            retainedBytes += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end) + LINE_TRUNCATED_SUFFIX;
    }

    private static int utf8CodePointBytes(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        return codePoint <= 0xFFFF ? 3 : 4;
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private final class SearchFileVisitor extends SimpleFileVisitor<Path> {
        private final List<Path> targets;
        private final SearchAccumulator accumulator;

        private SearchFileVisitor(List<Path> targets, SearchAccumulator accumulator) {
            this.targets = targets;
            this.accumulator = accumulator;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile() && isSearchable(file)) {
                targets.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            accumulator.skipUnreadableFile();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) {
            if (exception != null) {
                accumulator.skipUnreadableFile();
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class ReadAccumulator {
        private final int offset;
        private final int limit;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder currentLine = new StringBuilder();
        private int contentBytes;
        private int returnedLines;
        private int endLine;
        private int totalLines;
        private boolean isCurrentLineOverflow;
        private boolean isOutputCapped;
        private boolean isLineTruncated;

        private ReadAccumulator(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }

        private void accept(char[] buffer, int count) {
            for (int index = 0; index < count; index++) {
                char value = buffer[index];
                if (value == '\n') {
                    completeCurrentLine();
                } else if (currentLine.length() < MAX_LINE_BUFFER_CHARS) {
                    currentLine.append(value);
                } else {
                    isCurrentLineOverflow = true;
                }
            }
        }

        private void finish() {
            if (!currentLine.isEmpty() || isCurrentLineOverflow) {
                completeCurrentLine();
            }
        }

        private void completeCurrentLine() {
            totalLines++;
            String rawLine = currentLine.toString();
            if (rawLine.endsWith("\r")) {
                rawLine = rawLine.substring(0, rawLine.length() - 1);
            }
            retainLine(rawLine);
            currentLine.setLength(0);
            isCurrentLineOverflow = false;
        }

        private void retainLine(String rawLine) {
            if (totalLines < offset || returnedLines >= limit || isOutputCapped) {
                return;
            }
            String line = truncateCodePoints(rawLine, MAX_LINE_CODE_POINTS);
            boolean isTruncated = isCurrentLineOverflow || !line.equals(rawLine);
            int separatorBytes = returnedLines == 0 ? 0 : 1;
            if (contentBytes + separatorBytes + utf8Bytes(line) > MAX_INLINE_PAYLOAD_BYTES) {
                isOutputCapped = true;
                return;
            }
            if (returnedLines > 0) {
                content.append('\n');
                contentBytes++;
            }
            content.append(line);
            contentBytes += utf8Bytes(line);
            returnedLines++;
            endLine = totalLines;
            isLineTruncated |= isTruncated;
        }

        private Map<String, Object> result(String path) {
            if (offset > totalLines && !(totalLines == 0 && offset == 1)) {
                throw new FeatureToolException(FeatureToolException.Code.OFFSET_OUT_OF_RANGE,
                        "Read offset is past the end of the file");
            }
            boolean hasMore = endLine == 0 ? offset <= totalLines : endLine < totalLines;
            int nextOffset = hasMore ? (endLine == 0 ? offset : endLine + 1) : 0;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("offset", offset);
            result.put("limit", limit);
            result.put("startLine", returnedLines == 0 ? 0 : offset);
            result.put("endLine", endLine);
            result.put("totalLines", totalLines);
            result.put("returnedLines", returnedLines);
            result.put("nextOffset", nextOffset);
            result.put("hasMore", hasMore);
            result.put("truncated", hasMore || isLineTruncated);
            result.put("outputCapped", isOutputCapped);
            result.put("lineTruncated", isLineTruncated);
            result.put("content", content.toString());
            return result;
        }
    }

    private static final class SearchAccumulator {
        private final int offset;
        private final int limit;
        private final List<Map<String, Object>> matches = new ArrayList<>();
        private int seen;
        private int resultBytes;
        private int scannedFiles;
        private int skippedNonUtf8Files;
        private int skippedUnreadableFiles;
        private int skippedLargeFiles;
        private int truncatedLines;
        private boolean isOutputCapped;

        private SearchAccumulator(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }

        private void add(String path, int lineNumber, String rawLine) {
            int matchIndex = seen++;
            if (matchIndex < offset) {
                return;
            }
            String preview = truncateUtf8(rawLine, MAX_SEARCH_LINE_BYTES);
            if (!preview.equals(rawLine)) {
                truncatedLines++;
            }
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("path", path);
            match.put("lineNumber", lineNumber);
            match.put("line", preview);
            int separatorBytes = matches.isEmpty() ? 0 : 1;
            int matchBytes = utf8Bytes(String.valueOf(match));
            if (matches.size() >= limit || isOutputCapped) {
                return;
            }
            if (resultBytes + separatorBytes + matchBytes > MAX_INLINE_PAYLOAD_BYTES) {
                isOutputCapped = true;
                return;
            }
            matches.add(match);
            resultBytes += separatorBytes + matchBytes;
        }

        private void scanFile() {
            scannedFiles++;
        }

        private void skipNonUtf8File() {
            skippedNonUtf8Files++;
        }

        private void skipUnreadableFile() {
            skippedUnreadableFiles++;
        }

        private void skipLargeFile() {
            skippedLargeFiles++;
        }

        private Map<String, Object> result() {
            boolean hasMore = offset + matches.size() < seen;
            int skippedFiles = skippedNonUtf8Files + skippedUnreadableFiles
                    + skippedLargeFiles;
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("nonUtf8", skippedNonUtf8Files);
            skipped.put("unreadable", skippedUnreadableFiles);
            skipped.put("tooLarge", skippedLargeFiles);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("matches", List.copyOf(matches));
            result.put("offset", offset);
            result.put("limit", limit);
            result.put("returnedMatches", matches.size());
            result.put("totalMatches", seen);
            result.put("nextOffset", hasMore ? offset + matches.size() : 0);
            result.put("hasMore", hasMore);
            result.put("truncated", truncatedLines > 0 || hasMore);
            result.put("outputCapped", isOutputCapped);
            result.put("truncatedLines", truncatedLines);
            result.put("scannedFiles", scannedFiles);
            result.put("skippedFiles", skipped);
            result.put("scanComplete", skippedFiles == 0);
            return result;
        }
    }
}
