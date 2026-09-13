
package com.openjiuwen.autoharness.systemtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;

class AutoHarnessE2ESystemTestCompatibilityTest {
    @Test
    void resolveApiConfigShouldMirrorPythonE2EEnvironmentAliases() {
        Map<String, String> loaded = Map.of("API_BASE", "https://from-file.example", "API_KEY", "file-key",
                "MODEL_NAME", "file-model", "MODEL_PROVIDER", "file-provider");
        Map<String, String> env = Map.of("OPENJIUWEN_API_BASE", "https://openjiuwen.example", "OPENJIUWEN_API_KEY",
                "openjiuwen-key", "OPENJIUWEN_MODEL", "openjiuwen-model", "OPENJIUWEN_PROVIDER", "openjiuwen-provider",
                "MODEL_TIMEOUT", "77");

        Map<String, String> resolved = AutoHarnessE2ESystemTest.resolveApiConfig(loaded, env);

        assertThat(resolved).containsEntry("API_BASE", "https://openjiuwen.example");
        assertThat(resolved).containsEntry("API_KEY", "openjiuwen-key");
        assertThat(resolved).containsEntry("MODEL_NAME", "openjiuwen-model");
        assertThat(resolved).containsEntry("MODEL_PROVIDER", "openjiuwen-provider");
        assertThat(resolved).containsEntry("MODEL_TIMEOUT", "77");
    }

    @Test
    void resolveApiConfigShouldPreferCanonicalApiEnvironmentOverAliases() {
        Map<String, String> resolved =
            AutoHarnessE2ESystemTest.resolveApiConfig(Map.of(), Map.of("API_KEY", "canonical-key", "OPENJIUWEN_API_KEY",
                    "alias-key", "MODEL_NAME", "canonical-model", "OPENJIUWEN_MODEL", "alias-model"));

        assertThat(resolved).containsEntry("API_KEY", "canonical-key");
        assertThat(resolved).containsEntry("MODEL_NAME", "canonical-model");
    }

    @Test
    void resolveApiConfigShouldUseSettingsAndPythonModelDefaults() {
        Map<String, String> resolved = AutoHarnessE2ESystemTest.resolveApiConfig(Map.of(), Map.of(),
                Map.of("apiBase", "https://settings.example", "apiKey", "settings-key"));

        assertThat(resolved).containsEntry("API_BASE", "https://settings.example");
        assertThat(resolved).containsEntry("API_KEY", "settings-key");
        assertThat(resolved).containsEntry("MODEL_NAME", "GLM-5");
        assertThat(resolved).containsEntry("MODEL_PROVIDER", "OpenAI");
    }

    @Test
    void resolveApiConfigShouldLetEnvironmentOverrideSettingsLikePythonE2E() {
        Map<String, String> resolved = AutoHarnessE2ESystemTest.resolveApiConfig(Map.of(),
                Map.of("OPENJIUWEN_PROVIDER", "env-provider"), Map.of("provider", "settings-provider"));

        assertThat(resolved).containsEntry("MODEL_PROVIDER", "env-provider");
    }
}
