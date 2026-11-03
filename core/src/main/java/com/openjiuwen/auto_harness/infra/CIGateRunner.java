/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CI gate runner for auto-harness.
 * <p>
 * Mirrors Python's {@code CIGateRunner} in
 * {@code openjiuwen/auto_harness/infra/ci_gate_runner.py}.
 */
public class CIGateRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_YAML = "auto_harness/resources/ci_gate.yaml";
    private static final Pattern HUNK_RE =
            Pattern.compile("^@@\\s*-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s*@@");
    private static final Pattern DIFF_FILE_RE =
            Pattern.compile("^--- (?:a/)?(.+)$|^\\+\\+\\+ (?:b/)?(.+)$");
    private static final Pattern COMMITS_RE = Pattern.compile("COMMITS=(\\d+)");
    private static final Pattern CODESPELL_RE = Pattern.compile("^(\\S+):(\\d+):.*$");
    private static final Pattern MYPY_RE = Pattern.compile("^(\\S+):(\\d+):\\s*(error|note|warning):\\s+(.*)$");

    private String workspace;
    private String pythonExecutable;
    private String installCommand;
    private boolean prepared;
    private List<Map<String, Object>> gates;
    private final CommandExecutor executor;
    private final boolean makeAvailable;

    @FunctionalInterface
    public interface CommandExecutor {
        CommandResult execute(List<String> command, String cwd, Map<String, String> env)
                throws IOException, InterruptedException;
    }

    public record CommandResult(int returnCode, String output) {
    }

    public CIGateRunner(String workspace) {
        this(workspace, "", "", "");
    }

    public CIGateRunner(String workspace, String configPath) {
        this(workspace, configPath, "", "");
    }

    public CIGateRunner(String workspace, String configPath, String pythonExecutable, String installCommand) {
        this(workspace, configPath, pythonExecutable, installCommand, CIGateRunner::executeCommand);
    }

    public CIGateRunner(
            String workspace,
            String configPath,
            String pythonExecutable,
            String installCommand,
            CommandExecutor executor) {
        this.workspace = workspace;
        this.pythonExecutable = pythonExecutable == null ? "" : pythonExecutable;
        this.installCommand = installCommand == null ? "" : installCommand.strip();
        this.prepared = false;
        this.gates = loadGates((configPath == null || configPath.isBlank()) ? DEFAULT_YAML : configPath);
        this.executor = executor == null ? CIGateRunner::executeCommand : executor;
        this.makeAvailable = findOnPath("make").isPresent();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadGates(String path) {
        try {
            Object parsed;
            Path file = Path.of(path);
            if (Files.exists(file)) {
                parsed = new Yaml().load(Files.readString(file));
            } else {
                try (InputStream stream = CIGateRunner.class.getClassLoader().getResourceAsStream(path)) {
                    if (stream == null) {
                        return List.of();
                    }
                    parsed = new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            if (!(parsed instanceof Map<?, ?> root)) {
                return List.of();
            }
            Object raw = root.get("ci_gates");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> loaded = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    loaded.add(normalized);
                }
            }
            return loaded;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public List<Map<String, Object>> matchGates(String action) {
        String requested = action == null ? "" : action;
        if ("all".equals(requested)) {
            return new ArrayList<>(gates);
        }
        String target = "check".equals(requested) ? "lint" : requested;
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> gate : gates) {
            if (target.equals(gate.get("name"))) {
                matched.add(gate);
            }
        }
        return matched;
    }

    public CompletableFuture<Map<String, Object>> run() {
        return run("all");
    }

    public CompletableFuture<Map<String, Object>> run(String action) {
        return CompletableFuture.supplyAsync(() -> runSync(action == null ? "all" : action.strip()));
    }

    private Map<String, Object> runSync(String action) {
        List<Map<String, Object>> matched = matchGates(action);
        if (matched.isEmpty()) {
            return Map.of(
                    "passed", false,
                    "gates", List.of(),
                    "errors", "No gate matched action=" + action);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> gate : matched) {
            results.add(runGate(gate));
        }

        boolean allPassed = results.stream().allMatch(result -> Boolean.TRUE.equals(result.get("passed")));
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> result : results) {
            if (Boolean.TRUE.equals(result.get("passed"))) {
                continue;
            }
            String output = String.valueOf(result.getOrDefault("output", "")).strip();
            if (!output.isEmpty()) {
                errors.add(("[" + result.getOrDefault("name", "unknown") + "]\n" + output).strip());
            }
        }
        return Map.of(
                "passed", allPassed,
                "gates", results,
                "errors", String.join("\n\n", errors));
    }

    public Map<String, Object> runGate(Map<String, Object> gate) {
        String rawCommand = String.valueOf(gate.getOrDefault("command", ""));
        String name = String.valueOf(gate.getOrDefault("name", "unknown"));

        if (isCommitScopedCheck(name, rawCommand)) {
            return runCheckGate(name, rawCommand);
        }
        if (isCommitScopedTypeCheck(name, rawCommand)) {
            return runTypeCheckGate(rawCommand);
        }

        String command = normalizeCommand(rawCommand);
        try {
            ensureEnvironment();
            CommandResult result = executor.execute(buildShellCommand(command), workspace, commandEnv());
            String output = sanitizeFailureOutput(result.output());
            return Map.of(
                    "name", name,
                    "passed", result.returnCode() == 0,
                    "output", tail(output, 4000));
        } catch (Exception e) {
            return Map.of(
                    "name", name,
                    "passed", false,
                    "output", e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private boolean isCommitScopedCheck(String name, String rawCommand) {
        return ("lint".equals(name) || "check".equals(name)) && rawCommand.contains("COMMITS=");
    }

    private boolean isCommitScopedTypeCheck(String name, String rawCommand) {
        return "type-check".equals(name) && rawCommand.contains("COMMITS=");
    }

    private Map<String, Object> runCheckGate(String name, String rawCommand) {
        String commits = extractCommits(rawCommand);
        Map<String, Set<Integer>> lineRanges = getDiffLineRanges(commits);
        List<String> changedFiles = getChangedFiles(commits);
        if (changedFiles.isEmpty()) {
            return Map.of("name", name, "passed", true, "output", "No files to check");
        }
        if (lineRanges.isEmpty()) {
            lineRanges = wholeFileRanges(changedFiles);
        }

        String python = quotePath(resolvePythonExecutable(), false);
        String quotedFiles = joinQuotedFiles(changedFiles);
        List<String> violations = new ArrayList<>();
        boolean failed = false;

        CommandResult ruff = runToolCommand(python + " -m ruff check --output-format=json " + quotedFiles);
        FilterResult ruffFiltered = filterRuffJsonByLineRanges(ruff.output(), lineRanges);
        if (ruffFiltered.hasViolations()) {
            failed = true;
            violations.add("[ruff] " + ruffFiltered.output());
        }

        CommandResult format = runToolCommand(python + " -m ruff format --check --diff " + quotedFiles);
        FilterResult formatFiltered = filterFormatDiffByLineRanges(format.output(), lineRanges);
        if (formatFiltered.hasViolations()) {
            failed = true;
            violations.add("[format] " + formatFiltered.output());
        }

        CommandResult codespell = runToolCommand("codespell " + quotedFiles);
        FilterResult codespellFiltered = filterCodespellByLineRanges(codespell.output(), lineRanges);
        if (codespellFiltered.hasViolations()) {
            failed = true;
            violations.add("[codespell] " + codespellFiltered.output());
        }

        CommandResult pylint = runToolCommand(python + " -m pylint --output-format=json " + quotedFiles);
        FilterResult pylintFiltered = filterPylintJsonByLineRanges(pylint.output(), lineRanges);
        if (pylintFiltered.hasViolations()) {
            failed = true;
            violations.add("[pylint] " + pylintFiltered.output());
        }

        String combined = String.join("\n\n", violations);
        return Map.of(
                "name", name,
                "passed", !failed,
                "output", combined.isEmpty() ? "All checks passed (scope: changed lines only)" : tail(combined, 4000));
    }

    private Map<String, Object> runTypeCheckGate(String rawCommand) {
        String commits = extractCommits(rawCommand);
        Map<String, Set<Integer>> lineRanges = getDiffLineRanges(commits);
        List<String> changedFiles = getChangedFiles(commits);
        if (changedFiles.isEmpty()) {
            return Map.of("name", "type-check", "passed", true, "output", "No files to type-check");
        }
        if (lineRanges.isEmpty()) {
            lineRanges = wholeFileRanges(changedFiles);
        }

        String python = quotePath(resolvePythonExecutable(), false);
        String quotedFiles = joinQuotedFiles(changedFiles);
        CommandResult mypy = runToolCommand(
                python + " -m mypy --show-error-codes --show-column-numbers " + quotedFiles);
        FilterResult filtered = filterMypyByLineRanges(mypy.output(), lineRanges);
        return Map.of(
                "name", "type-check",
                "passed", !filtered.hasViolations(),
                "output", filtered.hasViolations()
                        ? tail(filtered.output(), 4000)
                        : "Type check passed (scope: changed lines only)");
    }

    private void ensureEnvironment() throws IOException, InterruptedException {
        if (prepared) {
            return;
        }
        if (installCommand.isBlank()) {
            prepared = true;
            return;
        }
        if (installCommand.contains("uv")) {
            ensureUvAvailable();
        }
        CommandResult result = executor.execute(buildShellCommand(installCommand), workspace, commandEnv());
        if (result.returnCode() != 0) {
            throw new IllegalStateException("CI gate install command failed: " + tail(result.output().strip(), 1000));
        }
        prepared = true;
    }

    private void ensureUvAvailable() throws IOException, InterruptedException {
        CommandResult version = executor.execute(buildProcessCommand("uv", "--version"), workspace, commandEnv());
        if (version.returnCode() == 0) {
            return;
        }
        String python = resolvePythonExecutable();
        CommandResult install = executor.execute(
                buildProcessCommand(python, "-m", "pip", "install", "uv"),
                workspace,
                commandEnv());
        if (install.returnCode() != 0) {
            throw new IllegalStateException("Failed to install uv: " + tail(install.output(), 500));
        }
    }

    public String normalizeCommand(String command) {
        String stripped = command.strip();
        String python = quotePath(resolvePythonExecutable(), false);
        if (!stripped.startsWith("make ")) {
            if (stripped.startsWith("python -m ")) {
                return stripped.replaceFirst("^python -m ",
                        Matcher.quoteReplacement(python + " -m "));
            }
            int makeIndex = stripped.indexOf("make ");
            if (makeIndex > 0) {
                String makeSegment = stripped.substring(makeIndex);
                String normalizedMake = normalizeCommand(makeSegment);
                if (!normalizedMake.equals(makeSegment)) {
                    return (stripped.substring(0, makeIndex) + normalizedMake).strip();
                }
            }
            return command;
        }

        if (makeAvailable) {
            return command;
        }

        List<String> parts = splitShellWords(stripped);
        if (parts.isEmpty() || !"make".equals(parts.get(0))) {
            return command;
        }
        String target = parts.size() > 1 ? parts.get(1) : "";
        Map<String, String> assignments = new LinkedHashMap<>();
        for (int i = 2; i < parts.size(); i++) {
            int equals = parts.get(i).indexOf('=');
            if (equals > 0) {
                assignments.put(parts.get(i).substring(0, equals), parts.get(i).substring(equals + 1));
            }
        }
        if ("test".equals(target)) {
            String testFlags = assignments.getOrDefault("TESTFLAGS", "").trim();
            return (python + " -m pytest " + testFlags).trim();
        }
        return command;
    }

    public String resolvePythonExecutable() {
        List<String> candidates = new ArrayList<>();
        if (!pythonExecutable.isBlank()) {
            candidates.add(pythonExecutable);
        }
        if (workspace != null && !workspace.isBlank()) {
            Path root = Path.of(workspace);
            if (isWindows()) {
                candidates.add(root.resolve(".venv").resolve("Scripts").resolve("python.exe").toString());
            } else {
                candidates.add(root.resolve(".venv").resolve("bin").resolve("python").toString());
            }
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return findOnPath("python3")
                .or(() -> findOnPath("python"))
                .orElse("python");
    }

    public Map<String, String> commandEnv() {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("CI", "1");
        env.remove("VIRTUAL_ENV");
        String resolvedPython = resolvePythonExecutable();
        env.put("AUTO_HARNESS_PYTHON", resolvedPython);
        Path pythonPath = Path.of(resolvedPython);
        if (pythonPath.getFileName() != null
                && pythonPath.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("python")) {
            Path binDir = pythonPath.getParent();
            if (binDir != null) {
                String existingPath = env.getOrDefault("PATH", env.getOrDefault("Path", ""));
                String pathSeparator = System.getProperty("path.separator");
                env.put("PATH", existingPath.isBlank()
                        ? binDir.toString()
                        : binDir + pathSeparator + existingPath);
            }
        }
        return env;
    }

    public static String sanitizeFailureOutput(String output) {
        if (output == null || output.strip().isEmpty()) {
            return "";
        }

        List<String> failures = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        List<String> current = null;
        for (String line : output.split("\\R")) {
            String stripped = line.strip();
            if (stripped.startsWith("=") && stripped.endsWith("=")) {
                String normalized = stripped.replace("=", "").strip().toLowerCase(Locale.ROOT);
                if ("failures".equals(normalized)) {
                    current = failures;
                    current.add(line);
                } else if ("short test summary info".equals(normalized)) {
                    current = summary;
                    current.add(line);
                } else {
                    current = null;
                }
                continue;
            }
            if (current != null) {
                current.add(line);
            }
        }
        String failureText = String.join("\n", failures).strip();
        String summaryText = String.join("\n", summary).strip();
        List<String> sections = new ArrayList<>();
        if (!failureText.isEmpty()) {
            sections.add(failureText);
        }
        if (!summaryText.isEmpty()) {
            sections.add(summaryText);
        }
        return sections.isEmpty() ? output.strip() : String.join("\n\n", sections);
    }

    public static String decodeStdout(byte[] stdout) {
        List<Charset> encodings = new ArrayList<>();
        encodings.add(StandardCharsets.UTF_8);
        if (isWindows()) {
            encodings.add(Charset.forName("GBK"));
            encodings.add(Charset.forName("windows-936"));
        }
        encodings.add(StandardCharsets.ISO_8859_1);
        for (Charset encoding : encodings) {
            try {
                return new String(stdout, encoding);
            } catch (Exception ignored) {
                // Continue to next encoding.
            }
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    public static String quotePath(String path, boolean convertSlashes) {
        String normalized = path;
        if (isWindows() && convertSlashes) {
            boolean isAbsolute = (path.length() >= 2 && path.charAt(1) == ':')
                    || path.startsWith("\\\\")
                    || path.startsWith("/");
            if (!isAbsolute) {
                normalized = path.replace("/", "\\");
            }
        }

        String specialChars = isWindows()
                ? " \t\n\r\"&|;<>()$`!*?[]{}"
                : " \t\n\r\"'&|;<>()$`\\!*?[]{}";
        boolean requiresQuotes = normalized.chars().anyMatch(ch -> specialChars.indexOf(ch) >= 0);
        if (!requiresQuotes) {
            return normalized;
        }
        if (isWindows()) {
            return "\"" + normalized.replace("\"", "\"\"") + "\"";
        }
        return "'" + normalized.replace("'", "'\"'\"'") + "'";
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable == null ? "" : pythonExecutable;
    }

    public void setInstallCommand(String installCommand) {
        this.installCommand = installCommand == null ? "" : installCommand.strip();
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    public void setGates(List<? extends Map<String, ?>> gates) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, ?> gate : gates) {
            normalized.add(new LinkedHashMap<>(gate));
        }
        this.gates = normalized;
    }

    public List<Map<String, Object>> getGates() {
        return gates;
    }

    public boolean isPrepared() {
        return prepared;
    }

    private static CommandResult executeCommand(List<String> command, String cwd, Map<String, String> env)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (cwd != null && !cwd.isBlank()) {
            builder.directory(Path.of(cwd).toFile());
        }
        builder.environment().putAll(env);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (InputStream stream = process.getInputStream()) {
            String output = decodeStdout(stream.readAllBytes());
            int code = process.waitFor();
            return new CommandResult(code, output);
        }
    }

    private static Optional<String> findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            path = System.getenv("Path");
        }
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String separator = Pattern.quote(System.getProperty("path.separator"));
        for (String entry : path.split(separator)) {
            Path candidate = Path.of(entry).resolve(executable);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toString());
            }
            Path exeCandidate = Path.of(entry).resolve(executable + ".exe");
            if (Files.isRegularFile(exeCandidate)) {
                return Optional.of(exeCandidate.toString());
            }
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private List<String> buildShellCommand(String command) {
        if (isWindows()) {
            return List.of("cmd.exe", "/c", command);
        }
        return List.of("bash", "-c", command);
    }

    private List<String> buildProcessCommand(String... args) {
        return List.of(args);
    }

    private String extractCommits(String command) {
        Matcher matcher = COMMITS_RE.matcher(command);
        return matcher.find() ? matcher.group(1) : "0";
    }

    private List<String> getChangedFiles(String commits) {
        try {
            int count = Integer.parseInt(commits);
            List<String> command;
            if (count > 0) {
                command = List.of("git", "diff", "--name-only", "HEAD~" + count + "..", "--diff-filter=ACMR");
            } else {
                command = List.of("git", "diff", "--name-only", "--cached", "--diff-filter=ACMR");
            }
            CommandResult result = executor.execute(command, workspace, commandEnv());
            if (result.returnCode() != 0) {
                return List.of();
            }
            List<String> files = new ArrayList<>();
            for (String line : result.output().split("\\R")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && (trimmed.endsWith(".py") || trimmed.endsWith(".pyi"))) {
                    files.add(trimmed);
                }
            }
            return files;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Set<Integer>> getDiffLineRanges(String commits) {
        try {
            int count = Integer.parseInt(commits);
            List<String> command;
            if (count > 0) {
                command = List.of("git", "diff", "-U0", "HEAD~" + count + "..", "--diff-filter=ACMR");
            } else {
                command = List.of("git", "diff", "-U0", "--cached", "--diff-filter=ACMR");
            }
            CommandResult result = executor.execute(command, workspace, commandEnv());
            if (result.returnCode() != 0) {
                return Map.of();
            }
            return parseUnifiedDiffHunks(result.output());
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, Set<Integer>> parseUnifiedDiffHunks(String diffText) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        String currentFile = null;
        for (String line : diffText.split("\\R")) {
            if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                Matcher matcher = DIFF_FILE_RE.matcher(line);
                if (matcher.matches()) {
                    String path = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    if (path != null && !"/dev/null".equals(path) && line.startsWith("+++ ")) {
                        currentFile = path;
                        result.computeIfAbsent(currentFile, ignored -> new LinkedHashSet<>());
                    }
                }
                continue;
            }
            Matcher matcher = HUNK_RE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int newStart = Integer.parseInt(matcher.group(3));
            int newCount = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
            if (currentFile == null || newCount <= 0) {
                continue;
            }
            Set<Integer> lines = result.computeIfAbsent(currentFile, ignored -> new LinkedHashSet<>());
            for (int lineNo = newStart; lineNo < newStart + newCount; lineNo++) {
                lines.add(lineNo);
            }
        }
        return result;
    }

    private Map<String, Set<Integer>> wholeFileRanges(List<String> changedFiles) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        for (String file : changedFiles) {
            Set<Integer> lines = new LinkedHashSet<>();
            for (int i = 1; i <= 1_000_000; i++) {
                lines.add(i);
            }
            result.put(file, lines);
        }
        return result;
    }

    private String joinQuotedFiles(List<String> changedFiles) {
        List<String> quoted = new ArrayList<>();
        for (String file : changedFiles) {
            quoted.add(quotePath(file, true));
        }
        return String.join(" ", quoted);
    }

    private CommandResult runToolCommand(String command) {
        try {
            ensureEnvironment();
            return executor.execute(buildShellCommand(command), workspace, commandEnv());
        } catch (Exception e) {
            return new CommandResult(1, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private String makeRepoRelative(String filepath) {
        String normalized = filepath.replace("\\", "/");
        String normalizedWorkspace = workspace == null ? "" : workspace.replace("\\", "/");
        if (!normalizedWorkspace.isEmpty() && normalized.startsWith(normalizedWorkspace)) {
            return normalized.substring(normalizedWorkspace.length()).replaceFirst("^/+", "");
        }
        return normalized;
    }

    private FilterResult filterRuffJsonByLineRanges(String rawJson, Map<String, Set<Integer>> lineRanges) {
        try {
            List<Map<String, Object>> violations = MAPPER.readValue(rawJson, new TypeReference<>() {
            });
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> violation : violations) {
                String filepath = makeRepoRelative(String.valueOf(violation.getOrDefault("filename", "")));
                Map<String, Object> location = castMap(violation.get("location"));
                int line = intValue(location.get("row"));
                Set<Integer> allowed = lineRanges.get(filepath);
                if (allowed == null || !allowed.contains(line)) {
                    continue;
                }
                lines.add(filepath + ":" + line + ":" + intValue(location.get("column")) + ": "
                        + violation.getOrDefault("code", "") + " "
                        + violation.getOrDefault("message", ""));
            }
            return lines.isEmpty() ? FilterResult.none() : FilterResult.of(String.join("\n", lines));
        } catch (JsonProcessingException e) {
            return rawJson == null || rawJson.isBlank() ? FilterResult.none() : FilterResult.of(rawJson);
        }
    }

    private FilterResult filterPylintJsonByLineRanges(String rawJson, Map<String, Set<Integer>> lineRanges) {
        try {
            List<Map<String, Object>> violations = MAPPER.readValue(rawJson, new TypeReference<>() {
            });
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> violation : violations) {
                String filepath = makeRepoRelative(String.valueOf(violation.getOrDefault("path", "")));
                int line = intValue(violation.get("line"));
                Set<Integer> allowed = lineRanges.get(filepath);
                if (allowed == null || !allowed.contains(line)) {
                    continue;
                }
                lines.add(filepath + ":" + line + ": [" + violation.getOrDefault("message-id", "") + "] "
                        + violation.getOrDefault("symbol", "") + ": "
                        + violation.getOrDefault("message", ""));
            }
            return lines.isEmpty() ? FilterResult.none() : FilterResult.of(String.join("\n", lines));
        } catch (JsonProcessingException e) {
            return rawJson == null || rawJson.isBlank() ? FilterResult.none() : FilterResult.of(rawJson);
        }
    }

    private FilterResult filterCodespellByLineRanges(String rawOutput, Map<String, Set<Integer>> lineRanges) {
        List<String> lines = new ArrayList<>();
        for (String line : rawOutput.split("\\R")) {
            Matcher matcher = CODESPELL_RE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String filepath = makeRepoRelative(matcher.group(1));
            int lineNumber = Integer.parseInt(matcher.group(2));
            Set<Integer> allowed = lineRanges.get(filepath);
            if (allowed != null && allowed.contains(lineNumber)) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? FilterResult.none() : FilterResult.of(String.join("\n", lines));
    }

    private FilterResult filterMypyByLineRanges(String rawOutput, Map<String, Set<Integer>> lineRanges) {
        List<String> lines = new ArrayList<>();
        for (String line : rawOutput.split("\\R")) {
            Matcher matcher = MYPY_RE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String filepath = makeRepoRelative(matcher.group(1));
            int lineNumber = Integer.parseInt(matcher.group(2));
            Set<Integer> allowed = lineRanges.get(filepath);
            if (allowed != null && allowed.contains(lineNumber)) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? FilterResult.none() : FilterResult.of(String.join("\n", lines));
    }

    private FilterResult filterFormatDiffByLineRanges(String rawDiff, Map<String, Set<Integer>> lineRanges) {
        Map<String, Set<Integer>> formatHunks = parseUnifiedDiffHunks(rawDiff);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Set<Integer>> entry : formatHunks.entrySet()) {
            String filepath = makeRepoRelative(entry.getKey());
            Set<Integer> allowed = lineRanges.get(filepath);
            if (allowed == null) {
                continue;
            }
            Set<Integer> overlap = new LinkedHashSet<>(entry.getValue());
            overlap.retainAll(allowed);
            if (!overlap.isEmpty()) {
                lines.add(filepath + ": formatting differs on changed lines " + overlap);
            }
        }
        return lines.isEmpty() ? FilterResult.none() : FilterResult.of(String.join("\n", lines));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return converted;
        }
        return Map.of();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static List<String> splitShellWords(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String tail(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    private record FilterResult(boolean hasViolations, String output) {
        static FilterResult none() {
            return new FilterResult(false, "");
        }

        static FilterResult of(String output) {
            return new FilterResult(true, output);
        }
    }
}
