/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.locales;

import java.util.Map;

/**
 * Lightweight i18n for agent team tool descriptions.
 * <p>
 * Mirrors Python tools/locales/__init__.py: provides a
 * {@code makeTranslator(lang)} factory that returns a translator
 * function for tool description strings.
 * </p>
 * <p>
 * Resolution order: Markdown files under descs/&lt;lang&gt;/&lt;tool&gt;.md
 * take precedence over STRINGS dict entries.
 * </p>
 * 
 * @since 0.1.7
 */
public final class LocalesTranslator {
    private final String language;
    private final Map<String, String> strings;

    /**
     * Map.of.
     * 
     * @since 0.1.7
     */
    private static final Map<String, Map<String, String>> STRING_TABLES =
        Map.of("cn", ToolStringsCn.STRINGS, "en", ToolStringsEn.STRINGS);

    /**
     * LocalesTranslator.
     * 
     * @param language language
     * @param strings strings
     * @since 0.1.7
     */
    private LocalesTranslator(String language, Map<String, String> strings) {
        this.language = language;
        this.strings = strings;
    }

    /**
     * makeTranslator.
     * 
     * @param lang lang
     * @return the result
     * @since 0.1.7
     */
    public static LocalesTranslator makeTranslator(String lang) {
        String resolved = (lang != null && STRING_TABLES.containsKey(lang)) ? lang : "cn";
        Map<String, String> table = STRING_TABLES.getOrDefault(resolved, ToolStringsCn.STRINGS);
        return new LocalesTranslator(resolved, table);
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Translate a tool description key.
     * 
     * @param tool the tool name (e.g. "send_message", "create_task")
     * @param key the key suffix (e.g. "_desc", ".content", ".to")
     * @param args optional format arguments
     * @return the translated string or the key itself if not found
     * @since 0.1.7
     */
    public String t(String tool, String key, Object... args) {
        String lookupKey;
        if (key == null || key.isEmpty() || "_desc".equals(key)) {
            lookupKey = tool + "._desc";
        } else if (key.startsWith(".")) {
            lookupKey = tool + key;
        } else {
            lookupKey = tool + "." + key;
        }

        // Try Markdown description first (classpath resource)
        String desc = loadMarkdownDesc(tool);
        if (desc != null) {
            return formatMessage(desc, args);
        }

        // Fall back to STRINGS dict
        String value = strings.get(lookupKey);
        if (value != null) {
            return formatMessage(value, args);
        }

        // Last resort: return the key
        return lookupKey;
    }

    /**
     * loadMarkdownDesc.
     * 
     * @param tool tool
     * @return the result
     * @since 0.1.7
     */
    private String loadMarkdownDesc(String tool) {
        String resourcePath = "openjiuwen/agent_teams/tools/locales/descs/" + language + "/" + tool + ".md";
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Silently fall through to STRINGS dict
        }
        return null;
    }

    /**
     * formatMessage.
     * 
     * @param template template
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static String formatMessage(String template, Object... args) {
        if (args.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", args[i] != null ? args[i].toString() : "");
        }
        return result;
    }
}
