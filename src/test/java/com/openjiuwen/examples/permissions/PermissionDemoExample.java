/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.permissions;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.SecurityRail;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import com.openjiuwen.harness.security.PermissionEngine;
import com.openjiuwen.harness.security.PermissionFactory;
import com.openjiuwen.harness.security.PermissionResult;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Permission Demo Example.
 * <p>
 * Demonstrates permission system usage for tool execution control.
 * <p>
 * Mirrors Python's {@code permission_demo} in
 * {@code examples.permissions.permission_demo}.
 */
public final class PermissionDemoExample {

    public static final String DEMO_AGENT_NAME = "permission_demo";
    public static final String DEMO_NL_AGENT_NAME = "permission_demo_nl";
    public static final String SAMPLE_FILE = "notes.txt";

    private PermissionDemoExample() {
    }

    /**
     * Build the tiered permission policy used by the demo.
     *
     * <p>Mirrors Python's {@code example_permissions_dict}.
     */
    public static Map<String, Object> examplePermissionsDict() {
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("read_file", "ask");
        tools.put("write_file", "deny");

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("*", "allow");

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("enabled", true);
        permissions.put("schema", "tiered_policy");
        permissions.put("permission_mode", "normal");
        permissions.put("tools", tools);
        permissions.put("defaults", defaults);
        permissions.put("rules", new ArrayList<>());
        permissions.put("approval_overrides", new ArrayList<>());
        return permissions;
    }

    /**
     * Create host callbacks for permission checks and persistence.
     *
     * <p>Mirrors Python's {@code example_permission_host}.
     */
    public static ToolPermissionHost examplePermissionHost(Path workspace, Path configYaml) {
        Path root = normalizeWorkspace(workspace);
        return ToolPermissionHost.builder()
                .resolveWorkspaceDir(() -> root)
                .permissionYamlPath(configYaml)
                .build();
    }

    /**
     * Evaluate the demo read-file call synchronously.
     *
     * <p>Mirrors Python's {@code demo_sync_permission_engine}.
     */
    public static PermissionResult demoSyncPermissionEngine(Path workspace) {
        Path root = normalizeWorkspace(workspace);
        PermissionEngine engine = new PermissionEngine(permissionsWithWorkspaceRoot(root));
        return engine.checkPermission("read_file", Map.of("path", root.resolve(SAMPLE_FILE).toString()));
    }

    /**
     * Evaluate the same demo read-file call through an async Java surface.
     *
     * <p>Mirrors Python's {@code demo_async_check_permission}.
     */
    public static CompletableFuture<PermissionResult> demoAsyncCheckPermission(Path workspace) {
        return CompletableFuture.supplyAsync(() -> demoSyncPermissionEngine(workspace));
    }

    /**
     * Build the standalone permission rail used by DeepAgent.
     *
     * <p>Mirrors Python's {@code demo_standalone_rail_factory}.
     */
    public static PermissionInterruptRail demoStandaloneRailFactory(Path workspace) {
        Path root = normalizeWorkspace(workspace);
        return PermissionFactory.buildPermissionInterruptRail(
                examplePermissionsDict(),
                null,
                examplePermissionHost(root, null),
                root
        );
    }

    /**
     * Create a DeepAgent with security and permission rails mounted.
     *
     * <p>Mirrors the non-network setup path in Python's
     * {@code demo_deep_agent_mounts_rails}.
     */
    public static DeepAgent demoDeepAgentMountsRails(Path workspace) {
        Path root = normalizeWorkspace(workspace);
        PermissionInterruptRail permissionRail = demoStandaloneRailFactory(root);

        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(AgentCard.builder()
                .name(DEMO_AGENT_NAME)
                .description("Demonstrates tool permission rails")
                .build());
        config.setWorkspace(new Workspace(root.toString(), "cn"));
        config.setMaxIterations(3);
        config.setPermissions(permissionsWithWorkspaceRoot(root));
        config.setRails(List.of(new SecurityRail(), permissionRail));
        return HarnessFactory.createDeepAgent(config);
    }

    /**
     * Return rail class names in the demo agent configuration.
     */
    public static List<String> mountedRailNames(DeepAgent agent) {
        if (!(agent.getConfig() instanceof DeepAgentConfig config)) {
            return List.of();
        }
        return config.getRails().stream()
                .map(rail -> rail.getClass().getSimpleName())
                .toList();
    }

    /**
     * Decide whether the network-dependent natural-language demo can run.
     *
     * <p>Mirrors Python's API-key gate in
     * {@code demo_natural_language_triggers_permission_rail}.
     */
    public static boolean shouldRunNaturalLanguageDemo(Map<String, String> env) {
        String apiKey = env != null ? env.getOrDefault("API_KEY", "") : "";
        return !apiKey.strip().isEmpty();
    }

