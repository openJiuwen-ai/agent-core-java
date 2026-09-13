/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HarnessConfigLoader.
 * 
 * @since 0.1.7
 */
public final class HarnessConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * HarnessConfigLoader.
     * 
     * @since 0.1.7
     */
    private HarnessConfigLoader() {
    }

    /**
     * load.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static ResolvedHarnessConfig load(String path) {
        return load(Path.of(path), Map.of(), null);
    }

    /**
     * load.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static ResolvedHarnessConfig load(Path path) {
        return load(path, Map.of(), null);
    }

    /**
     * load.
     * 
     * @param path path
     * @param params params
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    public static ResolvedHarnessConfig load(Path path, Map<String, Object> params, Path workspaceRoot) {
        Path resolvedPath = path.toAbsolutePath().normalize();
        if (!Files.exists(resolvedPath)) {
            throw new IllegalArgumentException("HarnessConfig file not found: " + resolvedPath);
        }
        try {
            String raw = Files.readString(resolvedPath);
            Object loaded = new Yaml().load(raw);
            Map<String, Object> data = loaded instanceof Map<?, ?> map ? castMap(map) : Map.of();
            HarnessConfig config = MAPPER.convertValue(data, HarnessConfig.class);
            return resolve(config, resolvedPath, params, workspaceRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "HarnessConfig validation failed in '" + resolvedPath + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * resolve.
     * 
     * @param config config
     * @param sourcePath sourcePath
     * @param params params
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    public static ResolvedHarnessConfig resolve(HarnessConfig config, Path sourcePath, Map<String, Object> params,
            Path workspaceRoot) {
        Objects.requireNonNull(config, "config");
        Path normalizedSource =
            sourcePath == null ? Path.of(".").toAbsolutePath().normalize() : sourcePath.toAbsolutePath().normalize();
        Map<String, Object> effectiveParams = new LinkedHashMap<>(params == null ? Map.of() : params);
        effectiveParams.putIfAbsent("workspace_root", String.valueOf(
                workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : normalizedSource.getParent()));

        String language = config.getLanguage() == null || config.getLanguage().isBlank() ? "cn" : config.getLanguage();
        String systemPrompt = null;
        List<ResolvedSection> extraSections = new ArrayList<>();
        List<ResolvedFileSection> fileSections = new ArrayList<>();

        if (config.getPrompts() != null) {
            for (HarnessConfig.SectionSchema section : config.getPrompts().getSections()) {
                Map<String, String> normalizedContent = normalizeContent(section.getContent());
                Map<String, String> rendered = renderAll(normalizedContent, effectiveParams);
                if (section.getFile() != null && !section.getFile().isBlank()) {
                    fileSections.add(new ResolvedFileSection(section.getFile(), rendered));
                } else if ("identity".equals(section.getName())) {
                    systemPrompt = pickLanguage(rendered, language);
                } else {
                    extraSections.add(new ResolvedSection(section.getName(),
                            section.getPriority() != null ? section.getPriority() : 30, rendered));
                }
            }
        }

        return new ResolvedHarnessConfig(config, systemPrompt, extraSections, fileSections, normalizedSource);
    }

    /**
     * castMap.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }

    /**
     * normalizeContent.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, String> normalizeContent(Object content) {
        if (content == null) {
            return Map.of();
        }
        if (content instanceof String text) {
            return Map.of("cn", text, "en", text);
        }
        if (content instanceof Map<?, ?> map) {
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return normalized;
        }
        return Map.of("cn", String.valueOf(content), "en", String.valueOf(content));
    }

    /**
     * renderAll.
     * 
     * @param content content
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, String> renderAll(Map<String, String> content, Map<String, Object> params) {
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : content.entrySet()) {
            rendered.put(entry.getKey(), renderTemplate(entry.getValue(), params));
        }
        return rendered;
    }

    /**
     * renderTemplate.
     * 
     * @param text text
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private static String renderTemplate(String text, Map<String, Object> params) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        Matcher matcher = TEMPLATE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.get(key);
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(value != null ? String.valueOf(value) : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * pickLanguage.
     * 
     * @param content content
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static String pickLanguage(Map<String, String> content, String language) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String picked = content.get(language);
        if (picked != null && !picked.isBlank()) {
            return picked;
        }
        picked = content.get("cn");
        if (picked != null && !picked.isBlank()) {
            return picked;
        }
        return content.get("en");
    }
}
