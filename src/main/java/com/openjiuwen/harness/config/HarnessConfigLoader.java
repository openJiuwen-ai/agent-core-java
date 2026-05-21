/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Load, validate, and resolve a harness_config.yaml file.
 * <p>
 * Mirrors Python's {@code HarnessConfigLoader} in
 * {@code openjiuwen.harness.harness_config.loader}.
 */
public class HarnessConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    private HarnessConfigLoader() {}

    /**
     * Load and resolve a harness_config.yaml, returning a ResolvedHarnessConfig.
     *
     * @param path           path to the harness_config.yaml file
     * @param params         optional render parameters for {{ var }} placeholders
     * @param workspaceRoot  overrides {{ workspace_root }} placeholder
     * @return resolved config
     */
    public static ResolvedHarnessConfig load(Path path, Map<String, Object> params, Path workspaceRoot) {
        path = path.toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("HarnessConfig file not found: " + path);
        }

        HarnessConfig config;
        try {
            String raw = Files.readString(path);
            JsonNode tree = YAML_MAPPER.readTree(raw);
            config = JSON_MAPPER.treeToValue(tree, HarnessConfig.class);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("HarnessConfig validation failed in '" + path + "': " + e.getMessage(), e);
        }

        // Effective params
        Map<String, Object> effectiveParams = new HashMap<>(params != null ? params : Map.of());
        effectiveParams.putIfAbsent("workspace_root",
                workspaceRoot != null ? workspaceRoot.toString() : path.getParent().toString());

        String language = config.getLanguage() != null ? config.getLanguage() : "cn";
        String systemPrompt = null;
        java.util.List<ResolvedSection> extraSections = new java.util.ArrayList<>();
        java.util.List<ResolvedFileSection> fileSections = new java.util.ArrayList<>();

        if (config.getPrompts() != null && config.getPrompts().getSections() != null) {
            for (SectionSchema sec : config.getPrompts().getSections()) {
                Map<String, String> rawContent = normalizeContent(sec.getContent());
                Map<String, String> rendered = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : rawContent.entrySet()) {
                    rendered.put(entry.getKey(), renderTemplate(entry.getValue(), effectiveParams));
                }

                if (sec.getFile() != null) {
                    fileSections.add(ResolvedFileSection.builder()
                            .filename(sec.getFile())
                            .content(rendered)
                            .build());
                } else if ("identity".equals(sec.getName())) {
                    systemPrompt = rendered.getOrDefault(language,
                            rendered.getOrDefault("cn",
                                    rendered.getOrDefault("en", null)));
                } else {
                    int priority = sec.getPriority() != null ? sec.getPriority() : 30;
                    extraSections.add(ResolvedSection.builder()
                            .name(sec.getName())
                            .priority(priority)
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
                .sourcePath(path)
                .build();
    }

    /** Convenience overload without explicit params. */
    public static ResolvedHarnessConfig load(Path path) {
        return load(path, null, null);
    }

    // ---- internal helpers ----

    static Map<String, String> normalizeContent(Object content) {
        if (content == null) {
            return Map.of();
        }
        if (content instanceof String s) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("cn", s);
            m.put("en", s);
            return m;
        }
        if (content instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> m = new LinkedHashMap<>((Map<String, String>) content);
            return m;
        }
        return Map.of();
    }

    static String renderTemplate(String text, Map<String, Object> params) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        Matcher m = TEMPLATE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).trim();
            Object val = params.get(key);
            m.appendReplacement(sb, val != null ? Matcher.quoteReplacement(val.toString()) : m.group(0));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
