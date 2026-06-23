/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI REPL helpers for interactive slash-command mode.
 *
 * <p>Mirrors Python's module functions and support classes in
 * {@code openjiuwen/harness/cli/ui/repl.py}.</p>
 */
public class CliRepl {

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\R(.*?)\\R---", Pattern.DOTALL);
    private static final List<String> DEFAULT_SKILL_DIRS = List.of(
            "~/.openjiuwen/workspace/skills",
            "~/.claude/skills",
            "~/.codex/skills",
            "~/.jiuwenclaw/workspace/skills"
    );
    private static final Map<String, String> SLASH_COMMANDS = new LinkedHashMap<>();
    private static final Map<String, String> SLASH_DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, Path> SKILL_COMMANDS = new LinkedHashMap<>();

    static {
        SLASH_COMMANDS.put("/help", "help");
        SLASH_COMMANDS.put("/exit", "exit");
        SLASH_COMMANDS.put("/quit", "exit");
        SLASH_COMMANDS.put("/clear", "clear");
        SLASH_COMMANDS.put("/status", "status");
        SLASH_COMMANDS.put("/cost", "cost");
        SLASH_COMMANDS.put("/compact", "compact");
        SLASH_COMMANDS.put("/sessions", "sessions");
        SLASH_COMMANDS.put("/auto-harness", "auto-harness");

        SLASH_DESCRIPTIONS.put("/help", "Show available commands");
        SLASH_DESCRIPTIONS.put("/exit", "Exit the REPL");
        SLASH_DESCRIPTIONS.put("/clear", "Clear the screen");
        SLASH_DESCRIPTIONS.put("/status", "Show model and token usage");
        SLASH_DESCRIPTIONS.put("/cost", "Show token cost breakdown");
        SLASH_DESCRIPTIONS.put("/compact", "Compact conversation history");
        SLASH_DESCRIPTIONS.put("/sessions", "List saved sessions");
        SLASH_DESCRIPTIONS.put("/config", "Show current configuration");
        SLASH_DESCRIPTIONS.put("/auto-harness", "Run auto-harness optimization");
    }

    private volatile boolean running;

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public static Map<String, String> slashCommands() {
        return new LinkedHashMap<>(SLASH_COMMANDS);
    }

    public static Map<String, String> slashDescriptions() {
        return new LinkedHashMap<>(SLASH_DESCRIPTIONS);
    }

    public static Map<String, Path> skillCommands() {
        return new LinkedHashMap<>(SKILL_COMMANDS);
    }

    public static List<SlashCompletion> slashCompletions(String textBeforeCursor) {
        String text = textBeforeCursor == null ? "" : textBeforeCursor;
        if (text.contains(" ") || !text.startsWith("/")) {
            return List.of();
        }
        return SLASH_COMMANDS.keySet().stream()
                .sorted()
                .filter(command -> !"/quit".equals(command))
                .filter(command -> command.startsWith(text))
                .map(command -> new SlashCompletion(command, SLASH_DESCRIPTIONS.getOrDefault(command, "")))
                .toList();
    }

    public static List<String> defaultSkillDirs() {
        return new ArrayList<>(DEFAULT_SKILL_DIRS);
    }

    public static List<HelpRow> autoHarnessHelpRows() {
        return List.of(
                new HelpRow(
                        "/auto-harness run [--task TOPIC] [--goal TEXT] [--pipeline meta|extended|auto] "
                                + "[--dry-run] [--no-push] [--budget N]",
                        "Run optimization cycle"
                ),
                new HelpRow("/auto-harness experience list [--type TYPE]", "List experience records"),
                new HelpRow("/auto-harness gap-analyze", "Analyze gaps"),
                new HelpRow("/auto-harness history [--limit N]", "Show recent session history")
        );
    }

    public static void resetSkillCommands() {
        for (String command : new ArrayList<>(SKILL_COMMANDS.keySet())) {
            SLASH_COMMANDS.remove(command);
            SLASH_DESCRIPTIONS.remove(command);
        }
        SKILL_COMMANDS.clear();
    }

