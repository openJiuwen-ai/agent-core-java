/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.patterns;

import com.openjiuwen.harness.security.fileguard.FileGuardAction;
import com.openjiuwen.harness.security.files.PathAccessExtractor.PathAccess;
import com.openjiuwen.harness.security.shellast.ShellAst;
import com.openjiuwen.harness.security.shellast.ShellAstParseResult;
import com.openjiuwen.harness.security.tiered.ToolCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists user-chosen "always allow" decisions into the agent {@code permissions} YAML.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.patterns.merge_permission_allow_rule_into_permissions},
 * {@code merge_file_guard_access_allows} and {@code write_permissions_section_to_agent_config_yaml}.
 * The merge helpers operate on independent deep copies (never mutate the input) and de-duplicate
 * {@code approval_overrides} by id. {@link #write} loads the whole agent YAML, replaces only the
 * top-level {@code permissions} section and dumps it back via a temp-file + atomic move so a
 * failure never leaves a half-written config.
 *
 * @since 0.1.15
 */
public final class PermissionsYamlWriter {

    private static final Logger logger = LoggerFactory.getLogger(PermissionsYamlWriter.class);

    private static final String SHELL_COMMAND_KEY = "command";
    private static final String SHELL_CMD_KEY = "cmd";
    private static final String MATCH_COMMAND = "command";
    private static final String ACTION_ALLOW = "allow";
    private static final String MATCH_PREFIX = "prefix";

    private PermissionsYamlWriter() {
    }

    /**
     * Merge a tool-level "always allow" decision into a fresh copy of {@code permissions}.
     *
     * <p>For shell tools the command is parsed with {@link ShellAst}; risky structures
     * (pipes, redirects, heredocs, command substitution, …) short-circuit to no override
     * (fail-closed, mirroring Python {@code build_shell_permission_suggestions}). Surviving
     * simple commands are persisted with the exact command text as the pattern, mirroring
     * Python {@code _build_single_shell_suggestion}'s exact scope. Non-shell tools are
     * elevated to {@code tools.<toolName> = allow}.
     *
     * @param permissions permissions section (not mutated)
     * @param toolName   tool name
     * @param toolArgs   tool arguments (e.g. {@code command} for shell tools)
     * @return a new merged permissions map
     * @since 0.1.15
     */
    public static Map<String, Object> mergeAllowRule(Map<String, Object> permissions,
                                                     String toolName,
                                                     Map<String, Object> toolArgs) {
        Map<String, Object> perms = deepCopyMap(permissions);
        if (toolName == null) {
            return perms;
        }
        Map<String, Object> args = toolArgs != null ? toolArgs : Map.of();
        if (ToolCategory.SHELL_TOOLS.contains(toolName)) {
            mergeShellAllow(perms, toolName, args);
        } else {
            mergeToolAllow(perms, toolName);
        }
        return perms;
    }

    /**
     * Merge path-level "always allow" accesses into {@code file_guard.paths}.
     *
     * <p>Mirrors Python {@code merge_file_guard_access_allows} + {@code merge_file_guard_path_rule}:
     * the access path itself is trusted (no parent roll-up) and the read/write/exec axes are
     * derived from the access action (write&#x21d2;read+write allow, exec&#x21d2;read+exec allow,
     * read&#x21d2;read allow). Existing entries for the same path are escalated toward
     * {@code allow} without downgrading any previously granted axis.
     *
     * @param permissions permissions section (not mutated)
     * @param accesses   extracted path accesses
     * @return a new merged permissions map
     * @since 0.1.15
     */
    public static Map<String, Object> mergeFileGuardAccessAllows(Map<String, Object> permissions,
                                                                  List<PathAccess> accesses) {
        Map<String, Object> perms = deepCopyMap(permissions);
        if (accesses == null || accesses.isEmpty()) {
            return perms;
        }
        for (PathAccess access : accesses) {
            if (access == null || access.getPath() == null) {
                continue;
            }
            String pathNorm = normalizePath(access.getPath());
            if (pathNorm.isEmpty()) {
                continue;
            }
            String[] axes = axesForFileGuardAction(access.getAction());
            mergeFileGuardPathRule(perms, pathNorm, axes[0], axes[1], axes[2], MATCH_PREFIX);
        }
        return perms;
    }

    /**
     * Write the {@code permissions} section back to the agent YAML, preserving every other key.
     *
     * <p>The target file must already exist (the persistence flow reads then replaces the
     * original config). A failure at any point returns {@code false} without leaving a
     * half-written file (content is rendered in memory and staged via a temp file, then
     * atomically moved over the original).
     *
     * @param yamlPath     agent config YAML path; {@code null} or missing file &#x2192; {@code false}
     * @param permissions  permissions section to persist (may be {@code null})
     * @return {@code true} when the file was rewritten successfully
     * @since 0.1.15
     */
    public static boolean write(Path yamlPath, Map<String, Object> permissions) {
        if (yamlPath == null) {
            logger.warn("[PermissionEngine] permission.write_yaml.abort reason=no_config_yaml_path");
            return false;
        }
        try {
            Path resolved = yamlPath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(resolved)) {
                logger.warn("[PermissionEngine] permission.write_yaml.abort reason=file_not_found path={}",
                        resolved);
                return false;
            }
            Map<String, Object> data = loadAgentConfig(resolved);
            data.put("permissions", deepCopy(permissions));
            String content = new Yaml().dump(data);
            stageAndMove(resolved, content);
            logger.info("[PermissionEngine] permission.write_yaml.ok path={}", resolved);
            return true;
        } catch (Exception ex) {
            logger.error("[PermissionEngine] permission.write_yaml.failed path={}", yamlPath, ex);
            return false;
        }
    }

    private static void mergeShellAllow(Map<String, Object> perms, String toolName, Map<String, Object> args) {
        String command = commandText(args);
        if (command == null || command.isBlank()) {
            return;
        }
        ShellAstParseResult parse = ShellAst.parse(command);
        if (isRiskyParseUnavailable(parse)) {
            logger.warn("[PermissionEngine] permission.merge.skip tool={} reason=risky_command_structure",
                    toolName);
            return;
        }
        String pattern = shellPattern(parse, command);
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        appendApprovalOverride(perms, toolName, MATCH_COMMAND, pattern, ACTION_ALLOW);
    }

    private static void mergeToolAllow(Map<String, Object> perms, String toolName) {
        Map<String, Object> tools = asMutableStringKeyMap(perms.get("tools"));
        tools.put(toolName, ACTION_ALLOW);
        perms.put("tools", tools);
    }

    private static boolean isRiskyParseUnavailable(ShellAstParseResult parse) {
        if (!"parse_unavailable".equals(parse.getKind())) {
            return false;
        }
        return parse.getFlags() != null && parse.getFlags().hasRiskyStructure();
    }

    private static String shellPattern(ShellAstParseResult parse, String command) {
        String text = command;
        if ("simple".equals(parse.getKind())
                && parse.getSubcommands() != null
                && !parse.getSubcommands().isEmpty()) {
            String subText = parse.getSubcommands().get(0).getText();
            if (subText != null && !subText.isBlank()) {
                text = subText;
            }
        }
        String pattern = text.strip();
        return pattern.isEmpty() ? null : pattern;
    }

    private static void appendApprovalOverride(Map<String, Object> perms, String toolName,
                                               String matchType, String pattern, String action) {
        List<Object> overrides = asMutableList(perms.get("approval_overrides"));
        perms.put("approval_overrides", overrides);
        String id = buildApprovalOverrideId(toolName, matchType, pattern);
        for (Object item : overrides) {
            if (item instanceof Map<?, ?> m && id.equals(m.get("id"))) {
                logger.info("[PermissionEngine] permission.persist.skip tool={} reason=approval_override_exists id={}",
                        toolName, id);
                return;
            }
        }
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("id", id);
        override.put("tools", new ArrayList<>(List.of(toolName)));
        override.put("match_type", matchType);
        override.put("pattern", pattern);
        override.put("action", action);
        overrides.add(override);
    }

    private static String buildApprovalOverrideId(String toolName, String matchType, String pattern) {
        String raw = "user_allow_" + toolName + "_" + matchType + "_" + pattern;
        String collapsed = raw.replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
        if (collapsed.isEmpty()) {
            return "user_allow_override";
        }
        return collapsed.length() > 120 ? collapsed.substring(0, 120) : collapsed;
    }

    private static String commandText(Map<String, Object> args) {
        Object raw = args.get(SHELL_COMMAND_KEY);
        String value = raw == null ? "" : raw.toString();
        if (value.isEmpty()) {
            Object cmd = args.get(SHELL_CMD_KEY);
            value = cmd == null ? "" : cmd.toString();
        }
        return value.strip();
    }

    private static String[] axesForFileGuardAction(FileGuardAction action) {
        if (action == FileGuardAction.WRITE) {
            return new String[]{ACTION_ALLOW, ACTION_ALLOW, "ask"};
        }
        if (action == FileGuardAction.EXEC) {
            return new String[]{ACTION_ALLOW, "ask", ACTION_ALLOW};
        }
        return new String[]{ACTION_ALLOW, "ask", "ask"};
    }

    @SuppressWarnings("unchecked")
    private static void mergeFileGuardPathRule(Map<String, Object> perms, String pathNorm,
                                               String read, String write, String exec, String match) {
        Map<String, Object> fg = asMutableStringKeyMap(perms.get("file_guard"));
        fg.put("enabled", true);
        List<Object> paths = asMutableList(fg.get("paths"));
        fg.put("paths", paths);
        perms.put("file_guard", fg);

        for (int i = 0; i < paths.size(); i++) {
            if (!(paths.get(i) instanceof Map<?, ?> existingRaw)) {
                continue;
            }
            String existingPath = normalizePath(existingRaw.get("path"));
            if (!existingPath.equals(pathNorm)) {
                continue;
            }
            Map<String, Object> existing = (Map<String, Object>) existingRaw;
            String mergedRead = escalateAxisTowardAllow(existing.get("read"), read);
            String mergedWrite = escalateAxisTowardAllow(existing.get("write"), write);
            String mergedExec = escalateAxisTowardAllow(existing.get("exec"), exec);
            String mergedMatch = existing.containsKey("match") && existing.get("match") != null
                    && !existing.get("match").toString().isEmpty()
                    ? existing.get("match").toString()
                    : match;
            Map<String, Object> merged = new LinkedHashMap<>(existing);
            merged.put("path", pathNorm);
            merged.put("read", mergedRead);
            merged.put("write", mergedWrite);
            merged.put("exec", mergedExec);
            merged.put("match", mergedMatch);
            paths.set(i, merged);
            logger.info("[PermissionEngine] permission.merge.file_guard path={} read={} write={} exec={}",
                    pathNorm, mergedRead, mergedWrite, mergedExec);
            return;
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", pathNorm);
        entry.put("read", read);
        entry.put("write", write);
        entry.put("exec", exec);
        entry.put("match", match);
        paths.add(entry);
        logger.info("[PermissionEngine] permission.merge.file_guard path={} read={} write={} exec={}",
                pathNorm, read, write, exec);
    }

    private static String escalateAxisTowardAllow(Object old, String neu) {
        if (ACTION_ALLOW.equals(neu)) {
            return ACTION_ALLOW;
        }
        if (old != null) {
            String s = old.toString();
            if (ACTION_ALLOW.equals(s) || "ask".equals(s) || "deny".equals(s)) {
                return s;
            }
        }
        return neu;
    }

    private static String normalizePath(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString().replace("\\", "/");
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static Map<String, Object> loadAgentConfig(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map<?, ?> m) {
                return asMutableStringKeyMap(m);
            }
            return new LinkedHashMap<>();
        }
    }

    private static void stageAndMove(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".permissions-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE)) {
                writer.write(content);
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException moveEx) {
                logger.warn("[PermissionEngine] permission.write_yaml.atomic_move_unsupported path={}",
                        target);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            deleteQuietly(temp);
            throw ex;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            logger.warn("[PermissionEngine] permission.write_yaml.cleanup_failed path={}", path, ex);
        }
    }

    private static Object deepCopy(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return copy;
        }
        if (o instanceof List<?> l) {
            List<Object> copy = new ArrayList<>();
            for (Object item : l) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> permissions) {
        if (permissions == null) {
            return new LinkedHashMap<>();
        }
        Object copy = deepCopy(permissions);
        return (Map<String, Object>) copy;
    }

    private static Map<String, Object> asMutableStringKeyMap(Object o) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return result;
    }

    private static List<Object> asMutableList(Object o) {
        List<Object> result = new ArrayList<>();
        if (o instanceof List<?> l) {
            result.addAll(l);
        }
        return result;
    }
}
