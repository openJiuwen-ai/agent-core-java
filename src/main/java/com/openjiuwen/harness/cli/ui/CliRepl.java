/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.openjiuwen.harness.cli.AutoHarnessCliSupport;
import com.openjiuwen.harness.cli.AutoHarnessRunRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI REPL (Read-Eval-Print Loop) for interactive mode.
 * <p>
 * Mirrors Python's {@code repl} in
 * {@code openjiuwen.harness.cli.ui.repl}.
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

    private volatile boolean running = false;

    /** Start the interactive REPL. */
    public void start() {
        running = true;
    }

    /** Stop the REPL. */
    public void stop() {
        running = false;
    }

    /** Check if REPL is running. */
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

    public static List<String> defaultSkillDirs() {
        return new ArrayList<>(DEFAULT_SKILL_DIRS);
    }

    public static void resetSkillCommands() {
        for (String cmd : new ArrayList<>(SKILL_COMMANDS.keySet())) {
            SLASH_COMMANDS.remove(cmd);
            SLASH_DESCRIPTIONS.remove(cmd);
        }
        SKILL_COMMANDS.clear();
    }

    public static String readSkillDescription(Path skillMd) {
        String text;
        try {
            text = Files.readString(skillMd);
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
        List<Path> roots = DEFAULT_SKILL_DIRS.stream()
                .map(CliRepl::expandUserPath)
                .toList();
        return scanSkillDirs(roots);
    }

    public static Map<String, Path> scanSkillDirs(List<Path> roots) {
        Map<String, Path> found = new LinkedHashMap<>();
        for (Path root : roots) {
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
            for (Path item : children) {
                Path skillMd = item.resolve("SKILL.md");
                if (!Files.exists(skillMd)) {
                    continue;
                }
                String name = readSkillName(skillMd, item.getFileName().toString());
                found.putIfAbsent(name, skillMd);
            }
        }
        return found;
    }

    public static void registerSkillCommands(Map<String, Path> skills) {
        for (Map.Entry<String, Path> entry : skills.entrySet()) {
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
            content = Files.readString(skillMd);
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
        String command = text == null || text.isBlank()
                ? ""
                : text.strip().split("\\s+", 2)[0].toLowerCase();
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
        List<String> args;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            args = List.of("cmd", "/c", command);
        } else {
            args = List.of("sh", "-c", command);
        }
        Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        process.waitFor();
        return new String(output, StandardCharsets.UTF_8);
    }

    public AutoHarnessCliSupport.PreparedRun subcmdRun(List<String> args, String workspace, Path currentDirectory) {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        List<String> safeArgs = args != null ? args : List.of();
        for (int index = 0; index < safeArgs.size(); index++) {
            String arg = safeArgs.get(index);
            if ("--goal".equals(arg) && index + 1 < safeArgs.size()) {
                request.setGoal(safeArgs.get(++index));
            } else if ("--stage".equals(arg) && index + 1 < safeArgs.size()) {
                request.setStage(safeArgs.get(++index));
            } else if ("--task".equals(arg) && index + 1 < safeArgs.size()) {
                request.setTask(safeArgs.get(++index));
            }
        }
        return AutoHarnessCliSupport.prepareRun(options(workspace), request, currentDirectory);
    }

    public AutoHarnessCliSupport.PreparedRun cmdAutoHarness(String command, String workspace, Path currentDirectory) {
        String text = command == null ? "" : command.strip();
        if (text.startsWith("/auto-harness")) {
            text = text.substring("/auto-harness".length()).strip();
        }
        if (text.startsWith("run ")) {
            return subcmdRun(List.of("--goal", text.substring("run ".length()).strip()), workspace, currentDirectory);
        }
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setGoal(text);
        return AutoHarnessCliSupport.prepareRun(options(workspace), request, currentDirectory);
    }

    private static AutoHarnessCliSupport.CliOptions options(String workspace) {
        AutoHarnessCliSupport.CliOptions opts = new AutoHarnessCliSupport.CliOptions();
        opts.setWorkspace(workspace != null ? workspace : "");
        return opts;
    }

    private static String readSkillName(Path skillMd, String defaultName) {
        String text;
        try {
            text = Files.readString(skillMd);
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

    private static void append(Appendable output, String text) {
        try {
            output.append(text);
        } catch (IOException ignored) {
            // Tests use StringBuilder; production callers can ignore rendering failures.
        }
    }
}
