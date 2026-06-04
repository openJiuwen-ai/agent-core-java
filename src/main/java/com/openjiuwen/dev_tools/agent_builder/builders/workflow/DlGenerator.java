/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DL generator — generates DL from natural language using LLM.
 * <p>
 * Mirrors Python's {@code DLGenerator} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_generator}.
 */
public class DlGenerator {

    public static final String DL_GENERATE_SYSTEM_TEMPLATE = """
            Components: {components}
            Schema: {schema}
            Plugins: {plugins}
            Examples: {examples}
            """;

    public static final String DL_REFINE_USER_TEMPLATE = """
            Input: {user_input}
            Mermaid: {exist_mermaid}
            DL: {exist_dl}
            """;

    private final Object llm;
    private final List<Object> reflectPrompts = new ArrayList<>();

    public DlGenerator(Object llm) {
        this.llm = llm;
    }

    public Object getLlm() { return llm; }

    public List<Object> getReflectPrompts() {
        return reflectPrompts;
    }

    public static String formatGenerateSystemTemplate(String components, String schema, String plugins, String examples) {
        return DL_GENERATE_SYSTEM_TEMPLATE
                .replace("{components}", String.valueOf(components))
                .replace("{schema}", String.valueOf(schema))
                .replace("{plugins}", String.valueOf(plugins))
                .replace("{examples}", String.valueOf(examples));
    }

    public static String formatRefineUserTemplate(String userInput, String existMermaid, String existDl) {
        return DL_REFINE_USER_TEMPLATE
                .replace("{user_input}", String.valueOf(userInput))
                .replace("{exist_mermaid}", String.valueOf(existMermaid))
                .replace("{exist_dl}", String.valueOf(existDl));
    }

    public String generate(String query, Map<String, Object> resource) {
        String[] assets = loadSchemaAndExamples();
        String systemPrompt = formatGenerateSystemTemplate(assets[0], assets[1], formatPlugins(resource), assets[2]);
        return invokeLlm(systemPrompt, query);
    }

    public String refine(String query, Map<String, Object> resource, String existDl, String existMermaid) {
        String[] assets = loadSchemaAndExamples();
        String systemPrompt = formatGenerateSystemTemplate(assets[0], assets[1], formatPlugins(resource), assets[2]);
        return invokeLlm(systemPrompt, formatRefineUserTemplate(query, existMermaid, existDl));
    }

    public static String[] loadSchemaAndExamples() {
        return new String[] {DlAssets.COMPONENTS_INFO, DlAssets.SCHEMA_INFO, DlAssets.EXAMPLES};
    }

    private String formatPlugins(Map<String, Object> resource) {
        Object plugins = resource == null ? null : resource.get("plugins");
        if (plugins instanceof Collection<?> collection && !collection.isEmpty()) {
            return String.join("\n", collection.stream().map(Objects::toString).toList());
        }
        if (plugins != null && !plugins.toString().isBlank()) {
            return plugins.toString();
        }
        return Prompts.EMPTY_RESOURCE_CONTENT;
    }

    private String invokeLlm(String systemPrompt, String userPrompt) {
        if (llm == null) {
            return "";
        }
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of(IntentionDetector.ROLE, "system", IntentionDetector.CONTENT, systemPrompt));
        messages.add(Map.of(IntentionDetector.ROLE, "user", IntentionDetector.CONTENT, userPrompt != null ? userPrompt : ""));
        messages.addAll(reflectPrompts);
        try {
            return IntentionDetector.invokeLlmContent(llm, messages);
        } catch (Exception e) {
            throw new IllegalStateException("DL generation failed: " + e.getMessage(), e);
        }
    }
}
