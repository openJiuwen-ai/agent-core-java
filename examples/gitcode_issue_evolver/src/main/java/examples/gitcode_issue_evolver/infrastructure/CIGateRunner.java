/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

import examples.gitcode_issue_evolver.infrastructure.ProcessEnvironmentPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Public class CIGateRunner used by the Java parity implementation.
 *
 * @since 1.0
 */
public final class CIGateRunner {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_CONFIG_RESOURCE = "/com/openjiuwen/autoharness/resources/ci_gate.yaml";
    private static final Map<String, String> ACTION_ALIASES = Map.of("check", "lint");
    private static final List<String> MAVEN_HOME_VARIABLES = List.of("MAVEN_HOME", "M2_HOME");
    private static final int MAX_CAPTURED_OUTPUT_BYTES = 4 * 1024 * 1024;
    private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOGGER = LoggerFactory.getLogger(CIGateRunner.class);
    private static final Pattern MAKE_SEGMENT_PATTERN = Pattern.compile("(^|\\s)make\\s+");
    private static final AtomicInteger OUTPUT_THREAD_SEQUENCE = new AtomicInteger();

    private String workspace;
    private final String configPath;
    private final String pythonExecutable;
    private final String installCommand;
    private final List<GateDefinition> gates;
    private final List<DirectGateDefinition> directGates;
    private final Duration planTimeout;
    private final boolean directExecution;
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
        this.directGates = List.of();
        this.planTimeout = Duration.ofMinutes(20);
        this.directExecution = false;
    }

    /**
     * Create a runner for a trusted verification plan expressed as executable arguments.
     *
     * @param workspace process working directory
     * @param commands trusted executable argument lists
     * @param timeout maximum duration for the complete command plan
     */
    public CIGateRunner(String workspace, List<List<String>> commands, Duration timeout) {
        this.workspace = workspace;
        this.configPath = "";
        this.pythonExecutable = "";
        this.installCommand = "";
        this.gates = List.of();
        this.directGates = directGateDefinitions(commands);
        this.planTimeout = validTimeout(timeout);
        this.directExecution = true;
        this.isPrepared = true;
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
        if (directExecution) {
            return runDirect(normalizedAction);
        }
        if (matchGates(normalizedAction).isEmpty()) {
            return CIGateResult.builder()
                    .isPassed(false)
                    .executedCommands(List.of())
                    .errors("No gate matched action=" + normalizedAction)
                    .failureType(VerificationFailureType.CI_INFRASTRUCTURE_FAILED)
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
                Thread.currentThread().interrupt();
            }
            return CIGateResult.builder()
                    .isPassed(false)
                    .executedCommands(commands)
                    .errors("CI gate install command failed: " + value(ex.getMessage()))
                    .failureType(VerificationFailureType.CI_INFRASTRUCTURE_FAILED)
                    .build();
        }
        boolean isAllPassed = true;
        VerificationFailureType failureType = VerificationFailureType.NONE;
        List<String> errors = new ArrayList<>();
        for (GateDefinition gate : matchedGates) {
            String command = normalizeCommand(gate.command());
            commands.add(command);
            GateRunResult result = runGateCommand(gate.name(), command);
            isAllPassed &= result.isPassed();
            failureType = combineFailureTypes(failureType, result.failureType());
            outputs.add("[" + gate.name() + "]\n" + result.output());
            Map<String, Object> gateResult = new LinkedHashMap<>();
            gateResult.put("name", gate.name());
            gateResult.put("passed", result.isPassed());
            gateResult.put("output", result.output());
            gateResult.put("failureType", result.failureType().name());
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
                .failureType(failureType)
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
            return new GateRunResult(name, result.code() == 0, output,
                    result.code() == 0 ? VerificationFailureType.NONE : VerificationFailureType.CHECK_FAILED);
        } catch (IOException ex) {
            LOGGER.warn("CI gate execution failed for {}", name, ex);
            return new GateRunResult(name, false, "CI gate execution failed",
                    VerificationFailureType.CI_INFRASTRUCTURE_FAILED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("CI gate execution was interrupted for {}", name, ex);
            return new GateRunResult(name, false, "CI gate execution was interrupted",
                    VerificationFailureType.CI_INFRASTRUCTURE_FAILED);
        }
    }

    private CIGateResult runDirect(String action) {
        List<DirectGateDefinition> matchedGates = matchDirectGates(action);
        if (matchedGates.isEmpty()) {
            return CIGateResult.builder()
                    .isPassed(false)
                    .executedCommands(List.of())
                    .errors("No gate matched action=" + action)
                    .failureType(VerificationFailureType.CI_INFRASTRUCTURE_FAILED)
                    .build();
        }
        boolean allPassed = true;
        VerificationFailureType failureType = VerificationFailureType.NONE;
        List<String> commands = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        List<Map<String, Object>> gateResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long deadlineNanos = planDeadlineNanos(planTimeout);
        for (DirectGateDefinition gate : matchedGates) {
            List<String> command = platformCommand(gate.command(), System.getProperty("os.name", ""));
            commands.add(String.join(" ", command));
            Duration remaining = remainingDuration(deadlineNanos);
            GateRunResult result = remaining.isZero()
                    ? new GateRunResult(gate.name(), false, "verification plan timed out",
                            VerificationFailureType.CHECK_FAILED)
                    : runDirectCommand(gate.name(), command, remaining);
            allPassed &= result.isPassed();
            failureType = combineFailureTypes(failureType, result.failureType());
            outputs.add("[" + gate.name() + "]\n" + result.output());
            Map<String, Object> gateResult = new LinkedHashMap<>();
            gateResult.put("name", gate.name());
            gateResult.put("passed", result.isPassed());
            gateResult.put("output", result.output());
            gateResult.put("failureType", result.failureType().name());
            gateResults.add(gateResult);
            if (!result.isPassed() && !result.output().isBlank()) {
                errors.add("[" + gate.name() + "]\n" + result.output().strip());
            }
        }
        return CIGateResult.builder()
                .isPassed(allPassed)
                .executedCommands(commands)
                .gateOutputs(outputs)
                .gates(gateResults)
                .errors(String.join("\n\n", errors))
                .failureType(failureType)
                .build();
    }

    private GateRunResult runDirectCommand(String name, List<String> command, Duration timeout) {
        try {
            ProcessResult result = runProcess(command, timeout, false);
            String output = sanitizeFailureOutput(result.output()).strip();
            if (output.length() > 4000) {
                output = output.substring(output.length() - 4000);
            }
            return new GateRunResult(name, result.code() == 0, output,
                    result.code() == 0 ? VerificationFailureType.NONE : VerificationFailureType.CHECK_FAILED);
        } catch (IOException ex) {
            LOGGER.warn("CI gate execution failed for {}", name, ex);
            return new GateRunResult(name, false, "CI gate execution failed",
                    VerificationFailureType.CI_INFRASTRUCTURE_FAILED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("CI gate execution was interrupted for {}", name, ex);
            return new GateRunResult(name, false, "CI gate execution was interrupted",
                    VerificationFailureType.CI_INFRASTRUCTURE_FAILED);
        }
    }

    private ProcessResult runShell(String command) throws IOException, InterruptedException {
        return runProcess(List.of("bash", "-c", command), Duration.ofMinutes(20), true);
    }

    private ProcessResult runProcess(List<String> command, Duration timeout,
                                     boolean configurePython) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        ProcessEnvironmentPolicy.sanitize(builder);
        Path workingDirectory = Path.of(workspace == null || workspace.isBlank() ? "." : workspace)
                .toAbsolutePath()
                .normalize();
        builder.command(resolveExecutable(command, builder.environment(), workingDirectory,
                System.getProperty("os.name", "")));
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("CI", "1");
        if (configurePython) {
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
                            : binDir + java.io.File.pathSeparator + existingPath);
                }
            }
        }
        Process process = builder.start();
        FutureTask<String> outputTask = new FutureTask<>(() -> readProcessOutput(process));
        Thread outputThread = new Thread(outputTask,
                "auto-harness-ci-output-" + OUTPUT_THREAD_SEQUENCE.incrementAndGet());
        outputThread.setDaemon(true);
        outputThread.setUncaughtExceptionHandler((thread, failure) ->
                LOGGER.error("Uncaught exception in {}", thread.getName(), failure));
        outputThread.start();
        boolean isFinished;
        try {
            isFinished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            terminate(process, outputTask);
            throw ex;
        }
        if (!isFinished) {
            terminate(process, outputTask);
            return new ProcessResult(124, "command timed out after " + timeout);
        }
        return new ProcessResult(process.exitValue(), awaitOutput(outputTask));
    }

    private static String readProcessOutput(Process process) throws IOException {
        try (InputStream output = process.getInputStream()) {
            byte[] captured = new byte[MAX_CAPTURED_OUTPUT_BYTES];
            byte[] chunk = new byte[8192];
            int position = 0;
            int capturedLength = 0;
            int read;
            while ((read = output.read(chunk)) != -1) {
                int firstCopy = Math.min(read, captured.length - position);
                System.arraycopy(chunk, 0, captured, position, firstCopy);
                int remaining = read - firstCopy;
                if (remaining > 0) {
                    System.arraycopy(chunk, firstCopy, captured, 0, remaining);
                }
                position = (position + read) % captured.length;
                capturedLength = Math.min(captured.length, capturedLength + read);
            }
            byte[] ordered = new byte[capturedLength];
            int start = capturedLength == captured.length ? position : 0;
            int firstCopy = Math.min(capturedLength, captured.length - start);
            System.arraycopy(captured, start, ordered, 0, firstCopy);
            if (firstCopy < capturedLength) {
                System.arraycopy(captured, 0, ordered, firstCopy, capturedLength - firstCopy);
            }
            return new String(ordered, StandardCharsets.UTF_8);
        }
    }

    private static String awaitOutput(FutureTask<String> outputTask) throws IOException, InterruptedException {
        try {
            return outputTask.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Unable to collect CI gate output", cause);
        } catch (TimeoutException ex) {
            boolean cancelled = outputTask.cancel(true);
            LOGGER.warn("Timed out collecting CI gate output; reader cancellation={}", cancelled, ex);
            throw new IOException("Timed out collecting CI gate output", ex);
        }
    }

    private static void terminate(Process process, FutureTask<String> outputTask) {
        if (!ProcessLifecycle.terminateAndWait(process, PROCESS_TERMINATION_TIMEOUT)) {
            LOGGER.error("CI process tree did not terminate before the cleanup deadline");
        }
        boolean cancelled = outputTask.cancel(true);
        LOGGER.debug("Requested cancellation of CI output reader: {}", cancelled);
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

    private List<DirectGateDefinition> matchDirectGates(String action) {
        if ("all".equals(action)) {
            return List.copyOf(directGates);
        }
        String target = ACTION_ALIASES.getOrDefault(action, action);
        return directGates.stream()
                .filter(gate -> gate.name().equals(target))
                .toList();
    }

    private static List<DirectGateDefinition> directGateDefinitions(List<List<String>> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<DirectGateDefinition> definitions = new ArrayList<>();
        for (List<String> command : commands) {
            if (command == null || command.isEmpty() || command.get(0) == null
                    || command.get(0).isBlank()) {
                throw new IllegalArgumentException("verification command executable must not be blank");
            }
            List<String> copied = new ArrayList<>();
            for (String argument : command) {
                if (argument == null) {
                    throw new IllegalArgumentException("verification command argument must not be null");
                }
                copied.add(argument);
            }
            definitions.add(new DirectGateDefinition(
                    "verification-" + (definitions.size() + 1), List.copyOf(copied)));
        }
        return List.copyOf(definitions);
    }

    private static Duration validTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofMinutes(20);
        }
        return timeout;
    }

    static List<String> platformCommand(List<String> command, String osName) {
        List<String> resolved = command == null ? new ArrayList<>() : new ArrayList<>(command);
        if (!resolved.isEmpty() && "mvn".equals(resolved.get(0))
                && value(osName).toLowerCase(Locale.ROOT).startsWith("windows")) {
            resolved.set(0, "mvn.cmd");
        }
        return List.copyOf(resolved);
    }

    static List<String> resolveExecutable(List<String> command, Map<String, String> environment,
                                          Path workspace, String osName) throws IOException {
        if (command == null || command.isEmpty() || command.get(0) == null || command.get(0).isBlank()) {
            throw new IOException("CI executable is not configured");
        }
        List<String> resolved = new ArrayList<>(command);
        Path executable = Path.of(command.get(0));
        if (executable.isAbsolute()) {
            Path normalized = executable.normalize();
            if (!isRunnableFile(normalized, osName)) {
                throw new IOException("CI executable is not available");
            }
            resolved.set(0, normalized.toString());
            return List.copyOf(resolved);
        }
        if (executable.getNameCount() != 1) {
            throw new IOException("CI executable must be absolute or available on PATH");
        }
        String pathValue = environmentValue(environment, "PATH");
        Path normalizedWorkspace = workspace == null ? null : workspace.toAbsolutePath().normalize();
        for (String entry : pathValue.split(Pattern.quote(File.pathSeparator))) {
            Path candidate = executableCandidate(entry, executable);
            if (isTrustedExecutable(candidate, normalizedWorkspace, osName)) {
                resolved.set(0, candidate.toString());
                return List.copyOf(resolved);
            }
        }
        if (isMavenExecutable(executable)) {
            for (String variable : MAVEN_HOME_VARIABLES) {
                Path candidate = executableCandidate(environmentValue(environment, variable),
                        Path.of("bin").resolve(executable));
                if (isTrustedExecutable(candidate, normalizedWorkspace, osName)) {
                    resolved.set(0, candidate.toString());
                    return List.copyOf(resolved);
                }
            }
        }
        throw new IOException("CI executable is not available in trusted tool locations");
    }

    private static Path executableCandidate(String directoryValue, Path executable) {
        String directoryText = stripPathQuotes(directoryValue);
        if (directoryText.isBlank()) {
            return null;
        }
        try {
            Path directory = Path.of(directoryText);
            return directory.isAbsolute() ? directory.resolve(executable).normalize() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isTrustedExecutable(Path candidate, Path workspace, String osName) {
        return candidate != null
                && (workspace == null || !candidate.startsWith(workspace))
                && isRunnableFile(candidate, osName);
    }

    private static boolean isMavenExecutable(Path executable) {
        String name = executable.toString().toLowerCase(Locale.ROOT);
        return "mvn".equals(name) || "mvn.cmd".equals(name) || "mvn.bat".equals(name);
    }

    private static String environmentValue(Map<String, String> environment, String name) {
        if (environment == null) {
            return "";
        }
        String directValue = environment.get(name);
        if (directValue != null) {
            return directValue;
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return value(entry.getValue());
            }
        }
        return "";
    }

    private static boolean isRunnableFile(Path path, String osName) {
        boolean windows = value(osName).toLowerCase(Locale.ROOT).startsWith("windows");
        return Files.isRegularFile(path) && (windows || Files.isExecutable(path));
    }

    private static String stripPathQuotes(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")
                ? text.substring(1, text.length() - 1) : text;
    }

    private static long planDeadlineNanos(Duration timeout) {
        long now = System.nanoTime();
        try {
            return Math.addExact(now, timeout.toNanos());
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration remainingDuration(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining <= 0L ? Duration.ZERO : Duration.ofNanos(remaining);
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
public record GateRunResult(String name, boolean isPassed, String output,
                            VerificationFailureType failureType) {
        /**
         * Create a compatibility gate result with a derived failure type.
         *
         * @param name gate name
         * @param isPassed whether the gate passed
         * @param output safe gate output
         */
        public GateRunResult(String name, boolean isPassed, String output) {
            this(name, isPassed, output,
                    isPassed ? VerificationFailureType.NONE : VerificationFailureType.CHECK_FAILED);
        }
    }

    private record DirectGateDefinition(String name, List<String> command) {
        private DirectGateDefinition {
            command = List.copyOf(command);
        }
    }

    private record ProcessResult(int code, String output) {
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static VerificationFailureType combineFailureTypes(VerificationFailureType current,
                                                                VerificationFailureType next) {
        if (current == VerificationFailureType.CI_INFRASTRUCTURE_FAILED
                || next == VerificationFailureType.CI_INFRASTRUCTURE_FAILED) {
            return VerificationFailureType.CI_INFRASTRUCTURE_FAILED;
        }
        if (current == VerificationFailureType.CHECK_FAILED
                || next == VerificationFailureType.CHECK_FAILED) {
            return VerificationFailureType.CHECK_FAILED;
        }
        return VerificationFailureType.NONE;
    }
}