    /**
     * Summarize a Runner-style dictionary result for display.
     *
     * <p>Mirrors Python's {@code _nl_summarize_dict_result}.
     */
    public static List<String> summarizeDictResult(String prefix, Map<String, Object> result) {
        if (result == null) {
            return List.of((prefix != null ? prefix : "") + " result=null");
        }
        String p = prefix != null ? prefix : "";
        List<String> keys = new ArrayList<>(result.keySet());
        Collections.sort(keys);

        List<String> lines = new ArrayList<>();
        lines.add(p + " result_type=" + result.get("result_type") + " keys=" + keys);
        for (String key : List.of("output", "error", "status", "interrupt_ids", "state")) {
            if (!result.containsKey(key) || result.get(key) == null) {
                continue;
            }
            Object value = result.get(key);
            if ("state".equals(key) && value instanceof List<?> list) {
                lines.add(p + "   state: len=" + list.size());
                continue;
            }
            lines.add(p + "   " + key + ": " + shorten(value));
        }
        return lines;
    }

    /**
     * Gate the network-dependent natural-language demo behind API_KEY.
     *
     * <p>Mirrors Python's {@code demo_natural_language_triggers_permission_rail}
     * without making model calls during deterministic Java tests.
     */
    public static NaturalLanguageDemoResult demoNaturalLanguageTriggersPermissionRail(
            Path workspace,
            Map<String, String> env
    ) {
        Path root = normalizeWorkspace(workspace);
        if (!shouldRunNaturalLanguageDemo(env)) {
            return new NaturalLanguageDemoResult(
                    "skipped",
                    "",
                    "Set API_KEY to run the model-driven read_file permission interrupt demo.",
                    root
            );
        }
        String modelName = env.getOrDefault("MODEL_NAME", "gpt-4.1-mini").strip();
        return new NaturalLanguageDemoResult(
                "ready",
                modelName,
                "API_KEY is present; Java permissions, host, and rails are ready for a model-driven read_file demo.",
                root
        );
    }

    /**
     * Run the deterministic part of the permission demo.
     *
     * <p>Mirrors Python's {@code main}.
     */
    public static DemoRunSummary runDemo(Path workspace, Map<String, String> env)
            throws IOException, java.util.concurrent.ExecutionException, InterruptedException {
        Path root = normalizeWorkspace(workspace);
        Files.createDirectories(root);
        Files.writeString(root.resolve(SAMPLE_FILE), "permission_demo secret line\nsecond line\n");

        PermissionResult sync = demoSyncPermissionEngine(root);
        PermissionResult async = demoAsyncCheckPermission(root).get();
        PermissionInterruptRail rail = demoStandaloneRailFactory(root);
        DeepAgent agent = demoDeepAgentMountsRails(root);
        NaturalLanguageDemoResult nl = demoNaturalLanguageTriggersPermissionRail(root, env);
        return new DemoRunSummary(root, sync, async, rail.getClass().getSimpleName(), mountedRailNames(agent), nl);
    }

    public static void main(String[] args) throws Exception {
        Path workspace = args != null && args.length > 0
                ? Path.of(args[0])
                : Files.createTempDirectory("ojw-perm-demo-");
        DemoRunSummary summary = runDemo(workspace, System.getenv());
        System.out.println("Workspace: " + summary.workspace());
        System.out.println("[Sync] read_file -> " + summary.syncResult().getPermission().getValue()
                + " | rule: " + summary.syncResult().getMatchedRule());
        System.out.println("[Async] read_file -> " + summary.asyncResult().getPermission().getValue()
                + " | needs_approval: " + summary.asyncResult().needsApproval());
        System.out.println("[Factory] buildPermissionInterruptRail -> " + summary.permissionRailName());
        System.out.println("[DeepAgent] rails: " + summary.mountedRailNames());
        System.out.println("[NL] " + summary.naturalLanguageResult().status()
                + ": " + summary.naturalLanguageResult().message());
    }

    public record NaturalLanguageDemoResult(String status, String modelName, String message, Path workspace) {
    }

    public record DemoRunSummary(
            Path workspace,
            PermissionResult syncResult,
            PermissionResult asyncResult,
            String permissionRailName,
            List<String> mountedRailNames,
            NaturalLanguageDemoResult naturalLanguageResult
    ) {
    }

    private static Map<String, Object> permissionsWithWorkspaceRoot(Path workspace) {
        Map<String, Object> permissions = new LinkedHashMap<>(examplePermissionsDict());
        permissions.put("workspace_root", normalizeWorkspace(workspace).toString());
        return permissions;
    }

    private static String shorten(Object value) {
        String text = String.valueOf(value);
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500) + "...";
    }

    private static Path normalizeWorkspace(Path workspace) {
        Path root = workspace != null ? workspace : Path.of(".");
        return root.toAbsolutePath().normalize();
    }
}
