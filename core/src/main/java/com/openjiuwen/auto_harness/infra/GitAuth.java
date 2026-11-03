package com.openjiuwen.auto_harness.infra;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen/auto_harness/infra/git_auth.py}.
 */
public final class GitAuth {

    private GitAuth() {
    }

    public static Map<String, String> buildGitAuthEnv() {
        return buildGitAuthEnv("", "");
    }

    public static Map<String, String> buildGitAuthEnv(String username, String token) {
        return buildGitAuthEnv(username, token, System.getenv());
    }

    static Map<String, String> buildGitAuthEnv(String username, String token, Map<String, String> baseEnv) {
        Map<String, String> env = new HashMap<>(baseEnv);
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GCM_INTERACTIVE", "never");

        if (isBlank(username) || isBlank(token)) {
            return env;
        }

        String basic = Base64.getEncoder()
                .encodeToString((username + ":" + token).getBytes(StandardCharsets.UTF_8));
        env.put("GIT_CONFIG_COUNT", "3");
        env.put("GIT_CONFIG_KEY_0", "credential.helper");
        env.put("GIT_CONFIG_VALUE_0", "");
        env.put("GIT_CONFIG_KEY_1", "credential.interactive");
        env.put("GIT_CONFIG_VALUE_1", "never");
        env.put("GIT_CONFIG_KEY_2", "http.https://gitcode.com/.extraheader");
        env.put("GIT_CONFIG_VALUE_2", "AUTHORIZATION: basic " + basic);
        return env;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
