/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for browser-move utility exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/harness/tools/browser_move/utils/__init__.py}.
 */
public final class BrowserMoveUtilsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/tools/browser_move/utils/__init__.py";
    public static final int DEFAULT_BROWSER_TIMEOUT_S = BrowserMoveEnv.DEFAULT_BROWSER_TIMEOUT_S;
    public static final int DEFAULT_GUARDRAIL_MAX_FAILURES = BrowserMoveEnv.DEFAULT_GUARDRAIL_MAX_FAILURES;
    public static final int DEFAULT_GUARDRAIL_MAX_STEPS = BrowserMoveEnv.DEFAULT_GUARDRAIL_MAX_STEPS;
    public static final boolean DEFAULT_GUARDRAIL_RETRY_ONCE = BrowserMoveEnv.DEFAULT_GUARDRAIL_RETRY_ONCE;
    public static final String DEFAULT_MODEL_NAME = BrowserMoveEnv.DEFAULT_MODEL_NAME;
    public static final String DEFAULT_PLAYWRIGHT_MCP_ARGS = BrowserMoveEnv.DEFAULT_PLAYWRIGHT_MCP_ARGS;
    public static final String DEFAULT_PLAYWRIGHT_MCP_COMMAND = BrowserMoveEnv.DEFAULT_PLAYWRIGHT_MCP_COMMAND;
    public static final String MISSING_API_KEY_MESSAGE = BrowserMoveEnv.MISSING_API_KEY_MESSAGE;

    private BrowserMoveUtilsPackage() {
    }

    public static Path resolveRepoDotenvPath() {
        return BrowserMoveEnv.resolveRepoDotenvPath();
    }

    public static boolean loadRepoDotenv() {
        return BrowserMoveEnv.loadRepoDotenv(false);
    }

    public static boolean loadRepoDotenv(boolean override) {
        return BrowserMoveEnv.loadRepoDotenv(override);
    }

    public static List<String> parseCommandArgs(String value) {
        return BrowserMoveEnv.parseCommandArgs(value);
    }

    public static boolean resolveBoolEnv(boolean defaultValue, String... keys) {
        return BrowserMoveEnv.resolveBoolEnv(defaultValue, keys);
    }

    public static int resolveBrowserTimeoutS() {
        return BrowserMoveEnv.resolveBrowserTimeoutS();
    }

    public static int resolveIntEnv(int defaultValue, Integer minimum, String... keys) {
        return BrowserMoveEnv.resolveIntEnv(defaultValue, minimum, keys);
    }

    public static String resolveModelName() {
        return BrowserMoveEnv.resolveModelName();
    }

    public static BrowserMoveEnv.ModelSettings resolveModelSettings() {
        return BrowserMoveEnv.resolveModelSettings();
    }

    public static Map<String, Object> extractJsonObject(Object text) {
        return ParsingUtils.extractJsonObject(text);
    }
}
