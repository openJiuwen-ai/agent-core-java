/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.files;

import com.openjiuwen.harness.security.fileguard.FileGuardAction;
import com.openjiuwen.harness.security.shellast.ShellAst;
import com.openjiuwen.harness.security.shellast.ShellAstParseResult;
import com.openjiuwen.harness.security.shellast.ShellSubcommand;
import com.openjiuwen.harness.security.tiered.ToolCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code (path, action, source)} accesses from a tool invocation.
 *
 * <p>Mirrors Python {@code files.extract.extract_accesses_native} (registry-driven
 * extraction for file tools plus shell path extraction for command tools) and the
 * legacy command-string extraction. The native path is the one used by
 * {@link com.openjiuwen.harness.security.fileguard.FileGuardChecker}.
 *
 * <p>Deviation from Python: the registry of file-tool specs is embedded here rather
 * than exposed as a separate mutable registry module, since the Java port does not
 * yet support runtime tool registration. The builtin specs mirror Python
 * {@code files.registry._bootstrap_builtin_specs}.
 *
 * @since 0.1.15
 */
public final class PathAccessExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PathAccessExtractor.class);

    private static final Set<String> SHELL_TOOLS = ToolCategory.SHELL_TOOLS;
    private static final Set<String> WRITE_PATH_TOOLS = Set.of(
            "write_file", "edit_file", "write_text_file", "write", "search_replace");

    private static final Set<String> PATH_AWARE_COMMANDS = Set.of(
            "cd", "rm", "cp", "mv", "mkdir", "touch", "chmod", "chown", "cat",
            "ls", "dir", "type", "del", "rd", "copy", "move", "md",
            "head", "tail", "more", "less", "vim", "nano", "gedit", "notepad");

    private static final Set<String> READ_CMDS = Set.of(
            "cat", "ls", "dir", "type", "head", "tail", "more", "less");
    private static final Set<String> WRITE_CMDS = Set.of(
            "rm", "mkdir", "touch", "chmod", "chown", "del", "rd", "md");
    private static final Set<String> TRANSFER_CMDS = Set.of("cp", "copy", "mv", "move");

    private static final Set<String> INTERPRETER_BASENAMES = Set.of(
            "python", "python3", "pythonw", "py",
            "node", "nodejs", "bash", "sh", "dash", "zsh", "fish",
            "pwsh", "powershell");

    private static final Pattern REDIRECT_RE =
            Pattern.compile("(?:^|[\\s;|&])(\\d*>>?|\\d*<|&>)\\s*([^\\s;|&<>]+)");

    private PathAccessExtractor() {
    }

    /** A single extracted access. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathAccess {
        /** Resolved path. */
        private Path path;
        /** Inferred file-access axis. */
        private FileGuardAction action;
        /** Origin: {@code tool_arg} or {@code shlex}. */
        private String source;
    }

    private record Spec(String argName, FileGuardAction action) {
    }

    private static final Map<String, List<Spec>> FILE_TOOL_SPECS = buildBuiltinSpecs();

    /**
     * Extract accesses from a tool invocation (native path).
     *
     * @param toolName     tool name
     * @param toolArgs     tool arguments
     * @param workspaceRoot workspace root for resolving relative paths
     * @return extracted accesses
     * @since 0.1.15
     */
    public static List<PathAccess> extractNative(String toolName, Map<String, Object> toolArgs,
                                                 Path workspaceRoot) {
        List<PathAccess> out = new ArrayList<>();
        if (toolName == null) {
            return out;
        }
        Map<String, Object> args = toolArgs != null ? toolArgs : Map.of();
        if (SHELL_TOOLS.contains(toolName)) {
            Path base = resolveShellWorkdir(args, workspaceRoot);
            String command = commandText(args);
            out.addAll(extractShellPathAccesses(command, base));
            return out;
        }
        List<Spec> specs = FILE_TOOL_SPECS.get(toolName);
        if (specs != null) {
            for (Spec spec : specs) {
                Object raw = args.get(spec.argName());
                if (!(raw instanceof String s) || s.isBlank()) {
                    continue;
                }
                Path p = resolvePathStr(s, workspaceRoot);
                if (p == null) {
                    continue;
                }
                out.add(PathAccess.builder()
                        .path(p)
                        .action(spec.action())
                        .source("tool_arg")
                        .build());
            }
            return out;
        }
        if (ToolCategory.PATH_TOOLS.contains(toolName)) {
            FileGuardAction action = WRITE_PATH_TOOLS.contains(toolName)
                    ? FileGuardAction.WRITE : FileGuardAction.READ;
            for (String s : iterPathStrings(args)) {
                Path p = resolvePathStr(s, workspaceRoot);
                if (p == null) {
                    continue;
                }
                out.add(PathAccess.builder()
                        .path(p)
                        .action(action)
                        .source("tool_arg")
                        .build());
            }
        }
        return out;
    }

    /**
     * Extract accesses from a raw command string (legacy, conservative).
     *
     * <p>Paths are returned unresolved ({@link Path#of}) because the legacy entrypoint
     * has no workspace context; the checker uses the native path instead.
     *
     * @param command command text
     * @return extracted accesses
     * @since 0.1.15
     */
    public static List<PathAccess> extractLegacy(String command) {
        List<PathAccess> out = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return out;
        }
        List<String> tokens = simpleTokenize(command);
        if (tokens.isEmpty()) {
            return out;
        }
        String cmd0 = basenameLower(tokens.get(0));
        if (!PATH_AWARE_COMMANDS.contains(cmd0)) {
            return out;
        }
        out.addAll(extractFromArgv(cmd0, tokens, null));
        out.addAll(extractRedirects(command, null));
        if (tokens.size() >= 2 && INTERPRETER_BASENAMES.contains(cmd0)) {
            String scriptTok = stripQuotes(tokens.get(1));
            if (!scriptTok.isEmpty() && !scriptTok.startsWith("-") && looksLikePath(scriptTok)) {
                out.add(PathAccess.builder()
                        .path(Path.of(scriptTok))
                        .action(FileGuardAction.EXEC)
                        .source("shlex")
                        .build());
            }
        }
        return out;
    }

    // ---------- shell extraction (native) ----------

    private static List<PathAccess> extractShellPathAccesses(String command, Path base) {
        List<PathAccess> out = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return out;
        }
        ShellAstParseResult parse = ShellAst.parse(command);
        List<ShellSubcommand> subs = parse.getSubcommands();
        if (subs != null && !subs.isEmpty()) {
            for (ShellSubcommand sub : subs) {
                List<String> argv = sub.getArgv();
                if (argv == null || argv.isEmpty()) {
                    continue;
                }
                String cmd0 = basenameLower(argv.get(0));
                out.addAll(extractFromArgv(cmd0, argv, base));
            }
        } else {
            List<String> tokens = simpleTokenize(command);
            if (!tokens.isEmpty()) {
                String cmd0 = basenameLower(tokens.get(0));
                if (PATH_AWARE_COMMANDS.contains(cmd0)) {
                    out.addAll(extractFromArgv(cmd0, tokens, base));
                }
            }
        }
        out.addAll(extractRedirects(command, base));
        List<String> tokens = simpleTokenize(command);
        if (tokens.size() >= 2) {
            String cmd0 = basenameLower(tokens.get(0));
            if (INTERPRETER_BASENAMES.contains(cmd0)) {
                String scriptTok = stripQuotes(tokens.get(1));
                if (!scriptTok.isEmpty() && !scriptTok.startsWith("-") && looksLikePath(scriptTok)) {
                    Path p = resolvePathStr(scriptTok, base);
                    if (p != null) {
                        out.add(PathAccess.builder()
                                .path(p)
                                .action(FileGuardAction.EXEC)
                                .source("shlex")
                                .build());
                    }
                }
            }
        }
        return out;
    }

    private static List<PathAccess> extractFromArgv(String cmd0, List<String> tokens, Path base) {
        List<PathAccess> out = new ArrayList<>();
        List<Path> pathTokens = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            String tok = stripQuotes(tokens.get(i));
            if (tok.isEmpty() || isShellFlag(tok) || !looksLikePath(tok)) {
                continue;
            }
            Path p = base != null ? resolvePathStr(tok, base) : rawPathOrNull(tok);
            if (p != null) {
                pathTokens.add(p);
            }
        }
        if (TRANSFER_CMDS.contains(cmd0) && pathTokens.size() >= 2) {
            out.add(access(pathTokens.get(0), FileGuardAction.READ, base));
            for (int i = 1; i < pathTokens.size(); i++) {
                out.add(access(pathTokens.get(i), FileGuardAction.WRITE, base));
            }
        } else if (WRITE_CMDS.contains(cmd0)) {
            for (Path p : pathTokens) {
                out.add(access(p, FileGuardAction.WRITE, base));
            }
        } else if (READ_CMDS.contains(cmd0)) {
            for (Path p : pathTokens) {
                out.add(access(p, FileGuardAction.READ, base));
            }
        } else if ("cd".equals(cmd0)) {
            for (Path p : pathTokens) {
                out.add(access(p, FileGuardAction.READ, base));
            }
        } else {
            for (Path p : pathTokens) {
                out.add(access(p, FileGuardAction.WRITE, base));
            }
        }
        return out;
    }

    private static PathAccess access(Path resolved, FileGuardAction action, Path base) {
        return PathAccess.builder()
                .path(resolved)
                .action(action)
                .source("shlex")
                .build();
    }

    private static List<PathAccess> extractRedirects(String command, Path base) {
        List<PathAccess> out = new ArrayList<>();
        Matcher m = REDIRECT_RE.matcher(command);
        while (m.find()) {
            String op = m.group(1);
            String target = stripQuotes(m.group(2));
            if (target.isEmpty() || !looksLikePath(target)) {
                continue;
            }
            Path p = base != null ? resolvePathStr(target, base) : rawPathOrNull(target);
            if (p == null) {
                continue;
            }
            FileGuardAction action = (op.contains("<") && !op.contains(">"))
                    ? FileGuardAction.READ : FileGuardAction.WRITE;
            out.add(access(p, action, base));
        }
        return out;
    }

    // ---------- path resolution ----------

    private static Path resolvePathStr(String raw, Path workspace) {
        String s = stripQuotes(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        s = expandRaw(s);
        try {
            Path p = Paths.get(s);
            if (!p.isAbsolute() && workspace != null) {
                p = workspace.resolve(s);
            }
            return p.normalize();
        } catch (RuntimeException ex) {
            logger.warn("[file_guard] path.resolve_failed raw={} reason={}", s, ex.getMessage());
            return null;
        }
    }

    private static Path rawPathOrNull(String tok) {
        try {
            return Paths.get(tok).normalize();
        } catch (java.nio.file.InvalidPathException ex) {
            return null;
        }
    }

    private static Path resolveShellWorkdir(Map<String, Object> args, Path workspaceRoot) {
        Object rawWd = args.get("workdir");
        if (rawWd instanceof String wd && !wd.isBlank()) {
            Path wdP = Paths.get(wd);
            if (wdP.isAbsolute()) {
                return wdP.normalize();
            }
            if (workspaceRoot != null) {
                return workspaceRoot.resolve(wd).normalize();
            }
        }
        return workspaceRoot;
    }

    private static String commandText(Map<String, Object> args) {
        Object cmd = args.get("command");
        if (cmd == null || cmd.toString().isBlank()) {
            cmd = args.get("cmd");
        }
        return cmd == null ? "" : cmd.toString().trim();
    }

    // ---------- tokenization helpers ----------

    private static List<String> simpleTokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean inContent = false;
        int i = 0;
        int n = command.length();
        while (i < n) {
            char c = command.charAt(i);
            if (Character.isWhitespace(c)) {
                if (inContent) {
                    tokens.add(word.toString());
                    word.setLength(0);
                    inContent = false;
                }
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                i++;
                inContent = true;
                while (i < n && command.charAt(i) != c) {
                    word.append(command.charAt(i));
                    i++;
                }
                if (i < n) {
                    i++;
                }
                continue;
            }
            word.append(c);
            inContent = true;
            i++;
        }
        if (inContent) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    private static String basenameLower(String cmd) {
        String base = cmd.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.toLowerCase(java.util.Locale.ROOT);
        if (base.endsWith(".exe")) {
            base = base.substring(0, base.length() - 4);
        }
        return base;
    }

    private static String stripQuotes(String tok) {
        String s = tok;
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static boolean isShellFlag(String tok) {
        String s = stripQuotes(tok);
        if (s.isEmpty()) {
            return true;
        }
        return s.startsWith("-");
    }

    private static boolean looksLikePath(String token) {
        String t = stripQuotes(token);
        if (t.isEmpty()) {
            return false;
        }
        if (t.equals(".") || t.equals("..")) {
            return true;
        }
        if (t.startsWith("\\\\") || t.startsWith("./") || t.startsWith("../")) {
            return true;
        }
        if (t.length() > 1 && t.charAt(1) == ':') {
            return true;
        }
        return t.contains("/") || t.contains("\\");
    }

    private static List<String> iterPathStrings(Map<String, Object> args) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (!(v instanceof String s) || s.isBlank()) {
                continue;
            }
            if (ToolCategory.PATH_ARG_KEYS.contains(k) || s.contains("/") || s.contains("\\")) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private static String expandRaw(String raw) {
        String s = raw;
        if (s.startsWith("~")) {
            String home = System.getProperty("user.home", "");
            if (s.equals("~")) {
                s = home;
            } else if (s.startsWith("~/")) {
                s = home + s.substring(1);
            }
        }
        return s;
    }

    private static Map<String, List<Spec>> buildBuiltinSpecs() {
        Map<String, List<Spec>> reg = new LinkedHashMap<>();
        addSpec(reg, "read_file", "file_path", FileGuardAction.READ);
        addSpec(reg, "read_file", "path", FileGuardAction.READ);
        addSpec(reg, "write_file", "file_path", FileGuardAction.WRITE);
        addSpec(reg, "write_file", "path", FileGuardAction.WRITE);
        addSpec(reg, "edit_file", "file_path", FileGuardAction.WRITE);
        addSpec(reg, "read_text_file", "file_path", FileGuardAction.READ);
        addSpec(reg, "write_text_file", "file_path", FileGuardAction.WRITE);
        addSpec(reg, "write", "file_path", FileGuardAction.WRITE);
        addSpec(reg, "read", "file_path", FileGuardAction.READ);
        addSpec(reg, "search_replace", "file_path", FileGuardAction.WRITE);
        addSpec(reg, "search_replace", "path", FileGuardAction.WRITE);
        addSpec(reg, "search_replace", "target_file", FileGuardAction.WRITE);
        addSpec(reg, "grep", "path", FileGuardAction.READ);
        addSpec(reg, "grep", "file_path", FileGuardAction.READ);
        addSpec(reg, "glob_file_search", "glob_pattern", FileGuardAction.READ);
        addSpec(reg, "glob", "pattern", FileGuardAction.READ);
        addSpec(reg, "list_dir", "path", FileGuardAction.READ);
        addSpec(reg, "list_files", "path", FileGuardAction.READ);
        return reg;
    }

    private static void addSpec(Map<String, List<Spec>> reg, String tool, String arg, FileGuardAction action) {
        reg.computeIfAbsent(tool, k -> new ArrayList<>()).add(new Spec(arg, action));
    }
}
