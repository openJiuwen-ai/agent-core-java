/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen/auto_harness/infra/git_auth.py}.
 */
@DisplayName("Git auth helper tests")
class TestGitAuth {

    @Test
    void buildGitAuthEnvWithoutCredentialsKeepsBaseEnvAndDisablesPrompts() {
        Map<String, String> env = GitAuth.buildGitAuthEnv("", "", Map.of("PATH", "demo"));

        assertEquals("demo", env.get("PATH"));
        assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
        assertEquals("never", env.get("GCM_INTERACTIVE"));
        assertFalse(env.containsKey("GIT_CONFIG_COUNT"));
    }

    @Test
    void buildGitAuthEnvWithCredentialsAddsGitcodeHeader() {
        Map<String, String> env = GitAuth.buildGitAuthEnv("alice", "secret", Map.of());
        String expectedBasic = Base64.getEncoder()
                .encodeToString("alice:secret".getBytes(StandardCharsets.UTF_8));

        assertEquals("3", env.get("GIT_CONFIG_COUNT"));
        assertEquals("credential.helper", env.get("GIT_CONFIG_KEY_0"));
        assertEquals("", env.get("GIT_CONFIG_VALUE_0"));
        assertEquals("credential.interactive", env.get("GIT_CONFIG_KEY_1"));
        assertEquals("never", env.get("GIT_CONFIG_VALUE_1"));
        assertEquals("http.https://gitcode.com/.extraheader", env.get("GIT_CONFIG_KEY_2"));
        assertEquals("AUTHORIZATION: basic " + expectedBasic, env.get("GIT_CONFIG_VALUE_2"));
        assertTrue(env.containsKey("GIT_TERMINAL_PROMPT"));
        assertTrue(env.containsKey("GCM_INTERACTIVE"));
    }

    @Test
    void buildGitAuthEnvWithMissingTokenSkipsHeaderInjection() {
        Map<String, String> env = GitAuth.buildGitAuthEnv("alice", "", Map.of());

        assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
        assertEquals("never", env.get("GCM_INTERACTIVE"));
        assertFalse(env.containsKey("GIT_CONFIG_KEY_2"));
    }
}
