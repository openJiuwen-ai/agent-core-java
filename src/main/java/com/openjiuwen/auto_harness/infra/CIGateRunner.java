/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * CI gate runner for auto-harness.
 * <p>
 * Mirrors Python's {@code CIGateRunner} in {@code openjiuwen.auto_harness.infra.ci_gate_runner}.
 */
public class CIGateRunner {

    private static final String DEFAULT_YAML = "openjiuwen/auto_harness/resources/ci_gate.yaml";

    private String workspace;
    private String pythonExecutable;
    private String installCommand;
    private boolean prepared;
    private List<Map<String, Object>> gates;
    private final CommandExecutor executor;

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
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadGates(String path) {
        try {
            if (!Files.exists(Path.of(path))) {
                return List.of();
            }
            Object parsed = new Yaml().load(Files.readString(Path.of(path)));
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
        } catch (Exception e) {
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
        String command = normalizeCommand(rawCommand);
        String name = String.valueOf(gate.getOrDefault("name", "unknown"));
        try {
            ensureEnvironment();
            CommandResult result = executor.execute(List.of("bash", "-c", command), workspace, commandEnv());
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

    private void ensureEnvironment() throws IOException, InterruptedException {
        if (prepared) {
            return;
        }
        if (installCommand.isBlank()) {
            prepared = true;
            return;
        }
        CommandResult result = executor.execute(List.of("bash", "-c", installCommand), workspace, commandEnv());
        if (result.returnCode() != 0) {
            throw new IllegalStateException("CI gate install command failed: " + tail(result.output().strip(), 1000));
        }
        prepared = true;
    }

    public String normalizeCommand(String command) {
        String stripped = command.strip();
        String python = quoteShell(resolvePythonExecutable());
        if (!stripped.startsWith("make ")) {
            if (stripped.startsWith("python -m ")) {
                return stripped.replaceFirst("^python -m ", java.util.regex.Matcher.quoteReplacement(python + " -m "));
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

        String[] parts = stripped.split("\\s+");
        if (parts.length == 0 || !"make".equals(parts[0])) {
            return command;
        }
        int testIndex = -1;
        for (int i = 1; i < parts.length; i++) {
            if ("test".equals(parts[i])) {
                testIndex = i;
                break;
            }
        }
        if (testIndex < 0) {
            return command;
        }
        String testFlags = "";
        for (int i = testIndex + 1; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals < 0) {
                return command;
            }
            if ("TESTFLAGS".equals(parts[i].substring(0, equals))) {
                testFlags = parts[i].substring(equals + 1).strip();
            }
        }
        return (python + " -m pytest " + testFlags).strip();
    }

    public String resolvePythonExecutable() {
        List<String> candidates = new ArrayList<>();
        if (!pythonExecutable.isBlank()) {
            candidates.add(pythonExecutable);
        }
        if (workspace != null && !workspace.isBlank()) {
            candidates.add(Path.of(workspace).resolve(".venv").resolve("bin").resolve("python").toString());
        }
        candidates.add(System.getProperty("java.home", ""));

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return findOnPath("python3").or(() -> findOnPath("python")).orElse("python");
    }

    public Map<String, String> commandEnv() {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("CI", "1");
        String resolvedPython = resolvePythonExecutable();
        env.put("AUTO_HARNESS_PYTHON", resolvedPython);
        Path pythonPath = Path.of(resolvedPython);
        if (pythonPath.getFileName() != null
                && pythonPath.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("python")) {
            Path binDir = pythonPath.getParent();
            if (binDir != null) {
                env.put("VIRTUAL_ENV", String.valueOf(binDir.getParent()));
                String existingPath = env.getOrDefault("PATH", env.getOrDefault("Path", ""));
                env.put("PATH", existingPath.isBlank() ? binDir.toString() : binDir + System.getProperty("path.separator") + existingPath);
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
            String output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
        for (String entry : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
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

    private static String quoteShell(String value) {
        if (value.matches("[A-Za-z0-9_./:\\\\-]+")) {
            return value;
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String tail(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }
}
