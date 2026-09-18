/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TeamAgentSpec YAML loader for the interactive CLI.
 *
 * <p>Mirrors Python's module helpers in
 * {@code openjiuwen/agent_teams/cli/spec_loader.py}.</p>
 */
public final class SpecLoader {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private SpecLoader() {
    }

    public static LoadedSpec loadSpecYaml(String path) {
        return loadSpecYaml(Path.of(path));
    }

    public static LoadedSpec loadSpecYaml(Path path) {
        return loadSpecYaml(path, System::getenv);
    }

    static LoadedSpec loadSpecYaml(Path path, Function<String, String> envLookup) {
        Path yamlPath = resolveUserPath(path);
        if (!Files.isRegularFile(yamlPath)) {
            raiseConfigInvalid("team spec yaml not found: " + yamlPath, null);
            throw new IllegalStateException("unreachable");
        }

        Object raw;
        try (Reader reader = Files.newBufferedReader(yamlPath, StandardCharsets.UTF_8)) {
            raw = new Yaml().load(reader);
        } catch (IOException | RuntimeException error) {
            raiseConfigInvalid("team spec yaml is malformed: " + yamlPath, error);
            throw new IllegalStateException("unreachable", error);
        }

        if (!(raw instanceof Map<?, ?> rawMap)) {
            raiseConfigInvalid("team spec yaml must decode to a mapping: " + yamlPath, null);
            throw new IllegalStateException("unreachable");
        }

        Map<String, Object> expanded = toStringKeyMap(expandEnvVars(rawMap, envLookup));
        Object runtimeRaw = expanded.remove("runtime");
        Map<String, Object> runtime = runtimeMap(runtimeRaw, yamlPath);

        try {
            TeamAgentSpec spec = OBJECT_MAPPER.convertValue(expanded, TeamAgentSpec.class);
            return new LoadedSpec(spec, runtime);
        } catch (IllegalArgumentException error) {
            raiseConfigInvalid("team spec yaml is malformed: " + yamlPath, error);
            throw new IllegalStateException("unreachable", error);
        }
    }

    static Object expandEnvVars(Object value, Function<String, String> envLookup) {
        Objects.requireNonNull(envLookup, "envLookup");
        if (value instanceof String text) {
            Matcher matcher = ENV_VAR_PATTERN.matcher(text);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String replacement = envLookup.apply(matcher.group(1));
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        replacement == null ? matcher.group(0) : replacement));
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), expandEnvVars(entry.getValue(), envLookup));
            }
            return converted;
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(expandEnvVars(item, envLookup));
            }
            return converted;
        }
        return value;
    }

    private static Path resolveUserPath(Path path) {
        Objects.requireNonNull(path, "path");
        String raw = path.toString();
        Path expanded = raw.equals("~") || raw.startsWith("~\\") || raw.startsWith("~/")
                ? Path.of(System.getProperty("user.home"), raw.length() == 1 ? "" : raw.substring(2))
                : path;
        return expanded.toAbsolutePath().normalize();
    }

    private static Map<String, Object> toStringKeyMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            raiseConfigInvalid("team spec yaml must decode to a mapping", null);
            throw new IllegalStateException("unreachable");
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    private static Map<String, Object> runtimeMap(Object runtimeRaw, Path yamlPath) {
        if (runtimeRaw == null || Boolean.FALSE.equals(runtimeRaw)) {
            return Map.of();
        }
        if (!(runtimeRaw instanceof Map<?, ?> runtimeMap)) {
            raiseConfigInvalid("team spec yaml runtime block must be a mapping: " + yamlPath, null);
            throw new IllegalStateException("unreachable");
        }
        Map<String, Object> runtime = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : runtimeMap.entrySet()) {
            runtime.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return runtime;
    }

    private static void raiseConfigInvalid(String reason, Throwable cause) {
        ErrorHelper.raiseError(
                StatusCode.AGENT_TEAM_CONFIG_INVALID,
                null,
                null,
                cause,
                Map.of("reason", reason)
        );
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Loaded YAML spec tuple.
     *
     * <p>Mirrors Python's {@code tuple[TeamAgentSpec, dict[str, Any]]} return from
     * {@code load_spec_yaml} in {@code openjiuwen/agent_teams/cli/spec_loader.py}.</p>
     *
     * @param spec             validated team agent spec
     * @param runtimeOverrides runtime block extracted from YAML
     */
    public record LoadedSpec(TeamAgentSpec spec, Map<String, Object> runtimeOverrides) {
        public LoadedSpec {
            spec = Objects.requireNonNull(spec, "spec");
            runtimeOverrides = runtimeOverrides == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(runtimeOverrides));
        }
    }
}
