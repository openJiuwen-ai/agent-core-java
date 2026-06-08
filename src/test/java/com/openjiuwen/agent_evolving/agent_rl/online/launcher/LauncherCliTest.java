/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherCliTest {

    @Test
    void parseArgsSupportsPythonLauncherOptionsAndBuildsOverrides() {
        LauncherCli.LauncherArgs args = LauncherCli.parseArgs(new String[]{
                "--config", "online.yaml",
                "--model-path=/models/base",
                "--model-name", "base-model",
                "--vllm-gpu", "0,1",
                "--vllm-tp", "2",
                "--vllm-port", "18001",
                "--inference-url", "http://inference.local",
                "--judge-model-path", "/models/judge",
                "--judge-model-name", "judge-model",
                "--judge-gpu", "2,3",
                "--judge-tp", "1",
                "--judge-port", "18002",
                "--judge-url", "http://judge.local",
                "--gateway-port", "18080",
                "--redis-url", "redis://localhost:6379/0",
                "--threshold", "9",
                "--scan-interval", "11",
                "--train-gpu", "4,5",
                "--ppo-config", "ppo.yaml",
                "--trajectory-batch-size", "6",
                "--lora-repo", "lora_repo",
                "--jiuwen-agent-server-port", "18091",
                "--demo",
                "--skip_jiuwen",
                "--jiuwen-ws-port", "19000",
                "--jiuwen-web-host", "0.0.0.0",
                "--jiuwen-web-port", "5173"
        });

        Map<String, Object> overrides = LauncherCli.buildCliOverrides(args);

        assertEquals("online.yaml", args.getConfig());
        assertEquals(true, overrides.get("demo"));
        assertNested(overrides, "inference", "model_path", "/models/base");
        assertNested(overrides, "inference", "tp", 2);
        assertNested(overrides, "inference", "existing_url", "http://inference.local");
        assertNested(overrides, "judge", "model_name", "judge-model");
        assertNested(overrides, "gateway", "redis_url", "redis://localhost:6379/0");
        assertNested(overrides, "training", "threshold", 9);
        assertNested(overrides, "training", "scan_interval", 11);
        assertNested(overrides, "trajectory", "batch_size", 6);
        assertNested(overrides, "jiuwen", "enabled", false);
        assertNested(overrides, "jiuwen", "web_host", "0.0.0.0");
    }

    @Test
    void parseArgsRejectsUnknownAndMissingValues() {
        assertThrows(IllegalArgumentException.class, () -> LauncherCli.parseArgs(new String[]{"--bad"}));
        assertThrows(IllegalArgumentException.class, () -> LauncherCli.parseArgs(new String[]{"--config"}));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LauncherCli.parseArgs(new String[]{"--vllm-port", "abc"})
        );
        assertTrue(exception.getMessage().contains("Invalid integer"));
    }

    @SuppressWarnings("unchecked")
    private static void assertNested(Map<String, Object> values, String section, String key, Object expected) {
        assertEquals(expected, ((Map<String, Object>) values.get(section)).get(key));
    }
}
