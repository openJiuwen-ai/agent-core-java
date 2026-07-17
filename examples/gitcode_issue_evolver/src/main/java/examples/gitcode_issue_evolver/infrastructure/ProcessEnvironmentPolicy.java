/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Removes credentials from processes started on behalf of an Agent.
 *
 * @since 0.1.12
 */
public final class ProcessEnvironmentPolicy {
    private static final List<String> SENSITIVE_MARKERS = List.of(
            "TOKEN", "SECRET", "PASSWORD", "PASSWD", "CREDENTIAL",
            "API_KEY", "ACCESS_KEY", "PRIVATE_KEY", "AUTHORIZATION",
            "ASKPASS", "COOKIE", "SESSION", "PASSPHRASE");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "SSH_AUTH_SOCK", "KUBECONFIG", "DOCKER_CONFIG", "NETRC");

    private ProcessEnvironmentPolicy() {
    }

    /**
     * Remove environment entries that commonly contain credentials.
     *
     * @param builder process builder to sanitize
     */
    public static void sanitize(ProcessBuilder builder) {
        sanitize(Objects.requireNonNull(builder, "builder must not be null").environment());
    }

    /**
     * Remove credential-bearing entries from an environment map.
     *
     * @param environment mutable child-process environment
     */
    public static void sanitize(Map<String, String> environment) {
        Map<String, String> mutableEnvironment = Objects.requireNonNull(
                environment, "environment must not be null");
        List<String> keys = new ArrayList<>(mutableEnvironment.keySet());
        for (String key : keys) {
            String normalized = key.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("GIT_CONFIG_")
                    || SENSITIVE_NAMES.contains(normalized)
                    || SENSITIVE_MARKERS.stream().anyMatch(normalized::contains)) {
                mutableEnvironment.remove(key);
            }
        }
    }
}
