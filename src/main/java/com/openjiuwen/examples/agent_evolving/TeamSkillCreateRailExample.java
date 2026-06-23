/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_evolving;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TeamSkillCreateRail example environment helpers.
 *
 * <p>Mirrors Python's {@code examples/agent_evolving/team_skill_create_rail_example.py}.</p>
 */
public final class TeamSkillCreateRailExample {

    private TeamSkillCreateRailExample() {
    }

    public static boolean loadEnvIfPresent() {
        return loadEnvIfPresent(Path.of(System.getProperty("user.dir", ".")));
    }

    public static boolean loadEnvIfPresent(Path cwd) {
        Path base = cwd == null ? Path.of(System.getProperty("user.dir", ".")) : cwd;
        Set<Path> loaded = new LinkedHashSet<>();
        return loadEnvFile(base.resolve(".env"), loaded);
    }

    public static String envValue(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(key);
        return value == null ? "" : value;
    }

    private static boolean loadEnvFile(Path path, Set<Path> loaded) {
        Path resolved = path.toAbsolutePath().normalize();
        if (!loaded.add(resolved) || !Files.isRegularFile(resolved)) {
            return false;
        }
        boolean changed = false;
        try {
            for (String line : Files.readAllLines(resolved, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int split = trimmed.indexOf('=');
                String key = trimmed.substring(0, split).trim();
                String value = stripQuotes(trimmed.substring(split + 1).trim());
                if (key.isEmpty() || System.getProperty(key) != null) {
                    continue;
                }
                System.setProperty(key, value);
                changed = true;
            }
        } catch (IOException exception) {
            return false;
        }
        return changed;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