    public static String readSkillDescription(Path skillMd) {
        String text;
        try {
            text = Files.readString(skillMd, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        for (String line : matcher.group(1).split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if ("description".equals(line.substring(0, colon).trim())) {
                return line.substring(colon + 1).trim();
            }
        }
        return "";
    }

    public static Map<String, Path> scanSkillDirs() {
        return scanSkillDirs(DEFAULT_SKILL_DIRS.stream().map(CliRepl::expandUserPath).toList());
    }

    public static Map<String, Path> scanSkillDirs(List<Path> roots) {
        Map<String, Path> found = new LinkedHashMap<>();
        for (Path root : roots == null ? List.<Path>of() : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            List<Path> children;
            try (var stream = Files.list(root)) {
                children = stream
                        .filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            } catch (IOException ignored) {
                continue;
            }
            for (Path child : children) {
                Path skillMd = child.resolve("SKILL.md");
                if (!Files.exists(skillMd)) {
                    continue;
                }
                found.putIfAbsent(readSkillName(skillMd, child.getFileName().toString()), skillMd);
            }
        }
        return found;
    }

    public static void registerSkillCommands(Map<String, Path> skills) {
        for (Map.Entry<String, Path> entry : emptyIfNull(skills).entrySet()) {
            String command = "/" + entry.getKey();
            if (SLASH_COMMANDS.containsKey(command)) {
                continue;
            }
            SLASH_COMMANDS.put(command, "skill");
            String description = readSkillDescription(entry.getValue());
            if (description.length() > 60) {
                description = description.substring(0, 57) + "...";
            }
            SLASH_DESCRIPTIONS.put(command, description);
            SKILL_COMMANDS.put(command, entry.getValue());
        }
    }

    public static String buildSkillQuery(Path skillMd, String args) {
        String content;
        try {
            content = Files.readString(skillMd, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "Error reading skill file: " + skillMd + ". Please check the skill directory.";
        }
        StringBuilder query = new StringBuilder();
        query.append("<skill-instructions>\n");
        query.append(content);
        query.append("\n</skill-instructions>");
        if (args != null && !args.isBlank()) {
            query.append("\n\nUser request: ").append(args);
        } else {
            query.append("\n\nPlease follow the skill instructions above.");
        }
        return query.toString();
    }

    public static String handleSlash(String text, Appendable output) {
        String command = firstToken(text).toLowerCase();
        if (!SLASH_COMMANDS.containsKey(command)) {
            append(output, "Unknown command: " + command + System.lineSeparator());
            append(output, "Type /help to see available commands." + System.lineSeparator());
            return null;
        }
        String handler = SLASH_COMMANDS.get(command);
        if ("skill".equals(handler)) {
            return SKILL_COMMANDS.containsKey(command) ? command : null;
        }
        if ("help".equals(handler)) {
            append(output, "/help Show this help message" + System.lineSeparator());
            append(output, "/exit Exit OpenJiuWen" + System.lineSeparator());
            append(output, "/status Show token usage and model info" + System.lineSeparator());
        }
        return null;
    }

    public static String handleShell(String command) throws IOException, InterruptedException {
        List<String> args = isWindows() ? List.of("cmd", "/c", command) : List.of("sh", "-c", command);
        Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        process.waitFor();
        return new String(output, StandardCharsets.UTF_8);
    }

    public PreparedRun cmdAutoHarness(String command, String workspace, Path currentDirectory) {
        return cmdAutoHarness(command, workspace, currentDirectory, null);
    }

    public PreparedRun cmdAutoHarness(String command, String workspace, Path currentDirectory, Object backend) {
        String text = command == null ? "" : command.strip();
        if (text.startsWith("/auto-harness")) {
            text = text.substring("/auto-harness".length()).strip();
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("auto-harness subcommand is required");
        }
        List<String> tokens = splitArgs(text);
        String subcommand = tokens.get(0);
        List<String> rest = tokens.subList(1, tokens.size());
        if ("run".equals(subcommand)) {
            return subcmdRun(rest, workspace, currentDirectory, backend);
        }
        if ("gap-analyze".equals(subcommand)) {
            validateGapAnalyzeArgs(rest);
            return PreparedRun.gapAnalyze(workspaceString(workspace));
        }
        return subcmdRun(List.of("--goal", text), workspace, currentDirectory, backend);
    }

    public PreparedRun subcmdRun(List<String> args, String workspace, Path currentDirectory) {
        return subcmdRun(args, workspace, currentDirectory, null);
    }

    public PreparedRun subcmdRun(List<String> args, String workspace, Path currentDirectory, Object backend) {
        RunArgs parsed = parseRunArgs(args);
        String workspaceValue = workspaceString(workspace);
        Path dataDir = Path.of(workspaceValue).resolve("auto_harness");
        Path configPath = dataDir.resolve("config.yaml");
        AutoHarnessConfig config = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString(), workspaceValue);
        config.setDataDir(dataDir.toString());
        if (!isBlank(config.getLocalRepo())
                && (AutoHarnessSchema.isPlaceholderLocalRepo(config.getLocalRepo())
                || !Files.exists(Path.of(config.getLocalRepo())))) {
            config.setLocalRepo("");
        }
        if (isBlank(config.getLocalRepo()) && !isBlank(config.getSuggestedLocalRepo())) {
            config.setLocalRepo(config.getSuggestedLocalRepo());
        }
        if (!isBlank(config.getLocalRepo())) {
            config.setWorkspace(config.getLocalRepo());
        } else if (isBlank(config.getWorkspace())) {
            config.setWorkspace(workspaceValue);
        }
        if (parsed.budget() != null) {
            config.setSessionBudgetSecs(parsed.budget());
        }
        if (parsed.noPush()) {
            config.setGitRemote("");
        }
        if (!isBlank(parsed.goal())) {
            config.setOptimizationGoal(parsed.goal());
        }
        String pipeline = parsed.pipeline() == null
                ? AutoHarnessPipelineNames.META_EVOLVE_PIPELINE
                : parsed.pipeline();
        config.setPipelinePreference(AutoHarnessSchema.normalizePipelinePreference(pipeline));

        List<OptimizationTask> tasks = null;
        if (!isBlank(parsed.task())) {
            OptimizationTask task = new OptimizationTask();
            task.setTopic(parsed.task());
            tasks = List.of(task);
        }

        return new PreparedRun(
                config,
                tasks,
                parsed.dryRun(),
                parsed.noPush(),
                parsed.task(),
                parsed.goal(),
                config.getPipelinePreference(),
                List.of(ToolTrackingRail.class),
                extractBackendAgent(backend),
                currentDirectory,
                configPath
        );
    }

    public static RunArgs parseRunArgs(List<String> args) {
        String task = null;
        boolean dryRun = false;
        boolean noPush = false;
        Double budget = null;
        String goal = null;
        String pipeline = null;
        List<String> safeArgs = args == null ? List.of() : args;
        for (int index = 0; index < safeArgs.size(); ) {
            String arg = safeArgs.get(index);
            if ("--task".equals(arg) && index + 1 < safeArgs.size()) {
                task = safeArgs.get(index + 1);
                index += 2;
            } else if ("--dry-run".equals(arg)) {
                dryRun = true;
                index += 1;
            } else if ("--no-push".equals(arg)) {
                noPush = true;
                index += 1;
            } else if ("--budget".equals(arg) && index + 1 < safeArgs.size()) {
                try {
                    budget = Double.parseDouble(safeArgs.get(index + 1));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("--budget requires a number", exception);
                }
                index += 2;
            } else if ("--goal".equals(arg) && index + 1 < safeArgs.size()) {
                goal = safeArgs.get(index + 1);
                index += 2;
            } else if ("--pipeline".equals(arg) && index + 1 < safeArgs.size()) {
                String rawPipeline = safeArgs.get(index + 1);
                if (!List.of("meta", "extended", "auto").contains(rawPipeline)) {
                    throw new IllegalArgumentException("--pipeline supports only meta, extended, or auto");
                }
                pipeline = rawPipeline;
                index += 2;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        return new RunArgs(task, dryRun, noPush, budget, goal, pipeline);
    }

    public static void validateGapAnalyzeArgs(List<String> args) {
        if (args != null && !args.isEmpty()) {
            throw new IllegalArgumentException("Unknown argument: " + args.get(0));
        }
    }

    public static ActivationInteractionResult handleActivateInteraction(
            String interactionId,
            Map<String, ?> value,
            String rawChoice) {
        if (value == null || !"activate_confirm".equals(String.valueOf(value.get("interaction_type")))) {
            return ActivationInteractionResult.ignored();
        }
        String normalizedChoice = rawChoice == null ? "" : rawChoice.strip().toLowerCase();
        String action;
        if (List.of("", "a", "accept").contains(normalizedChoice)) {
            action = "accept";
        } else if (List.of("r", "reject").contains(normalizedChoice)) {
            action = "reject";
        } else {
            throw new IllegalArgumentException("Please enter A or R");
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("interaction_id", interactionId);
        message.put("action", action);
        return new ActivationInteractionResult(
                true,
                action,
                activationDisplayLines(value),
                message
        );
    }

    public static String extractQuestionText(Object request) {
        Object toolArgs = readMember(request, "tool_args");
        if (toolArgs instanceof Map<?, ?> map) {
            Object query = map.get("query");
            if (query != null && !String.valueOf(query).isBlank()) {
                return String.valueOf(query);
            }
        }
        Object message = readMember(request, "message");
        if (message != null && !String.valueOf(message).isBlank() && !"Please input".equals(message)) {
            return String.valueOf(message);
        }
        return String.valueOf(request);
    }

    public static List<String> splitArgs(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int index = 0; index < (text == null ? "" : text).length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static List<String> activationDisplayLines(Map<String, ?> value) {
        Object rawExtensionName = value.get("extension_name");
        String extensionName = rawExtensionName == null ? "unknown" : String.valueOf(rawExtensionName);
        List<String> lines = new ArrayList<>();
        lines.add("Extension " + extensionName + " is ready");
        Object runtimePath = value.get("runtime_path");
        if (runtimePath != null && !String.valueOf(runtimePath).isBlank()) {
            lines.add("path: " + runtimePath);
        }
        Object summary = value.get("components_summary");
        if (summary instanceof Map<?, ?> map && !map.isEmpty()) {
            lines.add("components: "
                    + intValue(map.get("rails")) + " rails, "
                    + intValue(map.get("tools")) + " tools, "
                    + intValue(map.get("skills")) + " skills");
        }
        lines.add("A accept and hot-load");
        lines.add("R reject and clean up");
        return lines;
    }

    private static String readSkillName(Path skillMd, String defaultName) {
        String text;
        try {
            text = Files.readString(skillMd, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return defaultName;
        }
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return defaultName;
        }
        for (String line : matcher.group(1).split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if ("name".equals(line.substring(0, colon).trim())) {
                String name = line.substring(colon + 1).trim();
                return name.isBlank() ? defaultName : name;
            }
        }
        return defaultName;
    }

    private static Path expandUserPath(String raw) {
        if (raw.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(raw.substring(2));
        }
        return Path.of(raw);
    }

    private static Object extractBackendAgent(Object backend) {
        if (backend == null) {
            return null;
        }
        Object value = readMember(backend, "agent");
        return value == null ? readMember(backend, "_agent") : value;
    }

    private static Object readMember(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            // Fall through to getter lookup.
        }
        try {
            String methodName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String firstToken(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip().split("\\s+", 2)[0];
    }

    private static String workspaceString(String workspace) {
        return isBlank(workspace) ? Path.of("").toAbsolutePath().toString() : workspace;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static <K, V> Map<K, V> emptyIfNull(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }

    private static void append(Appendable output, String text) {
        try {
            output.append(text);
        } catch (IOException ignored) {
            // Tests use StringBuilder; production callers can ignore rendering failures.
        }
    }

    public record HelpRow(String command, String description) {
    }

    public record RunArgs(
            String task,
            boolean dryRun,
            boolean noPush,
            Double budget,
            String goal,
            String pipeline) {
    }

    /**
     * Mirrors Python's {@code Completion} values yielded by {@code SlashCompleter} in
     * {@code openjiuwen/harness/cli/ui/repl.py}.
     */
    public record SlashCompletion(String text, String displayMeta) {
    }

    public record PreparedRun(
            AutoHarnessConfig config,
            List<OptimizationTask> tasks,
            boolean dryRun,
            boolean noPush,
            String task,
            String goal,
            String pipelinePreference,
            List<Class<?>> streamRails,
            Object backendAgent,
            Path currentDirectory,
            Path configPath) {

        private static PreparedRun gapAnalyze(String workspace) {
            AutoHarnessConfig config = new AutoHarnessConfig();
            config.setWorkspace(workspace);
            return new PreparedRun(
                    config,
                    null,
                    false,
                    false,
                    null,
                    null,
                    AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO,
                    List.of(),
                    null,
                    null,
                    null
            );
        }

        public List<Map<String, Object>> dryRunPayload() {
            if (tasks == null) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (OptimizationTask taskItem : tasks) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("topic", taskItem.getTopic());
                data.put("description", taskItem.getDescription());
                data.put("files", taskItem.getFiles());
                result.add(data);
            }
            return result;
        }
    }

    public record ActivationInteractionResult(
            boolean handled,
            String action,
            List<String> displayLines,
            Map<String, Object> message) {

        private static ActivationInteractionResult ignored() {
            return new ActivationInteractionResult(false, "", List.of(), Map.of());
        }
    }
}
