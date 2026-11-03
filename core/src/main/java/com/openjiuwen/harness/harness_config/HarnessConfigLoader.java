/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code HarnessConfigLoader} in
 * {@code openjiuwen/harness/harness_config/loader.py}.
 */
public final class HarnessConfigLoader {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    private HarnessConfigLoader() {}

    /**
     * Load and resolve a {@code harness_config.yaml}.
     *
     * @param path path to the harness config file
     * @param params template render parameters, or {@code null}
     * @param workspaceRoot workspace root override, or {@code null}
     * @return resolved harness config
     */
    public static ResolvedHarnessConfig load(Path path, Map<String, Object> params, Path workspaceRoot) {
        Path resolvedPath = path.toAbsolutePath().normalize();
        if (!Files.exists(resolvedPath)) {
            throw new IllegalArgumentException("HarnessConfig file not found: " + resolvedPath);
        }

        HarnessConfig config;
        try {
            String raw = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            Object yamlObject = new Yaml().load(raw);
            Map<String, Object> data = yamlObject instanceof Map<?, ?> map
                    ? JSON_MAPPER.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {})
                    : new LinkedHashMap<>();
            config = JSON_MAPPER.convertValue(data, HarnessConfig.class);
            validateConfig(config, resolvedPath);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read HarnessConfig file: " + resolvedPath, e);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "HarnessConfig validation failed in '" + resolvedPath + "': " + e.getMessage(),
                    e
            );
        }

        Map<String, Object> effectiveParams = new HashMap<>(params != null ? params : Map.of());
        effectiveParams.putIfAbsent(
                "workspace_root",
                workspaceRoot != null ? workspaceRoot.toString() : resolvedPath.getParent().toString()
        );

        String language = config.getLanguage() != null ? config.getLanguage() : "cn";
        String systemPrompt = null;
        List<ResolvedSection> extraSections = new java.util.ArrayList<>();
        List<ResolvedFileSection> fileSections = new java.util.ArrayList<>();

        if (config.getPrompts() != null && config.getPrompts().getSections() != null) {
            for (HarnessConfig.SectionSchema section : config.getPrompts().getSections()) {
                Map<String, String> rawContent = normalizeContent(section.getContent());
                Map<String, String> rendered = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : rawContent.entrySet()) {
                    rendered.put(entry.getKey(), renderTemplate(entry.getValue(), effectiveParams));
                }

                if (section.getFile() != null) {
                    fileSections.add(ResolvedFileSection.builder()
                            .filename(section.getFile())
                            .content(rendered)
                            .build());
                } else if ("identity".equals(section.getName())) {
                    systemPrompt = rendered.getOrDefault(
                            language,
                            rendered.getOrDefault("cn", rendered.getOrDefault("en", null))
                    );
                } else {
                    extraSections.add(ResolvedSection.builder()
                            .name(section.getName())
                            .priority(section.getPriority() != null ? section.getPriority() : 30)
                            .content(rendered)
                            .build());
                }
            }
        }

        return ResolvedHarnessConfig.builder()
                .config(config)
                .systemPrompt(systemPrompt)
                .extraSections(extraSections)
                .fileSections(fileSections)
                .sourcePath(resolvedPath)
                .build();
    }

    /**
     * Load and resolve a {@code harness_config.yaml}.
     *
     * @param path path to the harness config file
     * @param params template render parameters, or {@code null}
     * @return resolved harness config
     */
    public static ResolvedHarnessConfig load(Path path, Map<String, Object> params) {
        return load(path, params, null);
    }

    /**
     * Load and resolve a {@code harness_config.yaml}.
     *
     * @param path path to the harness config file
     * @return resolved harness config
     */
    public static ResolvedHarnessConfig load(Path path) {
        return load(path, null, null);
    }

    static Map<String, String> normalizeContent(Object content) {
        if (content == null) {
            return Map.of();
        }
        if (content instanceof String text) {
            Map<String, String> normalized = new LinkedHashMap<>();
            normalized.put("cn", text);
            normalized.put("en", text);
            return normalized;
        }
        if (content instanceof Map<?, ?> map) {
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
            return normalized;
        }
        return Map.of();
    }

    static String renderTemplate(String text, Map<String, Object> params) {
        if (text == null || !text.contains("{{")) {
            return text;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(text);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = params.get(key);
            matcher.appendReplacement(
                    rendered,
                    value != null ? Matcher.quoteReplacement(value.toString()) : matcher.group(0)
            );
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static void validateConfig(HarnessConfig config, Path path) {
        if (config == null) {
            throw new IllegalArgumentException("HarnessConfig validation failed in '" + path + "': empty config");
        }
        if (config.getPrompts() != null && config.getPrompts().getSections() != null) {
            for (int i = 0; i < config.getPrompts().getSections().size(); i++) {
                HarnessConfig.SectionSchema section = config.getPrompts().getSections().get(i);
                if (section == null || isBlank(section.getName())) {
                    throw new IllegalArgumentException(
                            "HarnessConfig validation failed in '" + path + "': prompts.sections[" + i + "].name is required"
                    );
                }
            }
        }
        if (config.getResources() != null) {
            validateTools(config.getResources().getTools(), path, "resources.tools");
            validateRails(config.getResources().getRails(), path, "resources.rails");
        }
    }

    private static void validateTools(
            List<HarnessConfig.ToolResourceSchema> tools,
            Path path,
            String prefix
    ) {
        if (tools == null) {
            return;
        }
        for (int i = 0; i < tools.size(); i++) {
            HarnessConfig.ToolResourceSchema tool = tools.get(i);
            if (tool == null || isBlank(tool.getType())) {
                throw new IllegalArgumentException(
                        "HarnessConfig validation failed in '" + path + "': " + prefix + "[" + i + "].type is required"
                );
            }
        }
    }

    private static void validateRails(
            List<HarnessConfig.RailResourceSchema> rails,
            Path path,
            String prefix
    ) {
        if (rails == null) {
            return;
        }
        for (int i = 0; i < rails.size(); i++) {
            HarnessConfig.RailResourceSchema rail = rails.get(i);
            if (rail == null || isBlank(rail.getType())) {
                throw new IllegalArgumentException(
                        "HarnessConfig validation failed in '" + path + "': " + prefix + "[" + i + "].type is required"
                );
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
