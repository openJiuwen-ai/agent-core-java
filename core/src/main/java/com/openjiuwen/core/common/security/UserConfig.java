/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code UserConfig} in
 * {@code openjiuwen/core/common/security/user_config.py}.
 */
public final class UserConfig {

    public static final String DEFAULT_SENSITIVE_PATH_STR =
            "/etc/passwd,/etc/shadow,/etc/hosts,/etc/hostname,/etc/ssh/,"
                    + "C:\\Windows\\System32\\,C:\\Windows\\SysWOW64\\,C:\\Windows\\System\\";
    public static final List<String> DEFAULT_SENSITIVE_PATHS = List.of(
            "/etc/passwd",
            "/etc/shadow",
            "/etc/hosts",
            "/etc/hostname",
            "/etc/ssh/",
            "C:\\Windows\\System32\\",
            "C:\\Windows\\SysWOW64\\",
            "C:\\Windows\\System\\"
    );

    private static final Pattern WINDOWS_ENV = Pattern.compile("%([^%]+)%");
    private static final Object LOCK = new Object();
    private static final Map<String, String> DEFAULT_SETTINGS = Map.of(
            "is_sensitive", "true",
            "sensitive_paths", DEFAULT_SENSITIVE_PATH_STR
    );

    private static volatile UserConfig instance;
    private static volatile Path userPath;
    private static volatile Function<String, String> envReader = System::getenv;

    private final Map<String, String> settings;
    private volatile boolean sensitive;
    private volatile List<String> sensitivePaths;

    public UserConfig(Path configPath) {
        this.settings = loadSettings(configPath);
        this.sensitive = parseBoolean(requireSetting("is_sensitive"));
    }

    public static void setConfigPath(Path path) {
        if (instance != null) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                    "error_msg",
                    "config already initialized"
            );
        }
        userPath = resolveAndCheck(Objects.requireNonNull(path, "path must not be null"));
    }

    public static UserConfig getConfig() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new UserConfig(userPath);
                }
            }
        }
        return instance;
    }

    public static boolean isSensitive() {
        String envValue = envReader.apply("IS_SENSITIVE");
        if ("false".equalsIgnoreCase(envValue != null ? envValue.toLowerCase(Locale.ROOT) : null)) {
            return false;
        }
        return getConfig().sensitive;
    }

    public static List<String> getSensitivePaths() {
        return getConfig().getSensitivePathsList();
    }

    public static void setIsSensitive(boolean isSensitive) {
        UserConfig config = getConfig();
        synchronized (LOCK) {
            config.sensitive = isSensitive;
        }
    }

    public static void setSensitive(boolean isSensitive) {
        setIsSensitive(isSensitive);
    }

    public List<String> getSensitivePathsList() {
        if (sensitivePaths == null) {
            synchronized (this) {
                if (sensitivePaths == null) {
                    try {
                        String raw = settings.getOrDefault("sensitive_paths", "");
                        if (!raw.isBlank()) {
                            List<String> parsed = new ArrayList<>();
                            for (String item : raw.split(",")) {
                                String trimmed = item.trim();
                                if (!trimmed.isEmpty()) {
                                    parsed.add(trimmed);
                                }
                            }
                            sensitivePaths = parsed.isEmpty() ? DEFAULT_SENSITIVE_PATHS : List.copyOf(parsed);
                        } else {
                            sensitivePaths = DEFAULT_SENSITIVE_PATHS;
                        }
                    } catch (RuntimeException ignored) {
                        sensitivePaths = DEFAULT_SENSITIVE_PATHS;
                    }
                }
            }
        }
        return new ArrayList<>(sensitivePaths);
    }

    static synchronized void resetForTests() {
        instance = null;
        userPath = null;
        envReader = System::getenv;
    }

    static synchronized void setEnvReaderForTests(Function<String, String> reader) {
        envReader = reader != null ? reader : System::getenv;
    }

    private static Path resolveAndCheck(Path path) {
        Path resolved = expandPath(path.toString()).toAbsolutePath().normalize();
        Path root = Path.of("").toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                    "error_msg",
                    "config file must inside root"
            );
        }
        return resolved;
    }

    private static Path expandPath(String rawPath) {
        String expanded = rawPath;
        if (expanded.startsWith("~")) {
            expanded = System.getProperty("user.home") + expanded.substring(1);
        }
        Matcher matcher = WINDOWS_ENV.matcher(expanded);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = System.getenv(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group(0)));
        }
        matcher.appendTail(buffer);
        return Path.of(buffer.toString()).toAbsolutePath().normalize();
    }

    private static Map<String, String> loadSettings(Path configPath) {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return new LinkedHashMap<>(DEFAULT_SETTINGS);
        }
        try {
            return parseIniSettings(configPath);
        } catch (IOException | RuntimeException ignored) {
            return new LinkedHashMap<>(DEFAULT_SETTINGS);
        }
    }

    private static Map<String, String> parseIniSettings(Path configPath) throws IOException {
        Map<String, String> parsed = new LinkedHashMap<>();
        String currentSection = "";
        for (String line : Files.readAllLines(configPath)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length() - 1).trim();
                continue;
            }
            if (!"settings".equals(currentSection)) {
                continue;
            }
            int index = trimmed.indexOf('=');
            if (index < 0) {
                continue;
            }
            String key = trimmed.substring(0, index).trim();
            String value = trimmed.substring(index + 1).trim();
            parsed.put(key, value);
        }
        return parsed;
    }

    private String requireSetting(String key) {
        String value = settings.get(key);
        if (value == null) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                    "error_msg",
                    "missing settings." + key
            );
        }
        return value;
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("1", "true", "yes", "on").contains(normalized)) {
            return true;
        }
        if (List.of("0", "false", "no", "off").contains(normalized)) {
            return false;
        }
        throw ErrorHelper.buildError(
                StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                "error_msg",
                "invalid settings.is_sensitive"
        );
    }
}
