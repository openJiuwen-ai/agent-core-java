/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Public class CIGateRunner used by the Java parity implementation.
 *
 * @since 1.0
 */
public class CIGateRunner {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_CONFIG_RESOURCE = "/com/openjiuwen/auto_harness/resources/ci_gate.yaml";
    private static final Map<String, String> ACTION_ALIASES = Map.of("check", "lint");
    private static final Pattern MAKE_SEGMENT_PATTERN = Pattern.compile("(^|\\s)make\\s+");

    private String workspace;
    private final String configPath;
    private final String pythonExecutable;
    private final String installCommand;
    private final List<GateDefinition> gates;
    private boolean isPrepared = false;

    /**
     * Auto-generated for codecheck compliance.
     */
    public CIGateRunner(String workspace, String configPath, String pythonExecutable, String installCommand) {
        this.workspace = workspace;
        this.configPath = configPath == null || configPath.isBlank() ? DEFAULT_CONFIG_RESOURCE : configPath;
        this.pythonExecutable = pythonExecutable == null ? "" : pythonExecutable.trim();
        this.installCommand = installCommand;
        this.gates = loadGates(this.configPath);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CIGateResult run() {
        return run("all");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CIGateResult run(String action) {
        String normalizedAction = action == null ? "all" : action.trim();
        if (matchGates(normalizedAction).isEmpty()) {
            return CIGateResult.builder()
                    .isPassed(false)
                    .executedCommands(List.of())
                    .errors("No gate matched action=" + normalizedAction)
                    .build();
        }
        List<GateDefinition> matchedGates = matchGates(normalizedAction);
        List<String> commands = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        List<Map<String, Object>> gateResults = new ArrayList<>();
        try {
            ensureEnvironment(commands);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {

            }
            return CIGateResult.builder()
                    .isPassed(false)
                    .executedCommands(commands)
                    .errors("CI gate install command failed: " + value(ex.getMessage()))
                    .build();
        }
        boolean isAllPassed = true;
        List<String> errors = new ArrayList<>();
        for (GateDefinition gate : matchedGates) {
            String command = normalizeCommand(gate.command());
            commands.add(command);
            GateRunResult result = runGateCommand(gate.name(), command);
            isAllPassed &= result.isPassed();
            outputs.add("[" + gate.name() + "]\n" + result.output());
            Map<String, Object> gateResult = new LinkedHashMap<>();
            gateResult.put("name", gate.name());
            gateResult.put("passed", result.isPassed());
            gateResult.put("output", result.output());
            gateResults.add(gateResult);
            if (!result.isPassed() && !result.output().isBlank()) {
                errors.add("[" + gate.name() + "]\n" + result.output().strip());
            }
        }
        return CIGateResult.builder()
                .isPassed(isAllPassed)
                .executedCommands(commands)
                .gateOutputs(outputs)
                .gates(gateResults)
                .errors(String.join("\n\n", errors))
                .build();
    }

    private void ensureEnvironment(List<String> executedCommands) throws IOException, InterruptedException {
        if (isPrepared) {
            return;
        }
        if (installCommand == null || installCommand.isBlank()) {
            isPrepared = true;
            return;
        }
        executedCommands.add(installCommand);
        ProcessResult result = runShell(installCommand);
        if (result.code() != 0) {
            throw new IOException(result.output().strip());
        }
        isPrepared = true;
    }

    private GateRunResult runGateCommand(String name, String command) {
        try {
            ProcessResult result = runShell(command);
            String output = sanitizeFailureOutput(result.output()).strip();
            if (output.length() > 4000) {
                output = output.substring(output.length() - 4000);
            }
            return new GateRunResult(name, result.code() == 0, output);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {

            }
            return new GateRunResult(name, false, value(ex.getMessage()));
        }
    }

    private ProcessResult runShell(String command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
        builder.directory(Path.of(workspace == null || workspace.isBlank() ? "." : workspace).toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("CI", "1");
        String resolvedPython = resolvePythonExecutable();
        builder.environment().put("AUTO_HARNESS_PYTHON", resolvedPython);
        Path pythonPath = Path.of(resolvedPython);
        if (pythonPath.getFileName() != null && pythonPath.getFileName().toString().startsWith("python")) {
            Path binDir = pythonPath.getParent();
            if (binDir != null) {
                Path envRoot = binDir.getParent();
                builder.environment().put("VIRTUAL_ENV", value(envRoot == null ? null : envRoot.toString()));
                String existingPath = builder.environment().getOrDefault("PATH", "");
                builder.environment().put("PATH", existingPath.isBlank()
                        ? binDir.toString()
                        : binDir + ":" + existingPath);
            }
        }
        Process process = builder.start();
        boolean isFinished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
        if (!isFinished) {
            process.destroyForcibly();
            return new ProcessResult(124, "command timed out after " + Duration.ofMinutes(10));
        }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new ProcessResult(process.exitValue(), output);
    }

    private String normalizeCommand(String command) {
        String stripped = value(command).strip();
        String python = shellQuote(resolvePythonExecutable());
        if (!stripped.startsWith("make ")) {
            if (stripped.startsWith("python -m ")) {
                return python + " -m " + stripped.substring("python -m ".length());
            }
            int makeIndex = indexOfMakeSegment(stripped);
            if (makeIndex >= 0) {
                String makeSegment = stripped.substring(makeIndex);
                String normalizedMake = normalizeCommand(makeSegment);
                if (!normalizedMake.equals(makeSegment)) {
                    return (stripped.substring(0, makeIndex) + normalizedMake).strip();
                }
            }
            return command;
        }

        List<String> parts = splitShellLike(stripped);
        if (parts.isEmpty() || !"make".equals(parts.get(0)) || !parts.contains("test")) {
            return command;
        }
        int targetIndex = parts.indexOf("test");
        Map<String, String> assignments = new LinkedHashMap<>();
        for (int i = targetIndex + 1; i < parts.size(); i++) {
            String item = parts.get(i);
            int eq = item.indexOf('=');
            if (eq <= 0) {
                return command;
            }
            assignments.put(item.substring(0, eq), item.substring(eq + 1));
        }
        String testFlags = assignments.getOrDefault("TESTFLAGS", "").strip();
        return (python + " -m pytest " + testFlags).strip();
    }

    private String resolvePythonExecutable() {
        if (!pythonExecutable.isBlank() && Files.isRegularFile(Path.of(pythonExecutable))) {
            return pythonExecutable;
        }
        if (workspace != null && !workspace.isBlank()) {
            Path workspacePython = Path.of(workspace).resolve(".venv/bin/python");
            if (Files.isRegularFile(workspacePython)) {
                return workspacePython.toString();
            }
        }
        return "python";
    }

    private static String sanitizeFailureOutput(String output) {
        String raw = value(output);
        if (raw.isBlank()) {
            return "";
        }
        Map<String, List<String>> collected = new LinkedHashMap<>();
        collected.put("failures", new ArrayList<>());
        collected.put("short test summary info", new ArrayList<>());
        String current = "";
        for (String line : raw.split("\\R", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith("=") && stripped.endsWith("=")) {
                String normalized = stripped.replaceAll("^=+|=+$", "").strip().toLowerCase(Locale.ROOT);
                current = collected.containsKey(normalized) ? normalized : "";
                if (!current.isBlank()) {
                    collected.get(current).add(line);
                }
                continue;
            }
            if (!current.isBlank()) {
                collected.get(current).add(line);
            }
        }
        List<String> sections = new ArrayList<>();
        for (List<String> lines : collected.values()) {
            String section = String.join("\n", lines).strip();
            if (!section.isBlank()) {
                sections.add(section);
            }
        }
        return sections.isEmpty() ? raw.strip() : String.join("\n\n", sections);
    }

    private static int indexOfMakeSegment(String command) {
        java.util.regex.Matcher matcher = MAKE_SEGMENT_PATTERN.matcher(command);
        return matcher.find() ? matcher.start() + matcher.group(1).length() : -1;
    }

    private static List<String> splitShellLike(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if ((ch == '\'' || ch == '"') && quote == 0) {
                quote = ch;
                continue;
            }
            if (quote != 0 && ch == quote) {
                quote = 0;
                continue;
            }
            if (Character.isWhitespace(ch) && quote == 0) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String shellQuote(String value) {
        if (value == null || value.isBlank()) {
            return "''";
        }
        if (value.matches("[A-Za-z0-9_./:@%+=,-]+")) {
            return value;
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    @SuppressWarnings("unchecked")
    private List<GateDefinition> loadGates(String path) {
        try {
            Object loaded;
            if (DEFAULT_CONFIG_RESOURCE.equals(path)) {
                try (InputStream stream = CIGateRunner.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                    if (stream == null) {
                        return List.of();
                    }
                    loaded = new Yaml().load(stream);
                }
            } else {
                Path configFile = Path.of(path);
                if (!Files.exists(configFile)) {
                    return List.of();
                }
                try (InputStream stream = Files.newInputStream(configFile)) {
                    loaded = new Yaml().load(stream);
                }
            }
            Map<String, Object> data = loaded instanceof Map<?, ?> map ? castMap(map) : Map.of();
            Object rawGates = data.get("ci_gates");
            if (!(rawGates instanceof List<?> list)) {
                return List.of();
            }
            List<GateDefinition> parsed = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> rawGate)) {
                    continue;
                }
                Map<String, Object> gate = castMap(rawGate);
                String name = String.valueOf(gate.getOrDefault("name", "")).trim();
                String command = String.valueOf(gate.getOrDefault("command", "")).trim();
                if (name.isBlank() || command.isBlank()) {
                    continue;
                }
                boolean isRequired = Boolean.parseBoolean(String.valueOf(gate.getOrDefault("required", false)));
                parsed.add(new GateDefinition(name, command, isRequired));
            }
            return parsed;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (ClassCastException | IllegalArgumentException ex) {
            return List.of();
        }
    }

    private List<GateDefinition> matchGates(String action) {
        if ("all".equals(action)) {
            return List.copyOf(gates);
        }
        String target = ACTION_ALIASES.getOrDefault(action, action);
        return gates.stream()
                .filter(gate -> gate.name().equals(target))
                .toList();
    }

    private Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getConfigPath() {
        return configPath;
    }

    /**
 * Public record GateDefinition used by the Java parity implementation.
 *
 * @since 1.0
 */
public record GateDefinition(String name, String command, boolean isRequired) {
    }

    /**
 * Public record GateRunResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record GateRunResult(String name, boolean isPassed, String output) {
    }

    private record ProcessResult(int code, String output) {
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
