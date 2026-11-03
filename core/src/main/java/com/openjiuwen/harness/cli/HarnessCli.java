/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.harness.cli.ui.CliRunner;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Top-level CLI facade.
 *
 * <p>Mirrors Python's Click command surface in
 * {@code openjiuwen/harness/cli/cli.py}.</p>
 */
public final class HarnessCli {
    public static final String COMMAND_CHAT = "chat";
    public static final String COMMAND_RUN = "run";

    private HarnessCli() {
    }

    public static String defaultCommand(boolean stdinIsTty) {
        return stdinIsTty ? COMMAND_CHAT : COMMAND_RUN;
    }

    public static CLIOptions optionsFromMap(Map<String, Object> kwargs) {
        Map<String, Object> safe = kwargs == null ? Map.of() : kwargs;
        CLIOptions opts = new CLIOptions();
        opts.setModel(stringValue(firstPresent(safe, "model")));
        opts.setProvider(stringValue(firstPresent(safe, "provider")));
        opts.setApiKey(stringValue(firstPresent(safe, "api_key", "apiKey")));
        opts.setApiBase(stringValue(firstPresent(safe, "api_base", "apiBase")));
        opts.setRemote(stringValue(firstPresent(safe, "remote")));
        opts.setVerbose(booleanValue(firstPresent(safe, "verbose")));
        opts.setWorkspace(stringValue(firstPresent(safe, "workspace")));
        opts.setTenantId(stringValue(firstPresent(safe, "tenant", "tenant_id", "tenantId")));
        return opts;
    }

    /**
     * Build a TenantContext from CLI options when {@code --tenant} is provided.
     *
     * @param opts CLI options
     * @return tenant context, or null when absent
     * @since 0.1.7
     */
    public static TenantContext buildTenantContext(CLIOptions opts) {
        return Optional.ofNullable(opts)
                .map(CLIOptions::getTenantId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> TenantContext.builder().tenantId(id).build())
                .orElse(null);
    }

    public static String resolveRunPrompt(
            String prompt,
            boolean stdinIsTty,
            Supplier<String> stdinReader) {
        String resolved = prompt;
        if ("-".equals(prompt) || (prompt == null && !stdinIsTty)) {
            resolved = stdinReader == null ? "" : stdinReader.get();
            resolved = resolved == null ? "" : resolved.strip();
        }
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException(
                    "A prompt argument is required, or pipe via stdin.");
        }
        return resolved;
    }

    public static int runOnce(
            CLIOptions opts,
            String prompt,
            String outputFormat,
            CliRunner runner) {
        CliRunner effectiveRunner = runner == null ? new CliRunner() : runner;
        return effectiveRunner.runOnce(toConfigMap(opts), prompt, outputFormat);
    }

    public static AutoHarnessCliSupport.PreparedRun prepareAutoHarnessRun(
            CLIOptions opts,
            AutoHarnessRunRequest request) throws IOException {
        return AutoHarnessCliSupport.prepareRun(opts, request, null);
    }

    public static AutoHarnessCliSupport.GapAnalyzeRequest prepareGapAnalyze(
            CLIOptions opts,
            String competitor) {
        String workspace = opts == null ? "" : opts.getWorkspace();
        return AutoHarnessCliSupport.prepareGapAnalyze(workspace, competitor);
    }

    public static Map<String, Object> toConfigMap(CLIOptions opts) {
        CLIOptions safe = opts == null ? new CLIOptions() : opts;
        Map<String, Object> config = new LinkedHashMap<>();
        putIfPresent(config, "provider", safe.getProvider());
        putIfPresent(config, "model", safe.getModel());
        putIfPresent(config, "api_key", safe.getApiKey());
        putIfPresent(config, "api_base", safe.getApiBase());
        putIfPresent(config, "server_url", safe.getRemote());
        putIfPresent(config, "workspace", safe.getWorkspace());
        putIfPresent(config, "tenant_id", safe.getTenantId());
        config.put("verbose", safe.isVerbose());
        return config;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return;
        }
        target.put(key, value);
    }

    private static Object firstPresent(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
